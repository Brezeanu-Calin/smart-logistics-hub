package ro.logistics.smart_logistics.maintenance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.PastOrPresent;

import java.time.OffsetDateTime;

public record VehicleMaintenanceOperationRequest(
        @NotBlank @Size(max = 64) String componentKey,
        @NotNull @PastOrPresent OffsetDateTime performedAt,
        @NotNull @Min(0) Integer odometerKm
) {
}
