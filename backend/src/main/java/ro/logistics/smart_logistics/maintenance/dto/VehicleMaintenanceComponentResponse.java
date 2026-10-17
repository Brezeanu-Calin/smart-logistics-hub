package ro.logistics.smart_logistics.maintenance.dto;

import java.time.OffsetDateTime;

public record VehicleMaintenanceComponentResponse(
        String key,
        String label,
        Integer serviceIntervalKm,
        Integer serviceIntervalMonths,
        String inspectionNote,
        OffsetDateTime lastPerformedAt,
        Integer lastOdometerKm,
        Integer healthPercent,
        Integer distanceRemainingKm,
        Integer daysRemaining
) {
}
