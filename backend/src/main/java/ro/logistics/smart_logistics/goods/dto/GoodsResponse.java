package ro.logistics.smart_logistics.goods.dto;

import ro.logistics.smart_logistics.goods.domain.GoodsStatus;
import ro.logistics.smart_logistics.goods.domain.GoodsType;

public record GoodsResponse(
        Long id,
        String trackingCode,
        String clientName,
        GoodsType goodsType,
        String description,
        Double weightKg,
        Double volumeM3,
        String pickupAddress,
        String deliveryAddress,
        GoodsStatus status
) {
}
