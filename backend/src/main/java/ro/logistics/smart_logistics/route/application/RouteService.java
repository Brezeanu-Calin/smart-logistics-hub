package ro.logistics.smart_logistics.route.application;

import ro.logistics.smart_logistics.driver.application.DriverNotFoundException;
import ro.logistics.smart_logistics.driver.domain.Driver;
import ro.logistics.smart_logistics.driver.domain.DriverStatus;
import ro.logistics.smart_logistics.driver.domain.LicenseCategory;
import ro.logistics.smart_logistics.driver.persistence.DriverRepository;
import ro.logistics.smart_logistics.goods.application.GoodsNotFoundException;
import ro.logistics.smart_logistics.goods.domain.Goods;
import ro.logistics.smart_logistics.goods.domain.GoodsStatus;
import ro.logistics.smart_logistics.goods.persistence.GoodsRepository;
import ro.logistics.smart_logistics.route.domain.Route;
import ro.logistics.smart_logistics.route.domain.RouteStatus;
import ro.logistics.smart_logistics.route.domain.RouteStop;
import ro.logistics.smart_logistics.route.domain.RouteStopType;
import ro.logistics.smart_logistics.route.dto.RouteCreateRequest;
import ro.logistics.smart_logistics.route.dto.RouteCoordinate;
import ro.logistics.smart_logistics.route.dto.RouteResponse;
import ro.logistics.smart_logistics.route.dto.RouteStatusRequest;
import ro.logistics.smart_logistics.route.dto.RouteSimulationPositionResponse;
import ro.logistics.smart_logistics.route.dto.RouteStopRequest;
import ro.logistics.smart_logistics.route.dto.RouteStopResponse;
import ro.logistics.smart_logistics.route.persistence.RouteRepository;
import ro.logistics.smart_logistics.trailer.domain.Trailer;
import ro.logistics.smart_logistics.trailer.domain.TrailerStatus;
import ro.logistics.smart_logistics.trailer.domain.TrailerType;
import ro.logistics.smart_logistics.trailer.persistence.TrailerRepository;
import ro.logistics.smart_logistics.vehicle.application.VehicleNotFoundException;
import ro.logistics.smart_logistics.vehicle.domain.Vehicle;
import ro.logistics.smart_logistics.vehicle.domain.VehicleStatus;
import ro.logistics.smart_logistics.vehicle.domain.VehicleType;
import ro.logistics.smart_logistics.vehicle.persistence.VehicleRepository;
import ro.logistics.smart_logistics.maintenance.application.VehicleMaintenanceService;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional
public class RouteService {
    private static final List<RouteStatus> SCHEDULED_STATUSES = List.of(RouteStatus.PLANNED, RouteStatus.ACTIVE);

    private final RouteRepository routeRepository;
    private final DriverRepository driverRepository;
    private final VehicleRepository vehicleRepository;
    private final TrailerRepository trailerRepository;
    private final GoodsRepository goodsRepository;
    private final EntityManager entityManager;
    private final RouteDirectionsService directionsService;
    private final VehicleMaintenanceService maintenanceService;

    public RouteService(
            RouteRepository routeRepository,
            DriverRepository driverRepository,
            VehicleRepository vehicleRepository,
            TrailerRepository trailerRepository,
            GoodsRepository goodsRepository,
            EntityManager entityManager,
            RouteDirectionsService directionsService,
            VehicleMaintenanceService maintenanceService
    ) {
        this.routeRepository = routeRepository;
        this.driverRepository = driverRepository;
        this.vehicleRepository = vehicleRepository;
        this.trailerRepository = trailerRepository;
        this.goodsRepository = goodsRepository;
        this.entityManager = entityManager;
        this.directionsService = directionsService;
        this.maintenanceService = maintenanceService;
    }

