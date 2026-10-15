package ro.logistics.smart_logistics.route.application;

import ro.logistics.smart_logistics.route.domain.GeocodedAddress;
import ro.logistics.smart_logistics.route.dto.RouteCoordinate;
import ro.logistics.smart_logistics.route.persistence.GeocodedAddressRepository;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Service
public class OpenStreetMapDirectionsService implements RouteDirectionsService {
    private static final long MIN_NOMINATIM_INTERVAL_MILLIS = 1_100;

    private final RestClient geocodingClient;
    private final RestClient routingClient;
    private final GeocodedAddressRepository geocodedAddressRepository;
    private final String depotAddress;
    private final String userAgent;
    private long lastGeocodingRequestAt;

    public OpenStreetMapDirectionsService(
            RestClient.Builder restClientBuilder,
            GeocodedAddressRepository geocodedAddressRepository,
            @Value("${routing.depot-address:}") String depotAddress,
            @Value("${routing.nominatim.base-url:https://nominatim.openstreetmap.org}") String nominatimBaseUrl,
            @Value("${routing.osrm.base-url:https://router.project-osrm.org}") String osrmBaseUrl,
            @Value("${routing.nominatim.user-agent:SmartLogisticsHub/1.0 (personal project)}") String userAgent
    ) {
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(15));
        RestClient.Builder configuredBuilder = restClientBuilder.clone().requestFactory(requestFactory);
        this.geocodingClient = configuredBuilder.clone().baseUrl(nominatimBaseUrl).build();
        this.routingClient = configuredBuilder.clone().baseUrl(osrmBaseUrl).build();
        this.geocodedAddressRepository = geocodedAddressRepository;
        this.depotAddress = depotAddress;
        this.userAgent = userAgent;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public RouteTravelEstimate estimate(List<String> orderedStops) {
        if (depotAddress.isBlank()) {
            throw new RoutingUnavailableException("Configure DEPOT_ADDRESS before planning routes.");
        }
        if (userAgent.isBlank()) {
            throw new RoutingUnavailableException("Configure a descriptive NOMINATIM_USER_AGENT before planning routes.");
        }
        if (orderedStops.isEmpty() || orderedStops.stream().anyMatch(String::isBlank)) {
            throw new InvalidRouteException("Every route stop must have an address.");
        }
        if (orderedStops.size() > 25) {
            throw new InvalidRouteException("OSRM supports at most 25 stops in one route.");
        }

        List<String> addresses = new ArrayList<>(orderedStops.size() + 2);
        addresses.add(depotAddress);
        addresses.addAll(orderedStops);
        addresses.add(depotAddress);
        List<GeoPoint> waypoints = addresses.stream().map(this::geocode).toList();
        String coordinatePath = waypoints.stream()
                .map(point -> point.longitude() + "," + point.latitude())
                .collect(Collectors.joining(";"));

        OsrmResponse response;
        try {
            response = routingClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/route/v1/driving/" + coordinatePath)
                            .queryParam("overview", "full")
                            .queryParam("geometries", "geojson")
                            .queryParam("steps", "false")
                            .build())
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(OsrmResponse.class);
        } catch (RestClientException exception) {
            throw new RoutingUnavailableException("OSRM could not calculate a route for the supplied addresses.", exception);
        }

        if (response == null || !"Ok".equals(response.code()) || response.routes() == null
                || response.routes().isEmpty()) {
            throw new RoutingUnavailableException("OSRM returned no route for the supplied addresses.");
        }
        OsrmRoute route = response.routes().getFirst();
        if (route.legs() == null || route.legs().size() != orderedStops.size() + 1
                || route.geometry() == null || route.geometry().coordinates() == null
                || !Double.isFinite(route.distance()) || route.distance() <= 0
                || !Double.isFinite(route.duration()) || route.duration() <= 0) {
            throw new RoutingUnavailableException("OSRM returned incomplete route data.");
        }

        List<RouteCoordinate> geometry = route.geometry().coordinates().stream()
                .map(OpenStreetMapDirectionsService::toRouteCoordinate)
                .toList();
        List<RouteCoordinate> stopCoordinates = waypoints.subList(1, waypoints.size() - 1).stream()
                .map(point -> new RouteCoordinate(point.latitude(), point.longitude()))
                .toList();
        if (geometry.size() < 2 || stopCoordinates.size() != orderedStops.size()) {
            throw new RoutingUnavailableException("OSRM returned invalid route geometry.");
        }

