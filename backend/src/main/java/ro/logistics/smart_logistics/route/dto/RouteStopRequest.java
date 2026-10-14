package ro.logistics.smart_logistics.route.dto;

import ro.logistics.smart_logistics.route.domain.RouteStopType;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record RouteStopRequest(
        @NotNull @Positive Long goodsId,
        @NotNull RouteStopType type
) {
}
