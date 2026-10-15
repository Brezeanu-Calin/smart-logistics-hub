package ro.logistics.smart_logistics.route.dto;

import ro.logistics.smart_logistics.route.domain.RouteStatus;

import java.time.OffsetDateTime;
import java.util.List;

public record RouteResponse(
        Long id,
        Long driverId,
        String driverName,
        Long vehicleId,
        String vehicleLicensePlate,
        Long trailerId,
        String trailerLicensePlate,
        OffsetDateTime departureTime,
        OffsetDateTime estimatedArrivalTime,
        OffsetDateTime estimatedReturnTime,
        OffsetDateTime returnedAt,
        Long distanceMeters,
        Long drivingDurationSeconds,
        RouteStatus status,
        List<RouteStopResponse> stops
) {
}
