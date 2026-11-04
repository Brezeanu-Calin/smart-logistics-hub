package ro.logistics.smart_logistics.trailer.dto;

import ro.logistics.smart_logistics.trailer.domain.TrailerStatus;
import ro.logistics.smart_logistics.trailer.domain.TrailerType;
import ro.logistics.smart_logistics.vehicle.domain.VehicleType;
import ro.logistics.smart_logistics.maintenance.dto.TrailerMaintenanceComponentResponse;
import ro.logistics.smart_logistics.maintenance.dto.TrailerMaintenanceRecordResponse;

import java.util.List;

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
        TrailerStatus status,
        boolean maintenanceAutoTriggered,
        List<TrailerMaintenanceComponentResponse> maintenanceComponents,
        List<TrailerMaintenanceRecordResponse> maintenanceHistory
) {
}
