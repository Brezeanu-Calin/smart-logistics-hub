package ro.logistics.smart_logistics.maintenance.dto;

import java.time.OffsetDateTime;
import java.math.BigDecimal;

public record VehicleMaintenanceComponentResponse(
        String key,
        String label,
        Integer serviceIntervalKm,
        Integer serviceIntervalMonths,
        String inspectionNote,
        OffsetDateTime lastPerformedAt,
        Integer lastOdometerKm,
        BigDecimal lastCostEur,
        Integer healthPercent,
        Integer distanceRemainingKm,
        Integer daysRemaining
) {
}
