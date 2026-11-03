package ro.logistics.smart_logistics.route.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;
import java.util.List;

public record RouteCreateRequest(
        @NotNull @Positive Long driverId,
        @NotNull @Positive Long vehicleId,
        @NotNull OffsetDateTime departureTime,
        @NotEmpty @Size(min = 2, max = 24) List<@Valid RouteStopRequest> stops,
        @Positive Long trailerId
) {
    public RouteCreateRequest(
            Long driverId,
            Long vehicleId,
            OffsetDateTime departureTime,
            List<RouteStopRequest> stops
    ) {
        this(driverId, vehicleId, departureTime, stops, null);
    }
}
