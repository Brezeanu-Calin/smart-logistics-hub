package ro.logistics.smart_logistics.trailer.dto;

import ro.logistics.smart_logistics.trailer.domain.TrailerStatus;
import ro.logistics.smart_logistics.trailer.domain.TrailerType;

public record TrailerResponse(
        Long id,
        String licensePlate,
        TrailerType trailerType,
        String make,
        String model,
        Integer year,
        Double maxWeightCapacityKg,
        Double maxVolumeM3,
        TrailerStatus status
) {
}
