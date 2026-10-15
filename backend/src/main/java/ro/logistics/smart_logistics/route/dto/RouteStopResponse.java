package ro.logistics.smart_logistics.route.dto;

import ro.logistics.smart_logistics.route.domain.RouteStopType;

public record RouteStopResponse(
        Long goodsId,
        String trackingCode,
        RouteStopType type,
        Integer order,
        String address,
        Double latitude,
        Double longitude
) {
}
