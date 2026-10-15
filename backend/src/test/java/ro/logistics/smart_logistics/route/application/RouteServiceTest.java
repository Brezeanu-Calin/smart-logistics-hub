package ro.logistics.smart_logistics.route.application;

import ro.logistics.smart_logistics.driver.domain.Driver;
import ro.logistics.smart_logistics.driver.domain.DriverStatus;
import ro.logistics.smart_logistics.driver.domain.LicenseCategory;
import ro.logistics.smart_logistics.driver.persistence.DriverRepository;
import ro.logistics.smart_logistics.goods.domain.Goods;
import ro.logistics.smart_logistics.goods.domain.GoodsStatus;
import ro.logistics.smart_logistics.goods.domain.GoodsType;
import ro.logistics.smart_logistics.goods.persistence.GoodsRepository;
import ro.logistics.smart_logistics.route.domain.Route;
import ro.logistics.smart_logistics.route.domain.RouteStatus;
import ro.logistics.smart_logistics.route.domain.RouteStop;
import ro.logistics.smart_logistics.route.domain.RouteStopType;
import ro.logistics.smart_logistics.route.dto.RouteCreateRequest;
import ro.logistics.smart_logistics.route.dto.RouteStatusRequest;
import ro.logistics.smart_logistics.route.dto.RouteStopRequest;
import ro.logistics.smart_logistics.route.persistence.RouteRepository;
import ro.logistics.smart_logistics.trailer.domain.Trailer;
import ro.logistics.smart_logistics.trailer.domain.TrailerStatus;
import ro.logistics.smart_logistics.trailer.domain.TrailerType;
import ro.logistics.smart_logistics.trailer.persistence.TrailerRepository;
import ro.logistics.smart_logistics.vehicle.domain.Vehicle;
import ro.logistics.smart_logistics.vehicle.domain.VehicleStatus;
import ro.logistics.smart_logistics.vehicle.domain.VehicleType;
import ro.logistics.smart_logistics.vehicle.persistence.VehicleRepository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import jakarta.persistence.EntityManager;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RouteServiceTest {
    @Mock
    private RouteRepository routeRepository;
    @Mock
    private DriverRepository driverRepository;
    @Mock
    private VehicleRepository vehicleRepository;
    @Mock
    private TrailerRepository trailerRepository;
    @Mock
    private GoodsRepository goodsRepository;
    @Mock
    private EntityManager entityManager;
    @Mock
    private RouteDirectionsService directionsService;

    @InjectMocks
    private RouteService routeService;

    @Test
    void createAssignsGoodsButLeavesResourcesAvailableUntilActivation() throws Exception {
        Driver driver = new Driver("Alex", "alex@example.com", Set.of(LicenseCategory.B));
        setId(driver, 1L);
        Vehicle vehicle = new Vehicle("VAN-1", VehicleType.VAN, "Make", "Model", 2022, 5000.0, 20.0, 1000);
        setId(vehicle, 2L);
        Goods goods = goods(3L, 4000.0, 10.0);
        prepareBasicCreate(driver, vehicle, goods);

        var response = routeService.create(request(1L, 2L, 3L));

        assertEquals(RouteStatus.PLANNED, response.status());
        assertEquals(2, response.stops().size());
        assertEquals(GoodsStatus.ASSIGNED, goods.getStatus());
        assertEquals(DriverStatus.AVAILABLE, driver.getStatus());
        assertEquals(VehicleStatus.AVAILABLE, vehicle.getStatus());
        assertNull(response.trailerId());
    }

    @Test
    void selectsCompatibleTrailerWhenVehicleCapacityIsInsufficient() throws Exception {
        Driver driver = new Driver("Alex", "alex@example.com", Set.of(LicenseCategory.BE));
        setId(driver, 1L);
        Vehicle vehicle = new Vehicle("VAN-1", VehicleType.VAN, "Make", "Model", 2022, 5000.0, 10.0, 1000);
        setId(vehicle, 2L);
        Goods goods = goods(3L, 9000.0, 15.0);
        Trailer trailer = new Trailer(
                "TR-1", TrailerType.VAN_TRAILER, "Make", "Model", 2021, 5000.0, 10.0
        );
        setId(trailer, 4L);
        prepareBasicCreate(driver, vehicle, goods);
        when(trailerRepository.findAllForPlanning(
                TrailerType.VAN_TRAILER,
                List.of(TrailerStatus.AVAILABLE, TrailerStatus.IN_TRANSIT)
        ))
                .thenReturn(List.of(trailer));
        when(routeRepository.existsTrailerScheduleOverlap(
                eq(4L), anyList(), any(OffsetDateTime.class), any(OffsetDateTime.class)
        )).thenReturn(false);

        var response = routeService.create(request(1L, 2L, 3L));

        assertEquals(4L, response.trailerId());
        assertEquals("TR-1", response.trailerLicensePlate());
    }

    @Test
    void articulatedTruckAlwaysRequiresMatchingTrailer() throws Exception {
        Driver driver = new Driver("Alex", "alex@example.com", Set.of(LicenseCategory.CE));
        setId(driver, 1L);
        Vehicle vehicle = new Vehicle(
                "ART-1", VehicleType.ARTICULATED_TRUCK, "Make", "Tractor", 2022, 20000.0, 80.0, 1000
        );
        setId(vehicle, 2L);
        Goods goods = goods(3L, 1000.0, 5.0);
        Trailer trailer = new Trailer(
                "SEMI-1", TrailerType.ARTICULATED_TRUCK_TRAILER, "Make", "Trailer", 2021, 1000.0, 5.0
        );
        setId(trailer, 4L);
        prepareBasicCreate(driver, vehicle, goods);
        when(trailerRepository.findAllForPlanning(
                TrailerType.ARTICULATED_TRUCK_TRAILER,
                List.of(TrailerStatus.AVAILABLE, TrailerStatus.IN_TRANSIT)
        )).thenReturn(List.of(trailer));
        when(routeRepository.existsTrailerScheduleOverlap(
                eq(4L), anyList(), any(OffsetDateTime.class), any(OffsetDateTime.class)
        )).thenReturn(false);

        var response = routeService.create(request(1L, 2L, 3L));

        assertEquals(4L, response.trailerId());
    }

    @Test
    void rejectsPickupScheduledAfterDeliveryBeforeAccessingRepositories() {
        RouteCreateRequest request = new RouteCreateRequest(
                1L,
                2L,
                OffsetDateTime.parse("2026-10-15T08:00:00Z"),
                List.of(
                        new RouteStopRequest(3L, RouteStopType.DELIVERY),
                        new RouteStopRequest(3L, RouteStopType.PICKUP)
                )
        );

        assertThrows(InvalidRouteException.class, () -> routeService.create(request));

        verify(driverRepository, never()).findByIdForUpdate(any());
    }

    @Test
    void rejectsConflictingDriverSchedule() throws Exception {
        Driver driver = new Driver("Alex", "alex@example.com", Set.of(LicenseCategory.B));
        setId(driver, 1L);
        Vehicle vehicle = new Vehicle("VAN-1", VehicleType.VAN, "Make", "Model", 2022, 5000.0, 20.0, 1000);
        setId(vehicle, 2L);
        Goods goods = goods(3L, 4000.0, 10.0);
        when(driverRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(driver));
        when(vehicleRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(vehicle));
        when(goodsRepository.findAllByIdForUpdate(List.of(3L))).thenReturn(List.of(goods));
        when(directionsService.estimate(anyList()))
                .thenReturn(new RouteTravelEstimate(120_000, Duration.ofHours(6), Duration.ofHours(1)));
        when(routeRepository.existsDriverScheduleOverlap(
                eq(1L), anyList(), any(OffsetDateTime.class), any(OffsetDateTime.class)
        )).thenReturn(true);

        assertThrows(RouteConflictException.class, () -> routeService.create(request(1L, 2L, 3L)));

        verify(routeRepository, never()).saveAndFlush(any(Route.class));
    }

    @Test
    void activatingRouteMovesDriverVehicleAndGoodsIntoTransit() throws Exception {
        Driver driver = new Driver("Alex", "alex@example.com", Set.of(LicenseCategory.B));
        setId(driver, 1L);
        Vehicle vehicle = new Vehicle("VAN-1", VehicleType.VAN, "Make", "Model", 2022, 5000.0, 20.0, 1000);
        setId(vehicle, 2L);
        Goods goods = goods(3L, 4000.0, 10.0);
        goods.assignToRoute();
        Route route = route(driver, vehicle, goods, RouteStatus.PLANNED);
        setId(route, 8L);
        when(routeRepository.findById(8L)).thenReturn(Optional.of(route));
        when(routeRepository.findByIdForUpdate(8L)).thenReturn(Optional.of(route));
        when(driverRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(driver));
        when(vehicleRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(vehicle));
        when(goodsRepository.findAllByIdForUpdate(List.of(3L))).thenReturn(List.of(goods));
        when(routeRepository.saveAndFlush(any(Route.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        routeService.updateStatus(8L, new RouteStatusRequest(RouteStatus.ACTIVE));

        assertEquals(RouteStatus.ACTIVE, route.getStatus());
        assertEquals(DriverStatus.IN_TRANSIT, driver.getStatus());
        assertEquals(VehicleStatus.IN_TRANSIT, vehicle.getStatus());
        assertEquals(GoodsStatus.IN_TRANSIT, goods.getStatus());
    }

    @Test
    void cancellingActiveRouteReleasesUndeliveredGoodsAndKeepsResourcesReturning() throws Exception {
        Driver driver = new Driver("Alex", "alex@example.com", Set.of(LicenseCategory.B));
        setId(driver, 1L);
        driver.markInTransit();
        Vehicle vehicle = new Vehicle("VAN-1", VehicleType.VAN, "Make", "Model", 2022, 5000.0, 20.0, 1000);
        setId(vehicle, 2L);
        vehicle.markInTransit();
        Goods delivered = goods(3L, 1000.0, 2.0);
        delivered.assignToRoute();
        delivered.startTransit();
        delivered.markDelivered();
        Goods undelivered = goods(4L, 1000.0, 2.0);
        undelivered.assignToRoute();
        undelivered.startTransit();
        Route route = route(driver, vehicle, delivered, RouteStatus.ACTIVE);
        route.addStop(new RouteStop(undelivered, RouteStopType.PICKUP, 3));
        route.addStop(new RouteStop(undelivered, RouteStopType.DELIVERY, 4));
        setId(route, 8L);
        when(routeRepository.findById(8L)).thenReturn(Optional.of(route));
        when(routeRepository.findByIdForUpdate(8L)).thenReturn(Optional.of(route));
        when(goodsRepository.findAllByIdForUpdate(List.of(3L, 4L))).thenReturn(List.of(delivered, undelivered));
        when(driverRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(driver));
        when(vehicleRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(vehicle));
        when(routeRepository.saveAndFlush(any(Route.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        routeService.updateStatus(8L, new RouteStatusRequest(RouteStatus.CANCELLED));

        assertEquals(RouteStatus.CANCELLED, route.getStatus());
        assertEquals(GoodsStatus.DELIVERED, delivered.getStatus());
        assertEquals(GoodsStatus.UNASSIGNED, undelivered.getStatus());
        assertEquals(DriverStatus.RETURNING, driver.getStatus());
        assertEquals(VehicleStatus.IN_TRANSIT, vehicle.getStatus());
        org.junit.jupiter.api.Assertions.assertNotNull(route.getEstimatedReturnTime());
    }

    @Test
    void routeCannotCompleteUntilEveryGoodIsDelivered() throws Exception {
        Driver driver = new Driver("Alex", "alex@example.com", Set.of(LicenseCategory.B));
        setId(driver, 1L);
        Vehicle vehicle = new Vehicle("VAN-1", VehicleType.VAN, "Make", "Model", 2022, 5000.0, 20.0, 1000);
        setId(vehicle, 2L);
        Goods goods = goods(3L, 4000.0, 10.0);
        goods.assignToRoute();
        goods.startTransit();
        Route route = route(driver, vehicle, goods, RouteStatus.ACTIVE);
        setId(route, 8L);
        when(routeRepository.findById(8L)).thenReturn(Optional.of(route));
        when(routeRepository.findByIdForUpdate(8L)).thenReturn(Optional.of(route));
        when(driverRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(driver));
        when(vehicleRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(vehicle));
        when(goodsRepository.findAllByIdForUpdate(List.of(3L))).thenReturn(List.of(goods));

        assertThrows(
                RouteConflictException.class,
                () -> routeService.updateStatus(8L, new RouteStatusRequest(RouteStatus.COMPLETED))
        );

        assertEquals(RouteStatus.ACTIVE, route.getStatus());
        verify(routeRepository, never()).saveAndFlush(any(Route.class));
    }

    @Test
    void completingDeliveredRouteArchivesGoodsAndKeepsResourcesReturning() throws Exception {
        Driver driver = new Driver("Alex", "alex@example.com", Set.of(LicenseCategory.B));
        setId(driver, 1L);
        driver.markInTransit();
        Vehicle vehicle = new Vehicle("VAN-1", VehicleType.VAN, "Make", "Model", 2022, 5000.0, 20.0, 1000);
        setId(vehicle, 2L);
        vehicle.markInTransit();
        Goods goods = goods(3L, 4000.0, 10.0);
        goods.assignToRoute();
        goods.startTransit();
        goods.markDelivered();
        Route route = route(driver, vehicle, goods, RouteStatus.ACTIVE);
        setId(route, 8L);
        when(routeRepository.findById(8L)).thenReturn(Optional.of(route));
        when(routeRepository.findByIdForUpdate(8L)).thenReturn(Optional.of(route));
        when(goodsRepository.findAllByIdForUpdate(List.of(3L))).thenReturn(List.of(goods));
        when(driverRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(driver));
        when(vehicleRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(vehicle));
        when(routeRepository.saveAndFlush(any(Route.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        routeService.updateStatus(8L, new RouteStatusRequest(RouteStatus.COMPLETED));

        assertEquals(RouteStatus.COMPLETED, route.getStatus());
        assertEquals(DriverStatus.RETURNING, driver.getStatus());
        assertEquals(VehicleStatus.IN_TRANSIT, vehicle.getStatus());
        assertEquals(GoodsStatus.ARCHIVED, goods.getStatus());
        assertEquals(3600L, route.getReturnDurationSeconds());
        org.junit.jupiter.api.Assertions.assertNotNull(route.getEstimatedReturnTime());
    }

    @Test
    void confirmingFinalDeliveryCompletesRouteAndStartsReturnAutomatically() throws Exception {
        Driver driver = new Driver("Alex", "alex@example.com", Set.of(LicenseCategory.B));
        setId(driver, 1L);
        driver.markInTransit();
        Vehicle vehicle = new Vehicle("VAN-1", VehicleType.VAN, "Make", "Model", 2022, 5000.0, 20.0, 1000);
        setId(vehicle, 2L);
        vehicle.markInTransit();
        Goods goods = goods(3L, 4000.0, 10.0);
        goods.assignToRoute();
        goods.startTransit();
        Route route = route(driver, vehicle, goods, RouteStatus.ACTIVE);
        setId(route, 8L);
        when(routeRepository.findById(8L)).thenReturn(Optional.of(route));
        when(routeRepository.findByIdForUpdate(8L)).thenReturn(Optional.of(route));
        when(driverRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(driver));
        when(vehicleRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(vehicle));
        when(goodsRepository.findAllByIdForUpdate(List.of(3L))).thenReturn(List.of(goods));
        when(routeRepository.saveAndFlush(any(Route.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var response = routeService.markGoodsDelivered(8L, 3L);

        assertEquals(RouteStatus.COMPLETED, response.status());
        assertEquals(DriverStatus.RETURNING, driver.getStatus());
        assertEquals(VehicleStatus.IN_TRANSIT, vehicle.getStatus());
        assertEquals(GoodsStatus.ARCHIVED, goods.getStatus());
    }

    @Test
    void releasesResourcesAfterEstimatedDepotReturn() throws Exception {
        Driver driver = new Driver("Alex", "alex@example.com", Set.of(LicenseCategory.B));
        setId(driver, 1L);
        driver.markReturning();
        Vehicle vehicle = new Vehicle("VAN-1", VehicleType.VAN, "Make", "Model", 2022, 5000.0, 20.0, 1000);
        setId(vehicle, 2L);
        vehicle.markInTransit();
        Goods goods = goods(3L, 4000.0, 10.0);
        goods.assignToRoute();
        goods.startTransit();
        goods.markDelivered();
        goods.archive();
        Route route = route(driver, vehicle, goods, RouteStatus.ACTIVE);
        route.complete();
        route.setEstimatedReturnTime(OffsetDateTime.now().minusMinutes(1));
        setId(route, 8L);
        when(routeRepository.findAllByStatusInAndEstimatedReturnTimeLessThanEqualAndReturnedAtIsNull(
                anyList(), any(OffsetDateTime.class)
        )).thenReturn(List.of(route));
        when(driverRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(driver));
        when(vehicleRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(vehicle));

        assertEquals(1, routeService.releaseDueReturns());

        assertEquals(DriverStatus.AVAILABLE, driver.getStatus());
        assertEquals(VehicleStatus.AVAILABLE, vehicle.getStatus());
        org.junit.jupiter.api.Assertions.assertNotNull(route.getReturnedAt());
    }

    @Test
    void deletingPlannedRouteReleasesGoods() throws Exception {
        Driver driver = new Driver("Alex", "alex@example.com", Set.of(LicenseCategory.B));
        setId(driver, 1L);
        Vehicle vehicle = new Vehicle("VAN-1", VehicleType.VAN, "Make", "Model", 2022, 5000.0, 20.0, 1000);
        setId(vehicle, 2L);
        Goods goods = goods(3L, 4000.0, 10.0);
        goods.assignToRoute();
        Route route = route(driver, vehicle, goods, RouteStatus.PLANNED);
        setId(route, 8L);
        when(routeRepository.findById(8L)).thenReturn(Optional.of(route));
        when(routeRepository.findByIdForUpdate(8L)).thenReturn(Optional.of(route));
        when(driverRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(driver));
        when(vehicleRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(vehicle));
        when(goodsRepository.findAllByIdForUpdate(List.of(3L))).thenReturn(List.of(goods));

        routeService.delete(8L);

        assertEquals(GoodsStatus.UNASSIGNED, goods.getStatus());
        verify(routeRepository).delete(route);
        verify(routeRepository).flush();
    }

    @Test
    void rejectsDeliveryConfirmationOutOfPlannedOrder() throws Exception {
        Driver driver = new Driver("Alex", "alex@example.com", Set.of(LicenseCategory.B));
        setId(driver, 1L);
        Vehicle vehicle = new Vehicle("VAN-1", VehicleType.VAN, "Make", "Model", 2022, 5000.0, 20.0, 1000);
        setId(vehicle, 2L);
        Goods first = goods(3L, 1000.0, 2.0);
        Goods second = goods(4L, 1000.0, 2.0);
        for (Goods item : List.of(first, second)) {
            item.assignToRoute();
            item.startTransit();
        }
        Route route = route(driver, vehicle, first, RouteStatus.ACTIVE);
        route.addStop(new RouteStop(second, RouteStopType.PICKUP, 3));
        route.addStop(new RouteStop(second, RouteStopType.DELIVERY, 4));
        setId(route, 8L);
        when(routeRepository.findById(8L)).thenReturn(Optional.of(route));
        when(routeRepository.findByIdForUpdate(8L)).thenReturn(Optional.of(route));
        when(driverRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(driver));
        when(vehicleRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(vehicle));
        when(goodsRepository.findAllByIdForUpdate(List.of(3L, 4L))).thenReturn(List.of(first, second));

        assertThrows(RouteConflictException.class, () -> routeService.markGoodsDelivered(8L, 4L));

        assertEquals(GoodsStatus.IN_TRANSIT, second.getStatus());
    }

    private void prepareBasicCreate(Driver driver, Vehicle vehicle, Goods goods) {
        when(driverRepository.findByIdForUpdate(driver.getId())).thenReturn(Optional.of(driver));
        when(vehicleRepository.findByIdForUpdate(vehicle.getId())).thenReturn(Optional.of(vehicle));
        when(goodsRepository.findAllByIdForUpdate(List.of(goods.getId()))).thenReturn(List.of(goods));
        when(directionsService.estimate(anyList()))
                .thenReturn(new RouteTravelEstimate(120_000, Duration.ofHours(6), Duration.ofHours(1)));
        when(routeRepository.existsDriverScheduleOverlap(
                eq(driver.getId()), anyList(), any(OffsetDateTime.class), any(OffsetDateTime.class)
        )).thenReturn(false);
        when(routeRepository.existsVehicleScheduleOverlap(
                eq(vehicle.getId()), anyList(), any(OffsetDateTime.class), any(OffsetDateTime.class)
        )).thenReturn(false);
        when(routeRepository.saveAndFlush(any(Route.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    private static RouteCreateRequest request(Long driverId, Long vehicleId, Long goodsId) {
        return new RouteCreateRequest(
                driverId,
                vehicleId,
                OffsetDateTime.parse("2026-10-15T08:00:00Z"),
                List.of(
                        new RouteStopRequest(goodsId, RouteStopType.PICKUP),
                        new RouteStopRequest(goodsId, RouteStopType.DELIVERY)
                )
        );
    }

    private static Goods goods(Long id, double weight, double volume) throws Exception {
        Goods goods = new Goods(
                "TRK-2026-0001",
                "Client",
                GoodsType.GENERAL,
                null,
                weight,
                volume,
                "Pickup",
                "Delivery"
        );
        setId(goods, id);
        return goods;
    }

    private static Route route(Driver driver, Vehicle vehicle, Goods goods, RouteStatus status) {
        Route route = new Route(
                driver,
                vehicle,
                null,
                OffsetDateTime.parse("2026-10-15T08:00:00Z"),
                OffsetDateTime.parse("2026-10-15T14:00:00Z"),
                120_000L,
                21_600L,
                3_600L
        );
        route.addStop(new RouteStop(goods, RouteStopType.PICKUP, 1));
        route.addStop(new RouteStop(goods, RouteStopType.DELIVERY, 2));
        if (status == RouteStatus.ACTIVE) {
            route.activate();
        }
        return route;
    }

    private static void setId(Object entity, Long id) throws Exception {
        Field idField = entity.getClass().getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(entity, id);
    }
}
