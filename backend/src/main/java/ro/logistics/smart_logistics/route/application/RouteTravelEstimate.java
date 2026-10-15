package ro.logistics.smart_logistics.route.application;

import java.time.Duration;

public record RouteTravelEstimate(long distanceMeters, Duration drivingDuration, Duration returnDuration) {
}
