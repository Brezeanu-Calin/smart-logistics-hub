package ro.logistics.smart_logistics.vehicle.dto;

import ro.logistics.smart_logistics.vehicle.domain.VehicleStatus;
import ro.logistics.smart_logistics.vehicle.domain.VehicleType;

public record VehicleResponse(
        Long id,
        String licensePlate,
        VehicleType vehicleType,
        String make,
        String model,
        Integer year,
        Double maxWeightCapacityKg,
        Double maxVolumeM3,
        VehicleStatus status,
        Integer currentMileageKm
) {
}
