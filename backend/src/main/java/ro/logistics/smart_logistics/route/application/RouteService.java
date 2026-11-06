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
import ro.logistics.smart_logistics.route.domain.RouteArchiveRecord;
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
import ro.logistics.smart_logistics.route.persistence.RouteArchiveRecordRepository;
import ro.logistics.smart_logistics.trailer.domain.Trailer;
import ro.logistics.smart_logistics.trailer.domain.TrailerStatus;
import ro.logistics.smart_logistics.trailer.persistence.TrailerRepository;
import ro.logistics.smart_logistics.vehicle.application.VehicleNotFoundException;
import ro.logistics.smart_logistics.vehicle.domain.Vehicle;
import ro.logistics.smart_logistics.vehicle.domain.VehicleStatus;
import ro.logistics.smart_logistics.vehicle.domain.VehicleType;
import ro.logistics.smart_logistics.vehicle.persistence.VehicleRepository;
import ro.logistics.smart_logistics.maintenance.application.VehicleMaintenanceService;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

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
@Validated
public class RouteService {
    private static final int MAX_GOODS_PER_ROUTE = 12;
    private static final List<RouteStatus> SCHEDULED_STATUSES = List.of(RouteStatus.PLANNED, RouteStatus.ACTIVE);

    private final RouteRepository routeRepository;
    private final RouteArchiveRecordRepository routeArchiveRepository;
    private final DriverRepository driverRepository;
    private final VehicleRepository vehicleRepository;
    private final TrailerRepository trailerRepository;
    private final GoodsRepository goodsRepository;
    private final EntityManager entityManager;
    private final RouteDirectionsService directionsService;
    private final VehicleMaintenanceService maintenanceService;
    private final ObjectMapper objectMapper;

