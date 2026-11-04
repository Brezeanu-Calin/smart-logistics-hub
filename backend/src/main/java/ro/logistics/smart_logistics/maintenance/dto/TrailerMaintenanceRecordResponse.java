package ro.logistics.smart_logistics.maintenance.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record TrailerMaintenanceRecordResponse(
        String componentKey,
        String componentLabel,
        OffsetDateTime performedAt,
        BigDecimal costEur
) {
}
