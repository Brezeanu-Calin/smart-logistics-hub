package ro.logistics.smart_logistics.route.application;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class GoogleRoutesDirectionsService implements RouteDirectionsService {
    private static final String ROUTES_URL = "https://routes.googleapis.com/directions/v2:computeRoutes";
    private static final String FIELD_MASK =
            "routes.distanceMeters,routes.duration,routes.legs.duration,routes.legs.distanceMeters";

    private final RestClient restClient;
    private final String apiKey;
    private final String depotAddress;

    public GoogleRoutesDirectionsService(
            RestClient.Builder restClientBuilder,
            @Value("${routing.google.api-key:}") String apiKey,
            @Value("${routing.depot-address:}") String depotAddress
    ) {
        this.restClient = restClientBuilder.build();
        this.apiKey = apiKey;
        this.depotAddress = depotAddress;
    }

    @Override
    public RouteTravelEstimate estimate(List<String> orderedStops) {
        if (apiKey.isBlank() || depotAddress.isBlank()) {
            throw new RoutingUnavailableException(
                    "Google Routes API key and depot address must be configured before planning routes."
            );
        }
        if (orderedStops.isEmpty() || orderedStops.stream().anyMatch(String::isBlank)) {
            throw new InvalidRouteException("Every route stop must have an address.");
        }
        if (orderedStops.size() > 25) {
            throw new InvalidRouteException("Google Routes supports at most 25 stops in one route.");
        }

        Map<String, Object> request = new LinkedHashMap<>();
        request.put("origin", address(depotAddress));
        request.put("destination", address(depotAddress));
        request.put("intermediates", orderedStops.stream().map(GoogleRoutesDirectionsService::address).toList());
        request.put("travelMode", "DRIVE");
        request.put("routingPreference", "TRAFFIC_UNAWARE");

        GoogleRoutesResponse response;
        try {
            response = restClient.post()
                    .uri(ROUTES_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Goog-Api-Key", apiKey)
                    .header("X-Goog-FieldMask", FIELD_MASK)
                    .body(request)
                    .retrieve()
                    .body(GoogleRoutesResponse.class);
        } catch (RestClientException exception) {
            throw new RoutingUnavailableException("Google Routes could not calculate this route.", exception);
        }

        if (response == null || response.routes() == null || response.routes().isEmpty()) {
            throw new RoutingUnavailableException("Google Routes returned no route for the supplied addresses.");
        }
        GoogleRoute route = response.routes().getFirst();
        if (route.legs() == null || route.legs().isEmpty()) {
            throw new RoutingUnavailableException("Google Routes returned no route legs.");
        }
        if (route.distanceMeters() == null || route.distanceMeters() <= 0) {
            throw new RoutingUnavailableException("Google Routes returned an invalid route distance.");
        }
        Duration duration = parseDuration(route.duration());
        Duration returnDuration = parseDuration(route.legs().getLast().duration());
        if (duration.isNegative() || duration.isZero() || returnDuration.isNegative()) {
            throw new RoutingUnavailableException("Google Routes returned an invalid driving duration.");
        }
        return new RouteTravelEstimate(route.distanceMeters(), duration, returnDuration);
    }

    private static Map<String, String> address(String value) {
        return Map.of("address", value);
    }

    private static Duration parseDuration(String value) {
        if (value == null || !value.endsWith("s")) {
            throw new RoutingUnavailableException("Google Routes returned an invalid driving duration.");
        }
        try {
            return Duration.parse("PT" + value.substring(0, value.length() - 1) + "S");
        } catch (ArithmeticException | java.time.format.DateTimeParseException exception) {
            throw new RoutingUnavailableException("Google Routes returned an invalid driving duration.", exception);
        }
    }

    private record GoogleRoutesResponse(List<GoogleRoute> routes) {
    }

    private record GoogleRoute(Long distanceMeters, String duration, List<GoogleLeg> legs) {
    }

    private record GoogleLeg(Long distanceMeters, String duration) {
    }
}
