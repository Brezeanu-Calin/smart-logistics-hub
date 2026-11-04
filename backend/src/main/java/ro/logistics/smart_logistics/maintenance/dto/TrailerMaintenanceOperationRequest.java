package ro.logistics.smart_logistics.maintenance.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record TrailerMaintenanceOperationRequest(
        @NotBlank @Size(max = 64) String componentKey,
        @NotNull @PastOrPresent OffsetDateTime performedAt,
        @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal costEur
) {
}
