package ro.logistics.smart_logistics.route.application;

import ro.logistics.smart_logistics.route.domain.GeocodedCity;
import ro.logistics.smart_logistics.route.persistence.GeocodedCityRepository;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedQueue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OpenStreetMapDirectionsServiceTest {
    @Test
    void geocodesStopsAndUsesOsrmLegsToSeparateOutboundAndReturnEstimates() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        ConcurrentLinkedQueue<String> geocodingQueries = new ConcurrentLinkedQueue<>();
        ConcurrentLinkedQueue<String> userAgents = new ConcurrentLinkedQueue<>();
        server.createContext("/search", exchange -> {
            String query = exchange.getRequestURI().getRawQuery();
            geocodingQueries.add(URLDecoder.decode(query, StandardCharsets.UTF_8));
            userAgents.add(exchange.getRequestHeaders().getFirst("User-Agent"));
            respond(exchange, 200, "[{\"lat\":\"46.77\",\"lon\":\"23.59\"}]");
        });
        server.createContext("/route/v1/driving/", exchange -> respond(exchange, 200, """
                {
                  "code": "Ok",
                  "routes": [{
                    "distance": 15000,
                    "duration": 1800,
                    "legs": [
                      {"distance": 8000, "duration": 1000},
                      {"distance": 7000, "duration": 800}
                    ],
                    "geometry": {
                      "type": "LineString",
                      "coordinates": [[23.59, 46.77], [24.0, 46.5], [23.59, 46.77]]
                    }
                  }]
                }
                """));
        server.start();

        try {
            GeocodedCityRepository repository = mock(GeocodedCityRepository.class);
            Map<String, GeocodedCity> cache = new HashMap<>();
            when(repository.findById(anyString()))
                    .thenAnswer(invocation -> Optional.ofNullable(cache.get(invocation.getArgument(0))));
            when(repository.saveAndFlush(any(GeocodedCity.class)))
                    .thenAnswer(invocation -> {
                        GeocodedCity city = invocation.getArgument(0);
                        cache.put(sha256(city.getNormalizedCity()), city);
                        return city;
                    });

            String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
            OpenStreetMapDirectionsService service = new OpenStreetMapDirectionsService(
                    RestClient.builder(),
                    repository,
                    "Depot",
                    baseUrl,
                    baseUrl,
                    "SmartLogisticsHubTest/1.0"
            );

            RouteTravelEstimate estimate = service.estimate(List.of("Bucharest, Romania"));

            assertEquals(15_000, estimate.distanceMeters());
            assertEquals(Duration.ofSeconds(1_000), estimate.drivingDuration());
            assertEquals(Duration.ofSeconds(800), estimate.returnDuration());
            assertEquals(3, estimate.geometry().size());
            assertEquals(1, estimate.stopCoordinates().size());
            assertEquals(46.77, estimate.geometry().getFirst().latitude());
            assertEquals(46.77, estimate.stopCoordinates().getFirst().latitude());
            assertEquals(2, geocodingQueries.size());
            assertEquals(2, userAgents.size());
            assertEquals(
                    List.of("Depot", "Bucharest, Romania"),
                    geocodingQueries.stream().map(query -> query.substring(2, query.indexOf("&format="))).toList()
            );
            assertEquals(
                    List.of("SmartLogisticsHubTest/1.0", "SmartLogisticsHubTest/1.0"),
                    List.copyOf(userAgents)
            );
        } finally {
            server.stop(0);
        }
    }

    private static void respond(com.sun.net.httpserver.HttpExchange exchange, int status, String body)
            throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (var output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }

    private static String sha256(String value) throws java.security.NoSuchAlgorithmException {
        byte[] hash = java.security.MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8));
        return java.util.HexFormat.of().formatHex(hash);
    }
}
