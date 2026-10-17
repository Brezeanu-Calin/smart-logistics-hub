package ro.logistics.smart_logistics.vehicle.dto;

import ro.logistics.smart_logistics.vehicle.domain.VehicleStatus;
import ro.logistics.smart_logistics.vehicle.domain.VehicleType;
import ro.logistics.smart_logistics.vehicle.domain.TransmissionType;
import ro.logistics.smart_logistics.maintenance.dto.VehicleMaintenanceComponentResponse;

import java.util.List;

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
        Integer currentMileageKm,
        TransmissionType transmissionType,
        boolean maintenanceAutoTriggered,
        List<VehicleMaintenanceComponentResponse> maintenanceComponents
) {
}
