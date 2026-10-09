package ro.logistics.smart_logistics.goods.dto;

import ro.logistics.smart_logistics.goods.domain.GoodsStatus;

import jakarta.validation.constraints.NotNull;

public record GoodsStatusRequest(@NotNull GoodsStatus status) {
}
