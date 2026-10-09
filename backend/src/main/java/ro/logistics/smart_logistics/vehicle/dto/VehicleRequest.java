package ro.logistics.smart_logistics.vehicle.dto;

import ro.logistics.smart_logistics.vehicle.domain.VehicleType;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record VehicleRequest(
        @NotBlank @Size(max = 20) @Pattern(regexp = ".*\\S.*") String licensePlate,
        @NotNull VehicleType vehicleType,
        @NotBlank @Size(max = 100) String make,
        @NotBlank @Size(max = 100) String model,
        @NotNull @Min(1900) @Max(2100) Integer year,
        @NotNull @DecimalMin(value = "0.0", inclusive = false) Double maxWeightCapacityKg,
        @NotNull @DecimalMin(value = "0.0", inclusive = false) Double maxVolumeM3,
        @NotNull @Min(0) Integer currentMileageKm
) {
}
