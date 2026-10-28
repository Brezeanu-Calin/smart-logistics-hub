package ro.logistics.smart_logistics.finance.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record FinanceResponse(
        LocalDate from,
        LocalDate to,
        BigDecimal freightRevenueEur,
        BigDecimal maintenanceCostsEur,
        BigDecimal netProfitEur,
        long deliveredFreightCount,
        long unpricedFreightCount,
        long uncostedMaintenanceCount,
        List<TrendPoint> trend,
        List<DriverContribution> drivers,
        List<VehicleProfitability> vehicles,
        List<MaintenanceCategoryCost> maintenanceByCategory,
        List<FreightCategoryRevenue> freightByCategory,
        List<FreightMargin> freightMargins
) {
    public record TrendPoint(String period, BigDecimal revenueEur, BigDecimal maintenanceCostsEur) {
    }

    public record DriverContribution(
            Long driverId,
            String driverName,
            BigDecimal grossContributionEur,
            long deliveredFreightCount
    ) {
    }

    public record VehicleProfitability(
            Long vehicleId,
            String licensePlate,
            BigDecimal freightRevenueEur,
            BigDecimal maintenanceCostsEur,
            BigDecimal netProfitEur
    ) {
    }

    public record MaintenanceCategoryCost(String category, BigDecimal costEur, long operationCount) {
    }

    public record FreightCategoryRevenue(String category, BigDecimal revenueEur, long shipmentCount) {
    }

    public record FreightMargin(
            Long freightId,
            String trackingCode,
            String clientName,
            String category,
            String driverName,
            String vehicleLicensePlate,
            BigDecimal revenueEur,
            BigDecimal estimatedMaintenanceEur,
            BigDecimal estimatedMarginEur,
            BigDecimal estimatedMarginPercent
    ) {
    }
}
