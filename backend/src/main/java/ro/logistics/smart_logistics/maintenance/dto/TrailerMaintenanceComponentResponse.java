package ro.logistics.smart_logistics.maintenance.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record TrailerMaintenanceComponentResponse(
        String key,
        String label,
        Integer serviceIntervalMonths,
        OffsetDateTime lastPerformedAt,
        BigDecimal lastCostEur,
        Integer healthPercent,
        Integer daysRemaining
) {
}
