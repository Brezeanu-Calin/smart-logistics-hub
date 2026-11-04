package ro.logistics.smart_logistics.goods.dto;

import ro.logistics.smart_logistics.goods.domain.GoodsType;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record GoodsDTO(
        @NotBlank @Size(max = 150) String clientName,
        @NotNull GoodsType goodsType,
        @Size(max = 1000) String description,
        @NotNull @DecimalMin(value = "0.0", inclusive = false) Double weightKg,
        @DecimalMin(value = "0.00") @Digits(integer = 10, fraction = 2) BigDecimal revenueEur,
        @NotNull @DecimalMin(value = "0.0", inclusive = false) Double volumeM3,
        @NotBlank @Size(max = 500) String pickupCity,
        @NotBlank @Size(max = 500) String deliveryCity
) {
}
