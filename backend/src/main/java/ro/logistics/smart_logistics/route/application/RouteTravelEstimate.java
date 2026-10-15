package ro.logistics.smart_logistics.route.application;

import java.time.Duration;
import java.util.List;

import ro.logistics.smart_logistics.route.dto.RouteCoordinate;

public record RouteTravelEstimate(
        long distanceMeters,
        Duration drivingDuration,
        Duration returnDuration,
        List<RouteCoordinate> geometry,
        List<RouteCoordinate> stopCoordinates
) {
}
