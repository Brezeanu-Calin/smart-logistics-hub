package ro.logistics.smart_logistics.route.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.OffsetDateTime;
import java.util.List;

public record RouteCreateRequest(
        @NotNull @Positive Long driverId,
        @NotNull @Positive Long vehicleId,
        @NotNull OffsetDateTime departureTime,
        @NotEmpty List<@Valid RouteStopRequest> stops
) {
}
