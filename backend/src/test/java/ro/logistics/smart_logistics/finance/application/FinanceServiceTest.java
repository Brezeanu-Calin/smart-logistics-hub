package ro.logistics.smart_logistics.finance.application;

import ro.logistics.smart_logistics.driver.domain.Driver;
import ro.logistics.smart_logistics.driver.domain.LicenseCategory;
import ro.logistics.smart_logistics.finance.dto.FinanceResponse;
import ro.logistics.smart_logistics.goods.domain.Goods;
import ro.logistics.smart_logistics.goods.domain.GoodsStatus;
import ro.logistics.smart_logistics.goods.domain.GoodsType;
import ro.logistics.smart_logistics.maintenance.domain.VehicleMaintenanceOperation;
import ro.logistics.smart_logistics.maintenance.persistence.VehicleMaintenanceOperationRepository;
import ro.logistics.smart_logistics.route.domain.Route;
import ro.logistics.smart_logistics.route.domain.RouteStop;
import ro.logistics.smart_logistics.route.domain.RouteStatus;
import ro.logistics.smart_logistics.route.domain.RouteStopType;
import ro.logistics.smart_logistics.route.persistence.RouteRepository;
import ro.logistics.smart_logistics.vehicle.domain.Vehicle;
import ro.logistics.smart_logistics.vehicle.domain.VehicleType;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FinanceServiceTest {
    @Mock
    private RouteRepository routeRepository;

    @Mock
    private VehicleMaintenanceOperationRepository maintenanceRepository;

    @InjectMocks
    private FinanceService financeService;

    @Test
    void derivesRevenueMaintenanceAndDistanceAllocatedFreightMargins() throws Exception {
        Driver driver = new Driver("Alex Driver", "alex@example.com", Set.of(LicenseCategory.CE));
        setId(driver, 10L);
        Vehicle vehicle = new Vehicle(
                "CJ 01 SLH", VehicleType.ARTICULATED_TRUCK, "Make", "Model",
                2022, 20_000.0, 80.0, 100_000
        );
        setId(vehicle, 20L);
        Goods firstGoods = deliveredGoods(31L, "TRK-1", "600.00");
        Goods secondGoods = deliveredGoods(32L, "TRK-2", "400.00");
        OffsetDateTime departure = OffsetDateTime.parse("2026-03-05T08:00:00Z");
        Route route = new Route(
                driver, vehicle, null, departure, departure.plusHours(3),
                100_000L, Duration.ofHours(3).toSeconds(), Duration.ofHours(2).toSeconds()
        );
        setId(route, 40L);
        route.addStop(new RouteStop(firstGoods, RouteStopType.PICKUP, 1));
        route.addStop(new RouteStop(firstGoods, RouteStopType.DELIVERY, 2));
        route.addStop(new RouteStop(secondGoods, RouteStopType.PICKUP, 3));
        route.addStop(new RouteStop(secondGoods, RouteStopType.DELIVERY, 4));
        route.activate(departure);
        route.complete();
        VehicleMaintenanceOperation operation = new VehicleMaintenanceOperation(
                vehicle,
                "engine-oil-filter",
                departure.plusDays(2),
                99_000,
                new BigDecimal("100.00")
        );

        when(routeRepository.findAllByDepartureTimeGreaterThanEqualAndDepartureTimeLessThanOrderByDepartureTimeAsc(
                OffsetDateTime.parse("2026-03-01T00:00:00Z"),
                OffsetDateTime.parse("2026-04-01T00:00:00Z")
        )).thenReturn(List.of(route));
        when(maintenanceRepository.findAllByPerformedAtGreaterThanEqualAndPerformedAtLessThan(
                OffsetDateTime.parse("2026-03-01T00:00:00Z"),
                OffsetDateTime.parse("2026-04-01T00:00:00Z")
        )).thenReturn(List.of(operation));

        FinanceResponse response = financeService.analyze(
                LocalDate.parse("2026-03-01"),
                LocalDate.parse("2026-03-31")
        );

        assertEquals(new BigDecimal("1000.00"), response.freightRevenueEur());
        assertEquals(new BigDecimal("100.00"), response.maintenanceCostsEur());
        assertEquals(new BigDecimal("900.00"), response.netProfitEur());
        assertEquals(2, response.deliveredFreightCount());
        assertEquals(1, response.drivers().size());
        assertEquals(new BigDecimal("1000.00"), response.drivers().getFirst().grossContributionEur());
        assertEquals(new BigDecimal("900.00"), response.vehicles().getFirst().netProfitEur());
        assertEquals(new BigDecimal("550.00"), response.freightMargins().get(0).estimatedMarginEur());
        assertEquals(new BigDecimal("350.00"), response.freightMargins().get(1).estimatedMarginEur());
    }

    @Test
    void rejectsEndDatesBeforeStartDates() {
        assertThrows(
                InvalidFinanceRangeException.class,
                () -> financeService.analyze(LocalDate.parse("2026-04-01"), LocalDate.parse("2026-03-31"))
        );
    }

    private static Goods deliveredGoods(Long id, String trackingCode, String revenue) throws Exception {
        Goods goods = new Goods(
                trackingCode,
                "Client",
                GoodsType.GENERAL,
                null,
                100.0,
                new BigDecimal(revenue),
                2.0,
                "Cluj",
                "Brasov"
        );
        setId(goods, id);
        goods.assignToRoute();
        goods.startTransit();
        goods.markDelivered();
        return goods;
    }

    private static void setId(Object entity, Long id) throws Exception {
        Field field = entity.getClass().getDeclaredField("id");
        field.setAccessible(true);
        field.set(entity, id);
    }
}
