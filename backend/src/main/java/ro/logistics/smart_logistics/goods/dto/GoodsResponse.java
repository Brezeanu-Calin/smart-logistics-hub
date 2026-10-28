package ro.logistics.smart_logistics.goods.dto;

import ro.logistics.smart_logistics.goods.domain.GoodsStatus;
import ro.logistics.smart_logistics.goods.domain.GoodsType;

import java.math.BigDecimal;

public record GoodsResponse(
        Long id,
        String trackingCode,
        String clientName,
        GoodsType goodsType,
        String description,
        Double weightKg,
        BigDecimal revenueEur,
        Double volumeM3,
        String pickupCity,
        String deliveryCity,
        GoodsStatus status
) {
}
