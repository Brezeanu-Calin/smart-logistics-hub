package ro.logistics.smart_logistics.finance.application;

import ro.logistics.smart_logistics.finance.dto.FinanceResponse;
import ro.logistics.smart_logistics.goods.domain.Goods;
import ro.logistics.smart_logistics.goods.domain.GoodsStatus;
import ro.logistics.smart_logistics.maintenance.domain.VehicleMaintenanceOperation;
import ro.logistics.smart_logistics.maintenance.persistence.VehicleMaintenanceOperationRepository;
import ro.logistics.smart_logistics.route.domain.Route;
import ro.logistics.smart_logistics.route.domain.RouteStatus;
import ro.logistics.smart_logistics.route.persistence.RouteRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@Transactional(readOnly = true)
public class FinanceService {
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);

    private final RouteRepository routeRepository;
    private final VehicleMaintenanceOperationRepository maintenanceRepository;

    public FinanceService(
            RouteRepository routeRepository,
            VehicleMaintenanceOperationRepository maintenanceRepository
    ) {
        this.routeRepository = routeRepository;
        this.maintenanceRepository = maintenanceRepository;
    }

    public FinanceResponse analyze(LocalDate from, LocalDate to) {
        if (from == null || to == null || to.isBefore(from)) {
            throw new InvalidFinanceRangeException();
        }

        OffsetDateTime start = from.atStartOfDay().atOffset(ZoneOffset.UTC);
        OffsetDateTime endExclusive = to.plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC);
        List<Route> routes =
                routeRepository.findAllByDepartureTimeGreaterThanEqualAndDepartureTimeLessThanOrderByDepartureTimeAsc(
                        start,
                        endExclusive
                );
        List<VehicleMaintenanceOperation> operations =
                maintenanceRepository.findAllByPerformedAtGreaterThanEqualAndPerformedAtLessThan(start, endExclusive);

        Map<Long, DeliveredFreight> deliveredById = new LinkedHashMap<>();
        for (Route route : routes) {
            if (route.getStatus() == RouteStatus.CANCELLED) {
                continue;
            }
            for (Goods goods : route.getGoods()) {
                if (goods.getStatus() != GoodsStatus.DELIVERED && goods.getStatus() != GoodsStatus.ARCHIVED) {
                    continue;
                }
                DeliveredFreight previous = deliveredById.get(goods.getId());
                if (previous == null || route.getDepartureTime().isAfter(previous.route().getDepartureTime())) {
                    deliveredById.put(goods.getId(), new DeliveredFreight(goods, route));
                }
            }
        }
        List<DeliveredFreight> freight = List.copyOf(deliveredById.values());

        Map<Long, BigDecimal> vehicleCosts = new HashMap<>();
        Map<Long, String> vehiclePlates = new HashMap<>();
        Map<String, BigDecimal> maintenanceByCategory = new HashMap<>();
        Map<String, Long> maintenanceCategoryCounts = new HashMap<>();
        Map<String, BigDecimal> freightByCategory = new HashMap<>();
        Map<String, Long> freightCategoryCounts = new HashMap<>();
        Map<Long, BigDecimal> driverRevenue = new HashMap<>();
        Map<Long, String> driverNames = new HashMap<>();
        Map<Long, Long> driverCounts = new HashMap<>();
        Map<Long, BigDecimal> vehicleRevenue = new HashMap<>();
        Map<Long, BigDecimal> trendRevenue = new HashMap<>();
        Map<Long, BigDecimal> trendCosts = new HashMap<>();
        long unpricedFreightCount = 0;
        long uncostedMaintenanceCount = 0;
        BigDecimal totalRevenue = ZERO;
        BigDecimal totalMaintenance = ZERO;

        boolean dailyBuckets = ChronoUnit.DAYS.between(from, to) <= 31;
        for (DeliveredFreight item : freight) {
            Goods goods = item.goods();
            Route route = item.route();
            vehiclePlates.put(route.getVehicle().getId(), route.getVehicle().getLicensePlate());
            driverNames.put(route.getDriver().getId(), route.getDriver().getName());
            driverCounts.merge(route.getDriver().getId(), 1L, Long::sum);
            freightCategoryCounts.merge(goods.getGoodsType().name(), 1L, Long::sum);
            LocalDate departureDate = route.getDepartureTime().withOffsetSameInstant(ZoneOffset.UTC).toLocalDate();
            long bucket = bucketKey(departureDate, dailyBuckets);
            if (goods.getRevenueEur() == null) {
                unpricedFreightCount++;
                continue;
            }
            BigDecimal revenue = goods.getRevenueEur();
            totalRevenue = totalRevenue.add(revenue);
            trendRevenue.merge(bucket, revenue, BigDecimal::add);
            freightByCategory.merge(goods.getGoodsType().name(), revenue, BigDecimal::add);
            driverRevenue.merge(route.getDriver().getId(), revenue, BigDecimal::add);
            vehicleRevenue.merge(route.getVehicle().getId(), revenue, BigDecimal::add);
            vehiclePlates.put(route.getVehicle().getId(), route.getVehicle().getLicensePlate());
        }

        for (VehicleMaintenanceOperation operation : operations) {
            BigDecimal cost = operation.getCostEur();
            if (cost == null) {
                uncostedMaintenanceCount++;
                continue;
            }
            Long vehicleId = operation.getVehicle().getId();
            String plate = operation.getVehicle().getLicensePlate();
            String category = operation.getComponentKey();
            long bucket = bucketKey(
                    operation.getPerformedAt().withOffsetSameInstant(ZoneOffset.UTC).toLocalDate(),
                    dailyBuckets
            );
            totalMaintenance = totalMaintenance.add(cost);
            vehicleCosts.merge(vehicleId, cost, BigDecimal::add);
            vehiclePlates.put(vehicleId, plate);
            maintenanceByCategory.merge(category, cost, BigDecimal::add);
            maintenanceCategoryCounts.merge(category, 1L, Long::sum);
            trendCosts.merge(bucket, cost, BigDecimal::add);
        }

        Map<Long, Route> deliveredRoutes = new HashMap<>();
        for (DeliveredFreight item : freight) {
            deliveredRoutes.put(item.route().getId(), item.route());
        }
        Map<Long, Long> distanceByVehicle = new HashMap<>();
        for (Route route : deliveredRoutes.values()) {
            distanceByVehicle.merge(
                    route.getVehicle().getId(),
                    Math.max(0L, route.getDistanceMeters()),
                    Long::sum
            );
        }
        Map<Long, BigDecimal> allocatedRouteCosts = new HashMap<>();
        for (Route route : deliveredRoutes.values()) {
            long totalDistance = distanceByVehicle.getOrDefault(route.getVehicle().getId(), 0L);
            BigDecimal vehicleCost = vehicleCosts.getOrDefault(route.getVehicle().getId(), ZERO);
            if (totalDistance > 0) {
                BigDecimal routeShare = vehicleCost
                        .multiply(BigDecimal.valueOf(Math.max(0L, route.getDistanceMeters())))
                        .divide(BigDecimal.valueOf(totalDistance), 2, RoundingMode.HALF_UP);
                allocatedRouteCosts.merge(route.getId(), routeShare, BigDecimal::add);
            }
        }

        List<FinanceResponse.FreightMargin> margins = freight.stream()
                .map(item -> toMargin(item, allocatedRouteCosts, freight))
                .sorted(Comparator.comparing(FinanceResponse.FreightMargin::trackingCode))
                .toList();
        List<FinanceResponse.DriverContribution> drivers = driverCounts.entrySet().stream()
                .map(entry -> new FinanceResponse.DriverContribution(
                        entry.getKey(),
                        driverNames.get(entry.getKey()),
                        money(driverRevenue.getOrDefault(entry.getKey(), ZERO)),
                        entry.getValue()
                ))
                .sorted(Comparator.comparing(FinanceResponse.DriverContribution::grossContributionEur).reversed())
                .toList();

        Map<Long, String> vehicleLabels = new HashMap<>(vehiclePlates);
        List<FinanceResponse.VehicleProfitability> vehicles = vehicleLabels.keySet().stream()
                .map(id -> {
                    BigDecimal revenue = vehicleRevenue.getOrDefault(id, ZERO);
                    BigDecimal costs = vehicleCosts.getOrDefault(id, ZERO);
                    return new FinanceResponse.VehicleProfitability(
                            id,
                            vehicleLabels.get(id),
                            money(revenue),
                            money(costs),
                            money(revenue.subtract(costs))
                    );
                })
                .sorted(Comparator.comparing(FinanceResponse.VehicleProfitability::netProfitEur).reversed())
                .toList();

        List<FinanceResponse.MaintenanceCategoryCost> maintenanceCategories =
                maintenanceByCategory.entrySet().stream()
                        .map(entry -> new FinanceResponse.MaintenanceCategoryCost(
                                entry.getKey(),
                                money(entry.getValue()),
                                maintenanceCategoryCounts.getOrDefault(entry.getKey(), 0L)
                        ))
                        .sorted(Comparator.comparing(FinanceResponse.MaintenanceCategoryCost::costEur).reversed())
                        .toList();
        List<FinanceResponse.FreightCategoryRevenue> freightCategories =
                freightByCategory.entrySet().stream()
                        .map(entry -> new FinanceResponse.FreightCategoryRevenue(
                                entry.getKey(),
                                money(entry.getValue()),
                                freightCategoryCounts.getOrDefault(entry.getKey(), 0L)
                        ))
                        .sorted(Comparator.comparing(FinanceResponse.FreightCategoryRevenue::revenueEur).reversed())
                        .toList();

        return new FinanceResponse(
                from,
                to,
                money(totalRevenue),
                money(totalMaintenance),
                money(totalRevenue.subtract(totalMaintenance)),
                freight.size(),
                unpricedFreightCount,
                uncostedMaintenanceCount,
                buildTrend(from, to, dailyBuckets, trendRevenue, trendCosts),
                drivers,
                vehicles,
                maintenanceCategories,
                freightCategories,
                margins
        );
    }

    private static FinanceResponse.FreightMargin toMargin(
            DeliveredFreight item,
            Map<Long, BigDecimal> allocatedRouteCosts,
            List<DeliveredFreight> allFreight
    ) {
        Goods goods = item.goods();
        Route route = item.route();
        long routeFreightCount = allFreight.stream()
                .filter(other -> other.route().getId().equals(route.getId()))
                .count();
        BigDecimal allocatedCost = allocatedRouteCosts.getOrDefault(route.getId(), ZERO)
                .divide(BigDecimal.valueOf(Math.max(1, routeFreightCount)), 2, RoundingMode.HALF_UP);
        BigDecimal revenue = goods.getRevenueEur();
        BigDecimal margin = revenue == null ? null : revenue.subtract(allocatedCost);
        BigDecimal marginPercent = revenue == null || revenue.signum() == 0
                ? null
                : margin.multiply(BigDecimal.valueOf(100)).divide(revenue, 1, RoundingMode.HALF_UP);
        return new FinanceResponse.FreightMargin(
                goods.getId(),
                goods.getTrackingCode(),
                goods.getClientName(),
                goods.getGoodsType().name(),
                route.getDriver().getName(),
                route.getVehicle().getLicensePlate(),
                revenue == null ? null : money(revenue),
                money(allocatedCost),
                margin == null ? null : money(margin),
                marginPercent
        );
    }

    private static List<FinanceResponse.TrendPoint> buildTrend(
            LocalDate from,
            LocalDate to,
            boolean daily,
            Map<Long, BigDecimal> revenue,
            Map<Long, BigDecimal> costs
    ) {
        List<FinanceResponse.TrendPoint> points = new ArrayList<>();
        LocalDate cursor = daily ? from : from.withDayOfMonth(1);
        LocalDate last = daily ? to : to.withDayOfMonth(1);
        while (!cursor.isAfter(last)) {
            long key = bucketKey(cursor, daily);
            points.add(new FinanceResponse.TrendPoint(
                    daily ? cursor.toString() : cursor.toString().substring(0, 7),
                    money(revenue.getOrDefault(key, ZERO)),
                    money(costs.getOrDefault(key, ZERO))
            ));
            cursor = daily ? cursor.plusDays(1) : cursor.plusMonths(1);
        }
        return List.copyOf(points);
    }

    private static long bucketKey(LocalDate date, boolean daily) {
        LocalDate bucketDate = daily ? date : date.withDayOfMonth(1);
        return bucketDate.toEpochDay();
    }

    private static BigDecimal money(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP);
    }

    private record DeliveredFreight(Goods goods, Route route) {
    }
}
