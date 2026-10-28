package ro.logistics.smart_logistics.trailer.dto;

import ro.logistics.smart_logistics.trailer.domain.TrailerStatus;
import ro.logistics.smart_logistics.trailer.domain.TrailerType;
import ro.logistics.smart_logistics.vehicle.domain.VehicleType;

public record TrailerResponse(
        Long id,
        String licensePlate,
        TrailerType trailerType,
        VehicleType compatibleVehicleType,
        String make,
        String model,
        Integer year,
        Double maxWeightCapacityKg,
        Double maxVolumeM3,
        TrailerStatus status
) {
}