        List<OsrmLeg> legs = route.legs();
        double outboundSeconds = legs.subList(0, legs.size() - 1).stream()
                .mapToDouble(OsrmLeg::duration)
                .sum();
        double returnSeconds = legs.getLast().duration();
        if (!Double.isFinite(outboundSeconds) || !Double.isFinite(returnSeconds)
                || outboundSeconds <= 0 || returnSeconds < 0) {
            throw new RoutingUnavailableException("OSRM returned invalid driving durations.");
        }
        return new RouteTravelEstimate(
                Math.round(route.distance()),
                Duration.ofSeconds(Math.round(outboundSeconds)),
                Duration.ofSeconds(Math.round(returnSeconds)),
                geometry,
                stopCoordinates
        );
    }

    private synchronized GeoPoint geocode(String address) {
        String normalized = normalizeAddress(address);
        String hash = sha256(normalized);
        GeocodedAddress cached = geocodedAddressRepository.findById(hash).orElse(null);
        if (cached != null) {
            return new GeoPoint(cached.getLatitude(), cached.getLongitude());
        }

        waitForNominatimLimit();
        NominatimResult[] results;
        try {
            results = geocodingClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/search")
                            .queryParam("q", address)
                            .queryParam("format", "jsonv2")
                            .queryParam("limit", 1)
                            .build())
                    .header("User-Agent", userAgent)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(NominatimResult[].class);
        } catch (RestClientException exception) {
            throw new RoutingUnavailableException("Nominatim could not locate address: " + address, exception);
        }
        if (results == null || results.length == 0) {
            throw new InvalidRouteException("Nominatim could not find a location for address: " + address);
        }

        double latitude;
        double longitude;
        try {
            latitude = Double.parseDouble(results[0].lat());
            longitude = Double.parseDouble(results[0].lon());
        } catch (NumberFormatException exception) {
            throw new RoutingUnavailableException("Nominatim returned invalid coordinates for: " + address, exception);
        }
        if (!Double.isFinite(latitude) || latitude < -90 || latitude > 90
                || !Double.isFinite(longitude) || longitude < -180 || longitude > 180) {
            throw new RoutingUnavailableException("Nominatim returned invalid coordinates for: " + address);
        }

        geocodedAddressRepository.saveAndFlush(
                new GeocodedAddress(hash, normalized, latitude, longitude)
        );
        return new GeoPoint(latitude, longitude);
    }

    private synchronized void waitForNominatimLimit() {
        long waitMillis = lastGeocodingRequestAt + MIN_NOMINATIM_INTERVAL_MILLIS - System.currentTimeMillis();
        if (waitMillis > 0) {
            try {
                Thread.sleep(waitMillis);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new RoutingUnavailableException("Address lookup was interrupted.", exception);
            }
        }
        lastGeocodingRequestAt = System.currentTimeMillis();
    }

    private static String normalizeAddress(String address) {
        return Normalizer.normalize(address, Normalizer.Form.NFKC)
                .trim()
                .replaceAll("\\s+", " ")
                .toLowerCase(Locale.ROOT);
    }

    private static String sha256(String value) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available.", exception);
        }
    }

    private static RouteCoordinate toRouteCoordinate(List<Double> coordinate) {
        if (coordinate == null || coordinate.size() != 2
                || coordinate.get(0) == null || coordinate.get(1) == null
                || !Double.isFinite(coordinate.get(0)) || coordinate.get(0) < -180
                || coordinate.get(0) > 180 || !Double.isFinite(coordinate.get(1))
                || coordinate.get(1) < -90 || coordinate.get(1) > 90) {
            throw new RoutingUnavailableException("OSRM returned invalid route geometry.");
        }
        return new RouteCoordinate(coordinate.get(1), coordinate.get(0));
    }

    private record GeoPoint(double latitude, double longitude) {
    }

    private record NominatimResult(String lat, String lon) {
    }

    private record OsrmResponse(String code, List<OsrmRoute> routes) {
    }

    private record OsrmRoute(double distance, double duration, List<OsrmLeg> legs, GeoJsonLineString geometry) {
    }

    private record OsrmLeg(double distance, double duration) {
    }

    private record GeoJsonLineString(List<List<Double>> coordinates) {
    }
}
