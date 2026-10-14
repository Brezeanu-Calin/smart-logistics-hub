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
import ro.logistics.smart_logistics.route.dto.RouteResponse;
import ro.logistics.smart_logistics.route.dto.RouteStatusRequest;
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

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
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

    public RouteService(
            RouteRepository routeRepository,
            DriverRepository driverRepository,
            VehicleRepository vehicleRepository,
            TrailerRepository trailerRepository,
            GoodsRepository goodsRepository,
            EntityManager entityManager
    ) {
        this.routeRepository = routeRepository;
        this.driverRepository = driverRepository;
        this.vehicleRepository = vehicleRepository;
        this.trailerRepository = trailerRepository;
        this.goodsRepository = goodsRepository;
        this.entityManager = entityManager;
    }

    @Transactional(readOnly = true)
    public List<RouteResponse> findAll() {
        return routeRepository.findAllByOrderByDepartureTimeAsc().stream()
                .map(RouteService::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public RouteResponse findById(Long id) {
        return toResponse(findRoute(id));
    }

    public RouteResponse create(RouteCreateRequest request) {
        validateSchedule(request.departureTime(), request.estimatedArrivalTime());
        Set<Long> goodsIds = validateStops(request.stops());
        List<Long> sortedGoodsIds = goodsIds.stream().sorted().toList();

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

        ensureResourcesEligibleForPlanning(driver, vehicle);
        ensureNoScheduleOverlap(
                driver.getId(),
                vehicle.getId(),
                null,
                request.departureTime(),
                request.estimatedArrivalTime()
        );
        ensureGoodsUnassigned(goods);

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
                request.estimatedArrivalTime()
        );
        ensureDriverLicense(driver, vehicle.getVehicleType(), trailer != null);

        Route route = new Route(
                driver,
                vehicle,
                trailer,
                request.departureTime(),
                request.estimatedArrivalTime()
        );
        Map<Long, Goods> goodsById = goods.stream().collect(Collectors.toMap(Goods::getId, Function.identity()));
        for (int index = 0; index < request.stops().size(); index++) {
            RouteStopRequest stopRequest = request.stops().get(index);
            route.addStop(new RouteStop(
                    goodsById.get(stopRequest.goodsId()),
                    stopRequest.type(),
                    index + 1
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
        goods.markDelivered();
        goodsRepository.save(goods);
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

        route.activate();
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
        routeRepository.saveAndFlush(route);
        releaseResourcesIfNoActiveRoute(route);
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
            releaseResourcesIfNoActiveRoute(route);
        }
    }

    private void releaseResourcesIfNoActiveRoute(Route route) {
        Driver driver = lockDriver(route.getDriver().getId());
        Vehicle vehicle = lockVehicle(route.getVehicle().getId());
        Trailer trailer = route.getTrailer() == null ? null : lockTrailer(route.getTrailer().getId());

        if (!routeRepository.existsByDriver_IdAndStatusAndIdNot(
                driver.getId(), RouteStatus.ACTIVE, route.getId())) {
            driver.markAvailable();
            driverRepository.save(driver);
        }
        if (!routeRepository.existsByVehicle_IdAndStatusAndIdNot(
                vehicle.getId(), RouteStatus.ACTIVE, route.getId())) {
            vehicle.markAvailable();
            vehicleRepository.save(vehicle);
        }
        if (trailer != null && !routeRepository.existsByTrailer_IdAndStatusAndIdNot(
                trailer.getId(), RouteStatus.ACTIVE, route.getId())) {
            trailer.markAvailable();
            trailerRepository.save(trailer);
        }
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
    }

    private static void ensureResourcesEligibleForPlanning(Driver driver, Vehicle vehicle) {
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

    private static void validateSchedule(OffsetDateTime departureTime, OffsetDateTime arrivalTime) {
        if (departureTime == null || arrivalTime == null || !arrivalTime.isAfter(departureTime)) {
            throw new InvalidRouteException("Estimated arrival time must be after departure time.");
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
                        stop.getAddress()
                ))
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
                route.getStatus(),
                stops
        );
    }
}
