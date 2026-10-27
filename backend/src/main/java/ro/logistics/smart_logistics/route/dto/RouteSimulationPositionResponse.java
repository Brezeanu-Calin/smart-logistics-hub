package ro.logistics.smart_logistics.route.dto;

import java.time.OffsetDateTime;

public record RouteSimulationPositionResponse(
        Long routeId,
        double latitude,
        double longitude,
        double headingDegrees,
        String heading,
        double progress,
        OffsetDateTime calculatedAt
) {
}