    @Transactional(readOnly = true)
    public List<RouteResponse> findAll() {
        return routeRepository.findAllByOrderByDepartureTimeAsc().stream()
                .map(RouteService::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<RouteSimulationPositionResponse> findActiveSimulationPositions() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        return routeRepository.findAllByStatusOrderByDepartureTimeAsc(RouteStatus.ACTIVE).stream()
                .map(route -> RouteSimulationCalculator.calculate(route, now))
                .toList();
    }

    @Transactional(readOnly = true)
    public RouteResponse findById(Long id) {
        return toResponse(findRoute(id));
    }

    public RouteResponse recalculateDirections(Long id) {
        Route route = findRouteForUpdate(id);
        List<String> cities = route.getStops().stream()
                .sorted(Comparator.comparing(RouteStop::getStopOrder))
                .map(RouteStop::getCity)
                .toList();
        RouteTravelEstimate estimate = directionsService.estimate(cities);
        if (estimate == null || estimate.geometry() == null || estimate.geometry().size() < 2) {
            throw new RoutingUnavailableException("The routing service returned no road route geometry.");
        }

        route.clearGeometryPoints();
        entityManager.flush();
        estimate.geometry().forEach(point -> route.addGeometryPoint(point.latitude(), point.longitude()));
        return toResponse(routeRepository.saveAndFlush(route));
    }

    public RouteResponse create(RouteCreateRequest request) {
        validateDepartureTime(request.departureTime());
        Set<Long> goodsIds = validateStops(request.stops());
        List<Long> sortedGoodsIds = goodsIds.stream().sorted().toList();

        List<Goods> observedGoods = goodsRepository.findAllById(sortedGoodsIds);
        if (observedGoods.size() != sortedGoodsIds.size()) {
            Set<Long> foundIds = observedGoods.stream().map(Goods::getId).collect(Collectors.toSet());
            Long missingId = sortedGoodsIds.stream().filter(id -> !foundIds.contains(id)).findFirst().orElseThrow();
            throw new GoodsNotFoundException(missingId);
        }
        ensureGoodsUnassigned(observedGoods);
        Map<Long, Goods> observedGoodsById = observedGoods.stream()
                .collect(Collectors.toMap(Goods::getId, Function.identity()));
        List<String> cities = request.stops().stream()
                .map(stop -> cityFor(observedGoodsById.get(stop.goodsId()), stop.type()))
                .toList();
        RouteTravelEstimate estimate = directionsService.estimate(cities);
        if (estimate == null || estimate.stopCoordinates() == null
                || estimate.stopCoordinates().size() != request.stops().size()
                || estimate.geometry() == null || estimate.geometry().size() < 2
                || estimate.distanceMeters() <= 0
                || estimate.drivingDuration() == null || estimate.drivingDuration().isNegative()
                || estimate.drivingDuration().isZero()
                || estimate.returnDuration() == null || estimate.returnDuration().isNegative()) {
            throw new RoutingUnavailableException("The routing service returned an incomplete route estimate.");
        }

        Driver driver = driverRepository.findByIdForUpdate(request.driverId())
                .orElseThrow(() -> new DriverNotFoundException(request.driverId()));
        Vehicle vehicle = vehicleRepository.findByIdForUpdate(request.vehicleId())
                .orElseThrow(() -> new VehicleNotFoundException(request.vehicleId()));
        List<Goods> goods = goodsRepository.findAllByIdForUpdate(sortedGoodsIds);
        if (goods.size() != sortedGoodsIds.size()) {
            Set<Long> foundIds = goods.stream().map(Goods::getId).collect(Collectors.toSet());
            Long missingId = sortedGoodsIds.stream().filter(id -> !foundIds.contains(id)).findFirst().orElseThrow();
            throw new GoodsNotFoundException(missingId);
        }
        goods.forEach(good -> entityManager.refresh(good, LockModeType.PESSIMISTIC_WRITE));
        ensureResourcesEligibleForPlanning(driver, vehicle);
        ensureGoodsUnassigned(goods);

        Map<Long, Goods> goodsById = goods.stream().collect(Collectors.toMap(Goods::getId, Function.identity()));
        List<String> lockedCities = request.stops().stream()
                .map(stop -> cityFor(goodsById.get(stop.goodsId()), stop.type()))
                .toList();
        if (!cities.equals(lockedCities)) {
            throw new RouteConflictException("Freight cities changed during route planning; please try again.");
        }

        OffsetDateTime estimatedArrivalTime = request.departureTime().plus(estimate.drivingDuration());
        validateSchedule(request.departureTime(), estimatedArrivalTime);
        OffsetDateTime estimatedReturnTime = estimatedArrivalTime.plus(estimate.returnDuration());
        ensureNoScheduleOverlap(
                driver.getId(),
                vehicle.getId(),
                null,
                request.departureTime(),
                estimatedReturnTime
        );

        BigDecimal totalWeight = goods.stream()
                .map(item -> BigDecimal.valueOf(item.getWeightKg()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalVolume = goods.stream()
                .map(item -> BigDecimal.valueOf(item.getVolumeM3()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Trailer trailer = selectTrailer(
                vehicle,
                totalWeight,
                totalVolume,
                request.departureTime(),
                estimatedReturnTime
        );
        ensureDriverLicense(driver, vehicle.getVehicleType(), trailer != null);

        Route route = new Route(
                driver,
                vehicle,
                trailer,
                request.departureTime(),
                estimatedArrivalTime,
                estimate.distanceMeters(),
                estimate.drivingDuration().toSeconds(),
                estimate.returnDuration().toSeconds()
        );
        for (RouteCoordinate coordinate : estimate.geometry()) {
            route.addGeometryPoint(coordinate.latitude(), coordinate.longitude());
        }
        for (int index = 0; index < request.stops().size(); index++) {
            RouteStopRequest stopRequest = request.stops().get(index);
            route.addStop(new RouteStop(
                    goodsById.get(stopRequest.goodsId()),
                    stopRequest.type(),
                    index + 1,
                    estimate.stopCoordinates().get(index).latitude(),
                    estimate.stopCoordinates().get(index).longitude()
            ));
        }
        goods.forEach(Goods::assignToRoute);
        return toResponse(routeRepository.saveAndFlush(route));
    }

    public RouteResponse updateStatus(Long id, RouteStatusRequest request) {
        Route route = findRouteForMutation(id);
        RouteStatus requestedStatus = request.status();
        if (requestedStatus == RouteStatus.ACTIVE) {
            activate(route);
        } else if (requestedStatus == RouteStatus.COMPLETED) {
            complete(route);
        } else if (requestedStatus == RouteStatus.CANCELLED) {
            cancel(route);
        } else {
            throw new InvalidRouteException("A route can only be activated, completed, or cancelled.");
        }
        return toResponse(routeRepository.saveAndFlush(route));
    }

    public RouteResponse markGoodsDelivered(Long routeId, Long goodsId) {
        Route route = findRouteForMutation(routeId);
        if (route.getStatus() != RouteStatus.ACTIVE) {
            throw new RouteConflictException("Goods can only be delivered on an ACTIVE route.");
        }
        Goods goods = findGoodsOnRoute(route, goodsId);
        ensureDeliveryOrder(route, goodsId);
        goods.markDelivered();
        goodsRepository.save(goods);
        if (route.getGoods().stream().allMatch(item ->
                item.getId().equals(goodsId) || item.getStatus() == GoodsStatus.DELIVERED)) {
            complete(route);
        }
        return toResponse(route);
    }

    private void activate(Route route) {
        requireRouteStatus(route, RouteStatus.PLANNED);
        Driver driver = lockDriver(route.getDriver().getId());
        Vehicle vehicle = lockVehicle(route.getVehicle().getId());
        Trailer trailer = route.getTrailer() == null ? null : lockTrailer(route.getTrailer().getId());
        if (driver.getStatus() != DriverStatus.AVAILABLE
                || vehicle.getStatus() != VehicleStatus.AVAILABLE
                || (trailer != null && trailer.getStatus() != TrailerStatus.AVAILABLE)) {
            throw new RouteConflictException("The assigned driver, vehicle, and trailer must be AVAILABLE to activate.");
        }
        List<Goods> goods = lockRouteGoods(route);
        ensureGoodsStatus(goods, GoodsStatus.ASSIGNED);

        route.activate(OffsetDateTime.now(ZoneOffset.UTC));
        driver.markInTransit();
        vehicle.markInTransit();
        if (trailer != null) {
            trailer.markInTransit();
            trailerRepository.save(trailer);
        }
        goods.forEach(Goods::startTransit);
        goodsRepository.saveAll(goods);
        driverRepository.save(driver);
        vehicleRepository.save(vehicle);
    }

    private void complete(Route route) {
        requireRouteStatus(route, RouteStatus.ACTIVE);
        List<Goods> goods = lockRouteGoods(route);
        ensureGoodsStatus(goods, GoodsStatus.DELIVERED);
        route.complete();
        OffsetDateTime estimatedReturnTime = OffsetDateTime.now().plusSeconds(route.getReturnDurationSeconds());
        route.setEstimatedReturnTime(estimatedReturnTime);
        goods.forEach(Goods::archive);
        goodsRepository.saveAll(goods);

        Driver driver = lockDriver(route.getDriver().getId());
        driver.markReturning();
        driverRepository.save(driver);
        routeRepository.saveAndFlush(route);
    }

    private void cancel(Route route) {
        if (route.getStatus() != RouteStatus.PLANNED && route.getStatus() != RouteStatus.ACTIVE) {
            throw new RouteConflictException("Only PLANNED or ACTIVE routes can be cancelled.");
        }
        boolean wasActive = route.getStatus() == RouteStatus.ACTIVE;
        List<Goods> goods = lockRouteGoods(route);
        route.cancel();
        routeRepository.saveAndFlush(route);
        for (Goods good : goods) {
            if (good.getStatus() == GoodsStatus.ASSIGNED || good.getStatus() == GoodsStatus.IN_TRANSIT) {
                good.releaseFromRoute();
            }
        }
        goodsRepository.saveAll(goods);
        if (wasActive) {
            OffsetDateTime estimatedReturnTime = OffsetDateTime.now().plusSeconds(route.getReturnDurationSeconds());
            route.setEstimatedReturnTime(estimatedReturnTime);
            Driver driver = lockDriver(route.getDriver().getId());
            driver.markReturning();
            driverRepository.save(driver);
        } else {
            route.clearEstimatedReturnTime();
        }
        routeRepository.saveAndFlush(route);
    }

    public int releaseDueReturns() {
        List<Route> dueRoutes = routeRepository.findAllByStatusInAndEstimatedReturnTimeLessThanEqualAndReturnedAtIsNull(
                List.of(RouteStatus.COMPLETED, RouteStatus.CANCELLED), OffsetDateTime.now()
        );
        int released = 0;
        for (Route route : dueRoutes) {
            Driver driver = lockDriver(route.getDriver().getId());
            Vehicle vehicle = lockVehicle(route.getVehicle().getId());
            Trailer trailer = route.getTrailer() == null ? null : lockTrailer(route.getTrailer().getId());
            if (driver.getStatus() == DriverStatus.RETURNING
                    && !routeRepository.existsByDriver_IdAndStatusAndIdNot(
                    driver.getId(), RouteStatus.ACTIVE, route.getId())) {
                driver.markAvailable();
                driverRepository.save(driver);
            }
            if (vehicle.getStatus() == VehicleStatus.IN_TRANSIT
                    && !routeRepository.existsByVehicle_IdAndStatusAndIdNot(
                    vehicle.getId(), RouteStatus.ACTIVE, route.getId())) {
                vehicle.markAvailable();
                vehicleRepository.save(vehicle);
            }
            if (trailer != null && trailer.getStatus() == TrailerStatus.IN_TRANSIT
                    && !routeRepository.existsByTrailer_IdAndStatusAndIdNot(
                    trailer.getId(), RouteStatus.ACTIVE, route.getId())) {
                trailer.markAvailable();
                trailerRepository.save(trailer);
            }
            route.markReturned(OffsetDateTime.now());
            routeRepository.save(route);
            released++;
        }
        return released;
    }

    public void delete(Long id) {
        Route route = findRouteForMutation(id);
        if (route.getStatus() != RouteStatus.PLANNED && route.getStatus() != RouteStatus.CANCELLED) {
            throw new RouteConflictException("Only PLANNED or CANCELLED routes can be deleted.");
        }
        if (route.getStatus() == RouteStatus.CANCELLED
                && route.getEstimatedReturnTime() != null && route.getReturnedAt() == null) {
            throw new RouteConflictException("A cancelled route cannot be deleted while its resources are returning.");
        }
        for (Goods good : lockRouteGoods(route)) {
            if (good.getStatus() == GoodsStatus.ASSIGNED || good.getStatus() == GoodsStatus.IN_TRANSIT) {
                good.releaseFromRoute();
            }
        }
        routeRepository.delete(route);
        routeRepository.flush();
    }

    private Trailer selectTrailer(
            Vehicle vehicle,
            BigDecimal goodsWeight,
            BigDecimal goodsVolume,
            OffsetDateTime departureTime,
            OffsetDateTime arrivalTime
    ) {
        TrailerType compatibleTrailerType = trailerTypeFor(vehicle.getVehicleType());
        BigDecimal vehicleWeight = BigDecimal.valueOf(vehicle.getMaxWeightCapacityKg());
        BigDecimal vehicleVolume = BigDecimal.valueOf(vehicle.getMaxVolumeM3());
        boolean vehicleFits = goodsWeight.compareTo(vehicleWeight) <= 0
                && goodsVolume.compareTo(vehicleVolume) <= 0;
        boolean trailerRequired = vehicle.getVehicleType() == VehicleType.ARTICULATED_TRUCK || !vehicleFits;
        if (!trailerRequired) {
            return null;
        }

        BigDecimal remainingWeight = goodsWeight.subtract(vehicleWeight).max(BigDecimal.ZERO);
        BigDecimal remainingVolume = goodsVolume.subtract(vehicleVolume).max(BigDecimal.ZERO);
        List<Trailer> candidates = trailerRepository.findAllForPlanning(
                compatibleTrailerType,
                List.of(TrailerStatus.AVAILABLE, TrailerStatus.IN_TRANSIT)
        );
        Trailer selected = candidates.stream()
                .filter(trailer -> trailer.getStatus() == TrailerStatus.AVAILABLE
                        || trailer.getStatus() == TrailerStatus.IN_TRANSIT)
                .filter(trailer -> fitsTrailer(trailer, remainingWeight, remainingVolume))
                .filter(trailer -> !routeRepository.existsTrailerScheduleOverlap(
                        trailer.getId(), SCHEDULED_STATUSES, departureTime, arrivalTime
                ))
                .filter(trailer -> !routeRepository.existsByTrailer_IdAndStatusInAndEstimatedReturnTimeAfter(
                        trailer.getId(), List.of(RouteStatus.COMPLETED, RouteStatus.CANCELLED), departureTime
                ))
                .min(Comparator
                        .comparing((Trailer trailer) ->
                                BigDecimal.valueOf(trailer.getMaxWeightCapacityKg()).subtract(remainingWeight))
                        .thenComparing(trailer ->
                                BigDecimal.valueOf(trailer.getMaxVolumeM3()).subtract(remainingVolume))
                        .thenComparing(Trailer::getLicensePlate))
                .orElse(null);
        if (selected == null) {
            throw new RouteConflictException(
                    "No available compatible trailer can carry the goods within the route schedule."
            );
        }
        return selected;
    }

    private static boolean fitsTrailer(Trailer trailer, BigDecimal weightNeeded, BigDecimal volumeNeeded) {
        return BigDecimal.valueOf(trailer.getMaxWeightCapacityKg()).compareTo(weightNeeded) >= 0
                && BigDecimal.valueOf(trailer.getMaxVolumeM3()).compareTo(volumeNeeded) >= 0;
    }

    private void ensureNoScheduleOverlap(
            Long driverId,
            Long vehicleId,
            Long trailerId,
            OffsetDateTime departureTime,
            OffsetDateTime arrivalTime
    ) {
        if (routeRepository.existsDriverScheduleOverlap(
                driverId, SCHEDULED_STATUSES, departureTime, arrivalTime)) {
            throw new RouteConflictException("The driver already has an overlapping planned or active route.");
        }
        if (routeRepository.existsVehicleScheduleOverlap(
                vehicleId, SCHEDULED_STATUSES, departureTime, arrivalTime)) {
            throw new RouteConflictException("The vehicle already has an overlapping planned or active route.");
        }
        if (trailerId != null && routeRepository.existsTrailerScheduleOverlap(
                trailerId, SCHEDULED_STATUSES, departureTime, arrivalTime)) {
            throw new RouteConflictException("The trailer already has an overlapping planned or active route.");
        }
        if (routeRepository.existsByDriver_IdAndStatusInAndEstimatedReturnTimeAfter(
                driverId, List.of(RouteStatus.COMPLETED, RouteStatus.CANCELLED), departureTime
        ) || routeRepository.existsByVehicle_IdAndStatusInAndEstimatedReturnTimeAfter(
                vehicleId, List.of(RouteStatus.COMPLETED, RouteStatus.CANCELLED), departureTime
        ) || (trailerId != null && routeRepository.existsByTrailer_IdAndStatusInAndEstimatedReturnTimeAfter(
                trailerId, List.of(RouteStatus.COMPLETED, RouteStatus.CANCELLED), departureTime
        ))) {
            throw new RouteConflictException("An assigned resource is still returning from a completed route.");
        }
    }

    private void ensureResourcesEligibleForPlanning(Driver driver, Vehicle vehicle) {
        maintenanceService.updateAutomaticMaintenanceState(vehicle, OffsetDateTime.now());
        if (driver.getStatus() == DriverStatus.ON_LEAVE) {
            throw new RouteConflictException("A driver on leave cannot be assigned to a route.");
        }
        if (vehicle.getStatus() == VehicleStatus.MAINTENANCE) {
            throw new RouteConflictException("A vehicle in maintenance cannot be assigned to a route.");
        }
    }

    private static void ensureDriverLicense(Driver driver, VehicleType type, boolean usesTrailer) {
        LicenseCategory required = switch (type) {
            case VAN -> usesTrailer ? LicenseCategory.BE : LicenseCategory.B;
            case RIGID_TRUCK -> usesTrailer ? LicenseCategory.CE : LicenseCategory.C;
            case ARTICULATED_TRUCK -> LicenseCategory.CE;
        };
        if (!driver.getLicenseCategories().contains(required)) {
            throw new RouteConflictException(
                    "The driver does not hold the required " + required + " license category."
            );
        }
    }

    private static TrailerType trailerTypeFor(VehicleType type) {
        return switch (type) {
            case VAN -> TrailerType.VAN_TRAILER;
            case RIGID_TRUCK -> TrailerType.RIGID_TRUCK_TRAILER;
            case ARTICULATED_TRUCK -> TrailerType.ARTICULATED_TRUCK_TRAILER;
        };
    }

    private static Set<Long> validateStops(List<RouteStopRequest> stops) {
        Map<Long, EnumSet<RouteStopType>> stopTypesByGoods = new HashMap<>();
        Map<Long, Integer> pickupOrder = new HashMap<>();
        Map<Long, Integer> deliveryOrder = new HashMap<>();
        for (int index = 0; index < stops.size(); index++) {
            RouteStopRequest stop = stops.get(index);
            if (stop == null || stop.goodsId() == null || stop.type() == null) {
                throw new InvalidRouteException("Every route stop requires a goods ID and a stop type.");
            }
            EnumSet<RouteStopType> types = stopTypesByGoods.computeIfAbsent(
                    stop.goodsId(), ignored -> EnumSet.noneOf(RouteStopType.class)
            );
            if (!types.add(stop.type())) {
                throw new InvalidRouteException("Each good must have exactly one PICKUP and one DELIVERY stop.");
            }
            if (stop.type() == RouteStopType.PICKUP) {
                pickupOrder.put(stop.goodsId(), index);
            } else {
                deliveryOrder.put(stop.goodsId(), index);
            }
        }
        for (Map.Entry<Long, EnumSet<RouteStopType>> entry : stopTypesByGoods.entrySet()) {
            if (!entry.getValue().equals(EnumSet.allOf(RouteStopType.class))
                    || pickupOrder.get(entry.getKey()) >= deliveryOrder.get(entry.getKey())) {
                throw new InvalidRouteException(
                        "Every good must have one PICKUP before its DELIVERY stop."
                );
            }
        }
        return Set.copyOf(stopTypesByGoods.keySet());
    }

    private static void validateDepartureTime(OffsetDateTime departureTime) {
        if (departureTime == null) {
            throw new InvalidRouteException("Departure time is required.");
        }
    }

    private static void validateSchedule(OffsetDateTime departureTime, OffsetDateTime arrivalTime) {
        if (!arrivalTime.isAfter(departureTime)) {
            throw new InvalidRouteException("Estimated arrival time must be after departure time.");
        }
    }

    private static String cityFor(Goods goods, RouteStopType type) {
        return type == RouteStopType.PICKUP ? goods.getPickupCity() : goods.getDeliveryCity();
    }

    private static void ensureDeliveryOrder(Route route, Long goodsId) {
        int targetOrder = route.getStops().stream()
                .filter(stop -> stop.getStopType() == RouteStopType.DELIVERY)
                .filter(stop -> stop.getGoods().getId().equals(goodsId))
                .mapToInt(RouteStop::getStopOrder)
                .findFirst()
                .orElseThrow(() -> new RouteConflictException("Goods has no delivery stop on this route."));
        boolean earlierDeliveryPending = route.getStops().stream()
                .filter(stop -> stop.getStopType() == RouteStopType.DELIVERY)
                .filter(stop -> stop.getStopOrder() < targetOrder)
                .anyMatch(stop -> stop.getGoods().getStatus() != GoodsStatus.DELIVERED);
        if (earlierDeliveryPending) {
            throw new RouteConflictException("Goods must be delivered in the planned route-stop order.");
        }
    }

    private static void ensureGoodsUnassigned(List<Goods> goods) {
        for (Goods good : goods) {
            if (good.getStatus() != GoodsStatus.UNASSIGNED) {
                throw new RouteConflictException(
                        "Goods " + good.getId() + " is not UNASSIGNED and cannot be added to this route."
                );
            }
        }
    }

    private static void ensureGoodsStatus(List<Goods> goods, GoodsStatus status) {
        for (Goods good : goods) {
            if (good.getStatus() != status) {
                throw new RouteConflictException(
                        "Goods " + good.getId() + " must be " + status + " for this route operation."
                );
            }
        }
    }

    private static void requireRouteStatus(Route route, RouteStatus status) {
        if (route.getStatus() != status) {
            throw new RouteConflictException("Route must be " + status + " for this operation.");
        }
    }

    private static Goods findGoodsOnRoute(Route route, Long goodsId) {
        return route.getGoods().stream()
                .filter(goods -> goods.getId().equals(goodsId))
                .findFirst()
                .orElseThrow(() -> new RouteConflictException("Goods " + goodsId + " is not assigned to this route."));
    }

    private List<Goods> lockRouteGoods(Route route) {
        List<Long> ids = route.getGoods().stream().map(Goods::getId).sorted().toList();
        return goodsRepository.findAllByIdForUpdate(ids);
    }

    private Route findRoute(Long id) {
        return routeRepository.findById(id).orElseThrow(() -> new RouteNotFoundException(id));
    }

    private Route findRouteForUpdate(Long id) {
        return routeRepository.findByIdForUpdate(id).orElseThrow(() -> new RouteNotFoundException(id));
    }

    private Route findRouteForMutation(Long id) {
        Route observed = findRoute(id);
        lockDriver(observed.getDriver().getId());
        lockVehicle(observed.getVehicle().getId());
        lockRouteGoods(observed);
        if (observed.getTrailer() != null) {
            lockTrailer(observed.getTrailer().getId());
        }
        Route locked = findRouteForUpdate(id);
        entityManager.refresh(locked, LockModeType.PESSIMISTIC_WRITE);
        return locked;
    }

    private Driver lockDriver(Long id) {
        return driverRepository.findByIdForUpdate(id).orElseThrow(() -> new DriverNotFoundException(id));
    }

    private Vehicle lockVehicle(Long id) {
        return vehicleRepository.findByIdForUpdate(id).orElseThrow(() -> new VehicleNotFoundException(id));
    }

    private Trailer lockTrailer(Long id) {
        return trailerRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new RouteConflictException("Assigned trailer " + id + " no longer exists."));
    }

    private static RouteResponse toResponse(Route route) {
        List<RouteStopResponse> stops = route.getStops().stream()
                .map(stop -> new RouteStopResponse(
                        stop.getGoods().getId(),
                        stop.getGoods().getTrackingCode(),
                        stop.getStopType(),
                        stop.getStopOrder(),
                        stop.getCity(),
                        stop.getLatitude(),
                        stop.getLongitude()
                ))
                .toList();
        List<RouteCoordinate> geometry = route.getGeometryPoints().stream()
                .map(point -> new RouteCoordinate(point.getLatitude(), point.getLongitude()))
                .toList();
        return new RouteResponse(
                route.getId(),
                route.getDriver().getId(),
                route.getDriver().getName(),
                route.getVehicle().getId(),
                route.getVehicle().getLicensePlate(),
                route.getTrailer() == null ? null : route.getTrailer().getId(),
                route.getTrailer() == null ? null : route.getTrailer().getLicensePlate(),
                route.getDepartureTime(),
                route.getEstimatedArrivalTime(),
                route.getEstimatedReturnTime(),
                route.getReturnedAt(),
                route.getDistanceMeters(),
                route.getDrivingDurationSeconds(),
                route.getStatus(),
                stops,
                geometry
        );
    }
}