    public RouteService(
            RouteRepository routeRepository,
            RouteArchiveRecordRepository routeArchiveRepository,
            DriverRepository driverRepository,
            VehicleRepository vehicleRepository,
            TrailerRepository trailerRepository,
            GoodsRepository goodsRepository,
            EntityManager entityManager,
            RouteDirectionsService directionsService,
            VehicleMaintenanceService maintenanceService,
            ObjectMapper objectMapper
    ) {
        this.routeRepository = routeRepository;
        this.routeArchiveRepository = routeArchiveRepository;
        this.driverRepository = driverRepository;
        this.vehicleRepository = vehicleRepository;
        this.trailerRepository = trailerRepository;
        this.goodsRepository = goodsRepository;
        this.entityManager = entityManager;
        this.directionsService = directionsService;
        this.maintenanceService = maintenanceService;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<RouteResponse> findAll() {
        return routeRepository.findAllByArchivedFalseOrderByDepartureTimeAsc().stream()
                .map(RouteService::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<RouteResponse> findArchived() {
        return routeRepository.findAllByArchivedTrueOrderByDepartureTimeDesc().stream()
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
    public RouteResponse findById(@Positive Long id) {
        return toResponse(findRoute(id));
    }

    public RouteResponse recalculateDirections(@Positive Long id) {
        Route route = findRouteForUpdate(id);
        if (route.isArchived()) {
            throw new RouteConflictException("Archived routes cannot be changed.");
        }
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

    public RouteResponse create(@Valid RouteCreateRequest request) {
        if (request == null) {
            throw new InvalidRouteException("Route details are required.");
        }
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
                goods,
                totalWeight,
                totalVolume,
                request.departureTime(),
                estimatedReturnTime,
                request.trailerId(),
                null
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

    public RouteResponse updatePlanned(@Positive Long id, @Valid RouteCreateRequest request) {
        if (request == null) {
            throw new InvalidRouteException("Route details are required.");
        }
        Route route = findRouteForMutation(id);
        if (route.isArchived() || route.getStatus() != RouteStatus.PLANNED) {
            throw new RouteConflictException("Only planned routes can be edited.");
        }
        validateDepartureTime(request.departureTime());
        Set<Long> requestedGoodsIds = validateStops(request.stops());
        Set<Long> originalGoodsIds = route.getGoods().stream()
                .map(Goods::getId)
                .collect(Collectors.toSet());
        List<Long> allGoodsIds = java.util.stream.Stream.concat(
                        originalGoodsIds.stream(), requestedGoodsIds.stream())
                .distinct()
                .sorted()
                .toList();
        List<Goods> goods = goodsRepository.findAllByIdForUpdate(allGoodsIds);
        if (goods.size() != allGoodsIds.size()) {
            Set<Long> foundIds = goods.stream().map(Goods::getId).collect(Collectors.toSet());
            Long missingId = allGoodsIds.stream().filter(goodsId -> !foundIds.contains(goodsId)).findFirst().orElseThrow();
            throw new GoodsNotFoundException(missingId);
        }
        Map<Long, Goods> goodsById = goods.stream().collect(Collectors.toMap(Goods::getId, Function.identity()));
        for (Long goodsId : requestedGoodsIds) {
            Goods good = goodsById.get(goodsId);
            if (good.isArchived()) {
                throw new RouteConflictException("Archived goods " + goodsId + " cannot be assigned to a route.");
            }
            if (good.getStatus() != GoodsStatus.UNASSIGNED
                    && !(originalGoodsIds.contains(goodsId) && good.getStatus() == GoodsStatus.ASSIGNED)) {
                throw new RouteConflictException(
                        "Goods " + goodsId + " is no longer available for this route."
                );
            }
        }

        List<String> cities = request.stops().stream()
                .map(stop -> cityFor(goodsById.get(stop.goodsId()), stop.type()))
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

        Driver driver = lockDriver(request.driverId());
        Vehicle vehicle = lockVehicle(request.vehicleId());
        List<Goods> selectedGoods = requestedGoodsIds.stream().map(goodsById::get).toList();
        ensureResourcesEligibleForPlanning(driver, vehicle);
        OffsetDateTime estimatedArrivalTime = request.departureTime().plus(estimate.drivingDuration());
        validateSchedule(request.departureTime(), estimatedArrivalTime);
        OffsetDateTime estimatedReturnTime = estimatedArrivalTime.plus(estimate.returnDuration());
        ensureNoScheduleOverlapExcludingRoute(
                route.getId(),
                driver.getId(),
                vehicle.getId(),
                request.departureTime(),
                estimatedReturnTime
        );

        BigDecimal totalWeight = selectedGoods.stream()
                .map(item -> BigDecimal.valueOf(item.getWeightKg()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalVolume = selectedGoods.stream()
                .map(item -> BigDecimal.valueOf(item.getVolumeM3()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        Trailer trailer = selectTrailer(
                vehicle,
                selectedGoods,
                totalWeight,
                totalVolume,
                request.departureTime(),
                estimatedReturnTime,
                request.trailerId(),
                route.getId()
        );
        ensureDriverLicense(driver, vehicle.getVehicleType(), trailer != null);

        Set<Long> requestedGoodsSet = Set.copyOf(requestedGoodsIds);
        goods.stream()
                .filter(good -> originalGoodsIds.contains(good.getId()) && !requestedGoodsSet.contains(good.getId()))
                .filter(good -> good.getStatus() == GoodsStatus.ASSIGNED)
                .forEach(Goods::releaseFromRoute);
        selectedGoods.stream()
                .filter(good -> good.getStatus() == GoodsStatus.UNASSIGNED)
                .forEach(Goods::assignToRoute);

        route.updatePlannedDetails(
                driver,
                vehicle,
                trailer,
                request.departureTime(),
                estimatedArrivalTime,
                estimate.distanceMeters(),
                estimate.drivingDuration().toSeconds(),
                estimate.returnDuration().toSeconds()
        );
        route.clearStops();
        route.clearGeometryPoints();
        entityManager.flush();
        for (RouteCoordinate coordinate : estimate.geometry()) {
            route.addGeometryPoint(coordinate.latitude(), coordinate.longitude());
        }
        for (int index = 0; index < request.stops().size(); index++) {
            RouteStopRequest stop = request.stops().get(index);
            route.addStop(new RouteStop(
                    goodsById.get(stop.goodsId()),
                    stop.type(),
                    index + 1,
                    estimate.stopCoordinates().get(index).latitude(),
                    estimate.stopCoordinates().get(index).longitude()
            ));
        }
        return toResponse(routeRepository.saveAndFlush(route));
    }

    public RouteResponse updateStatus(@Positive Long id, @Valid RouteStatusRequest request) {
        Route route = findRouteForMutation(id);
        if (route.isArchived()) {
            throw new RouteConflictException("Archived routes cannot be changed.");
        }
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

    public RouteResponse markGoodsDelivered(@Positive Long routeId, @Positive Long goodsId) {
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
        saveArchiveSnapshot(route);
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
            if (route.isArchived()) {
                saveArchiveSnapshot(route);
            }
            released++;
        }
        return released;
    }

    public void delete(@Positive Long id) {
        Route route = findRouteForMutation(id);
        if (route.getStatus() == RouteStatus.ACTIVE) {
            throw new RouteConflictException("An ACTIVE route cannot be archived.");
        }
        if (route.getStatus() == RouteStatus.PLANNED) {
            List<Goods> goods = lockRouteGoods(route);
            for (Goods good : goods) {
                if (good.getStatus() == GoodsStatus.ASSIGNED || good.getStatus() == GoodsStatus.IN_TRANSIT) {
                    good.releaseFromRoute();
                }
            }
            goodsRepository.saveAll(goods);
        }
        route.archive();
        routeRepository.saveAndFlush(route);
        saveArchiveSnapshot(route);
    }

    private void saveArchiveSnapshot(Route route) {
        try {
            String snapshot = objectMapper.writeValueAsString(toResponse(route));
            OffsetDateTime archivedAt = OffsetDateTime.now(ZoneOffset.UTC);
            routeArchiveRepository.findByRouteId(route.getId())
                    .ifPresentOrElse(
                            existing -> {
                                existing.update(archivedAt, snapshot);
                                routeArchiveRepository.save(existing);
                            },
                            () -> routeArchiveRepository.save(
                                    new RouteArchiveRecord(route.getId(), archivedAt, snapshot)
                            )
                    );
        } catch (JacksonException exception) {
            throw new IllegalStateException("Could not preserve the route archive snapshot.", exception);
        }
    }

    private Trailer selectTrailer(
            Vehicle vehicle,
            List<Goods> goods,
            BigDecimal goodsWeight,
            BigDecimal goodsVolume,
            OffsetDateTime departureTime,
            OffsetDateTime arrivalTime,
            Long requestedTrailerId,
            Long excludedRouteId
    ) {
        BigDecimal vehicleWeight = BigDecimal.valueOf(vehicle.getMaxWeightCapacityKg());
        BigDecimal vehicleVolume = BigDecimal.valueOf(vehicle.getMaxVolumeM3());
        boolean vehicleFits = goodsWeight.compareTo(vehicleWeight) <= 0
                && goodsVolume.compareTo(vehicleVolume) <= 0;
        boolean specializedCargo = goods.stream().anyMatch(item -> item.getGoodsType().requiresSpecializedTrailer());
        boolean trailerRequired = vehicle.getVehicleType() == VehicleType.ARTICULATED_TRUCK
                || !vehicleFits
                || specializedCargo;
        if (vehicle.getVehicleType() == VehicleType.ARTICULATED_TRUCK && requestedTrailerId == null) {
            throw new RouteConflictException("An articulated truck requires an explicitly selected compatible trailer.");
        }
        if (!trailerRequired && requestedTrailerId == null) {
            return null;
        }

        BigDecimal requiredWeight = specializedCargo
                ? goodsWeight
                : goodsWeight.subtract(vehicleWeight).max(BigDecimal.ZERO);
        BigDecimal requiredVolume = specializedCargo
                ? goodsVolume
                : goodsVolume.subtract(vehicleVolume).max(BigDecimal.ZERO);
        List<Trailer> candidates = requestedTrailerId == null
                ? trailerRepository.findAllForPlanning(
                        vehicle.getVehicleType(),
                        List.of(TrailerStatus.AVAILABLE)
                )
                : List.of(trailerRepository.findByIdForUpdate(requestedTrailerId)
                        .orElseThrow(() -> new RouteConflictException(
                                "Selected trailer " + requestedTrailerId + " does not exist."
                        )));
        Trailer selected = candidates.stream()
                .filter(trailer -> trailer.getStatus() == TrailerStatus.AVAILABLE)
                .filter(trailer -> trailer.getCompatibleVehicleType() == vehicle.getVehicleType())
                .filter(trailer -> goods.stream()
                        .allMatch(item -> trailer.getTrailerType().supports(item.getGoodsType())))
                .filter(trailer -> fitsTrailer(trailer, requiredWeight, requiredVolume))
                .filter(trailer -> excludedRouteId == null
                        ? !routeRepository.existsTrailerScheduleOverlap(
                                trailer.getId(), SCHEDULED_STATUSES, departureTime, arrivalTime)
                        : !routeRepository.existsTrailerScheduleOverlapExcludingRoute(
                                trailer.getId(), SCHEDULED_STATUSES, departureTime, arrivalTime, excludedRouteId))
                .filter(trailer -> !routeRepository.existsByTrailer_IdAndStatusInAndEstimatedReturnTimeAfter(
                        trailer.getId(), List.of(RouteStatus.COMPLETED, RouteStatus.CANCELLED), departureTime
                ))
                .min(Comparator
                        .comparing((Trailer trailer) ->
                                BigDecimal.valueOf(trailer.getMaxWeightCapacityKg()).subtract(requiredWeight))
                        .thenComparing(trailer ->
                                BigDecimal.valueOf(trailer.getMaxVolumeM3()).subtract(requiredVolume))
                        .thenComparing(Trailer::getLicensePlate))
                .orElse(null);
        if (selected == null) {
            throw new RouteConflictException(
                    requestedTrailerId == null
                            ? "No available compatible trailer can carry the goods within the route schedule."
                            : "The selected trailer is unavailable, incompatible with this vehicle or freight, over capacity, or already scheduled."
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

    private void ensureNoScheduleOverlapExcludingRoute(
            Long excludedRouteId,
            Long driverId,
            Long vehicleId,
            OffsetDateTime departureTime,
            OffsetDateTime arrivalTime
    ) {
        if (routeRepository.existsDriverScheduleOverlapExcludingRoute(
                driverId, SCHEDULED_STATUSES, departureTime, arrivalTime, excludedRouteId)) {
            throw new RouteConflictException("The driver already has an overlapping planned or active route.");
        }
        if (routeRepository.existsVehicleScheduleOverlapExcludingRoute(
                vehicleId, SCHEDULED_STATUSES, departureTime, arrivalTime, excludedRouteId)) {
            throw new RouteConflictException("The vehicle already has an overlapping planned or active route.");
        }
        if (routeRepository.existsByDriver_IdAndStatusInAndEstimatedReturnTimeAfter(
                driverId, List.of(RouteStatus.COMPLETED, RouteStatus.CANCELLED), departureTime)
                || routeRepository.existsByVehicle_IdAndStatusInAndEstimatedReturnTimeAfter(
                vehicleId, List.of(RouteStatus.COMPLETED, RouteStatus.CANCELLED), departureTime)) {
            throw new RouteConflictException("An assigned resource is still returning from a completed route.");
        }
    }

    private void ensureResourcesEligibleForPlanning(Driver driver, Vehicle vehicle) {
        maintenanceService.updateAutomaticMaintenanceState(vehicle, OffsetDateTime.now());
        if (driver.isArchived() || vehicle.isArchived()) {
            throw new RouteConflictException("Archived drivers and vehicles cannot be assigned to a route.");
        }
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

    private static Set<Long> validateStops(List<RouteStopRequest> stops) {
        if (stops == null || stops.isEmpty() || stops.size() > MAX_GOODS_PER_ROUTE * 2) {
            throw new InvalidRouteException(
                    "A route can contain at most " + MAX_GOODS_PER_ROUTE + " freight items."
            );
        }
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
        if (stopTypesByGoods.size() > MAX_GOODS_PER_ROUTE) {
            throw new InvalidRouteException(
                    "A route can contain at most " + MAX_GOODS_PER_ROUTE + " freight items."
            );
        }
        return Set.copyOf(stopTypesByGoods.keySet());
    }

    private static void validateDepartureTime(OffsetDateTime departureTime) {
        if (departureTime == null) {
            throw new InvalidRouteException("Departure time is required.");
        }
        if (!departureTime.isAfter(OffsetDateTime.now(ZoneOffset.UTC))) {
            throw new InvalidRouteException("Departure time must be in the future.");
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
            if (good.isArchived() || good.getStatus() != GoodsStatus.UNASSIGNED) {
                throw new RouteConflictException(
                        "Goods " + good.getId() + " is archived or not UNASSIGNED and cannot be added to this route."
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
                route.isArchived(),
                stops,
                geometry
        );
    }
}
