package ro.logistics.smart_logistics.vehicle.application;

import ro.logistics.smart_logistics.vehicle.domain.Vehicle;
import ro.logistics.smart_logistics.vehicle.domain.VehicleStatus;
import ro.logistics.smart_logistics.vehicle.domain.TransmissionType;
import ro.logistics.smart_logistics.vehicle.domain.VehicleType;
import ro.logistics.smart_logistics.vehicle.dto.VehicleRequest;
import ro.logistics.smart_logistics.vehicle.dto.VehicleResponse;
import ro.logistics.smart_logistics.vehicle.persistence.VehicleRepository;
import ro.logistics.smart_logistics.route.domain.RouteStatus;
import ro.logistics.smart_logistics.route.persistence.RouteRepository;
import ro.logistics.smart_logistics.maintenance.application.VehicleMaintenanceService;
import ro.logistics.smart_logistics.maintenance.dto.VehicleMaintenanceOperationRequest;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.List;
import java.util.Locale;
import java.time.OffsetDateTime;

@Service
@Transactional
@Validated
public class VehicleService {
    private final VehicleRepository vehicleRepository;
    private final RouteRepository routeRepository;
    private final VehicleMaintenanceService maintenanceService;

    public VehicleService(
            VehicleRepository vehicleRepository,
            RouteRepository routeRepository,
            VehicleMaintenanceService maintenanceService
    ) {
        this.vehicleRepository = vehicleRepository;
        this.routeRepository = routeRepository;
        this.maintenanceService = maintenanceService;
    }

    public List<VehicleResponse> findAll() {
        return vehicleRepository.findAllForUpdate().stream()
                .map(vehicle -> {
                    maintenanceService.updateAutomaticMaintenanceState(vehicle, OffsetDateTime.now());
                    return toResponse(vehicle);
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public List<VehicleResponse> findArchived() {
        return vehicleRepository.findAllByArchivedTrueOrderByLicensePlateAsc().stream()
                .map(this::toResponse)
                .toList();
    }

    public VehicleResponse findById(@Positive Long id) {
        Vehicle vehicle = lockVehicle(id);
        maintenanceService.updateAutomaticMaintenanceState(vehicle, OffsetDateTime.now());
        return toResponse(vehicle);
    }

    public VehicleResponse create(@Valid VehicleRequest request) {
        validateTransmission(request.vehicleType(), request.transmissionType());
        String licensePlate = normalizeLicensePlate(request.licensePlate());
        ensureLicensePlateAvailable(licensePlate, null);
        Vehicle vehicle = new Vehicle(
                licensePlate,
                request.vehicleType(),
                request.make().trim(),
                request.model().trim(),
                request.year(),
                request.maxWeightCapacityKg(),
                request.maxVolumeM3(),
                request.currentMileageKm(),
                request.transmissionType()
        );
        Vehicle saved = saveEntity(vehicle, licensePlate);
        maintenanceService.initializeHealth(saved);
        maintenanceService.updateAutomaticMaintenanceState(saved, OffsetDateTime.now());
        return toResponse(vehicleRepository.saveAndFlush(saved));
    }

    public VehicleResponse update(@Positive Long id, @Valid VehicleRequest request) {
        Vehicle vehicle = lockVehicle(id);
        ensureNotArchived(vehicle);
        ensureNotScheduled(vehicle);
        validateTransmission(request.vehicleType(), request.transmissionType());
        if (request.currentMileageKm() < vehicle.getCurrentMileageKm()) {
            throw new InvalidVehicleStatusException("Vehicle mileage cannot be reduced below its recorded odometer.");
        }
        String licensePlate = normalizeLicensePlate(request.licensePlate());
        ensureLicensePlateAvailable(licensePlate, id);
        vehicle.updateDetails(
                licensePlate,
                request.vehicleType(),
                request.make().trim(),
                request.model().trim(),
                request.year(),
                request.maxWeightCapacityKg(),
                request.maxVolumeM3(),
                request.currentMileageKm(),
                request.transmissionType()
        );
        Vehicle saved = saveEntity(vehicle, licensePlate);
        maintenanceService.updateAutomaticMaintenanceState(saved, OffsetDateTime.now());
        return toResponse(vehicleRepository.saveAndFlush(saved));
    }

    public VehicleResponse updateDispatcherManagedStatus(@Positive Long id, @NotNull VehicleStatus status) {
        Vehicle vehicle = lockVehicle(id);
        ensureNotArchived(vehicle);
        if (vehicle.getStatus() == VehicleStatus.IN_TRANSIT) {
            throw new InvalidVehicleStatusException(
                    "A vehicle's status cannot be changed manually while it is IN_TRANSIT on a route."
            );
        }
        ensureNotScheduled(vehicle);
        OffsetDateTime now = OffsetDateTime.now();
        maintenanceService.updateAutomaticMaintenanceState(vehicle, now);
        if (status == VehicleStatus.AVAILABLE && vehicle.getStatus() == VehicleStatus.MAINTENANCE) {
            maintenanceService.completeMaintenance(vehicle);
        }
        try {
            vehicle.updateDispatcherManagedStatus(status);
        } catch (IllegalArgumentException exception) {
            throw new InvalidVehicleStatusException(exception.getMessage());
        }
        return toResponse(vehicleRepository.save(vehicle));
    }

    public VehicleResponse recordMaintenanceOperation(
            @Positive Long id,
            @Valid VehicleMaintenanceOperationRequest request
    ) {
        Vehicle vehicle = lockVehicle(id);
        ensureNotArchived(vehicle);
        vehicle = maintenanceService.recordOperation(id, request);
        return toResponse(vehicle);
    }

    public void delete(@Positive Long id) {
        Vehicle vehicle = lockVehicle(id);
        ensureNotScheduled(vehicle);
        if (vehicle.isArchived()) {
            return;
        }
        vehicle.archive();
        vehicleRepository.saveAndFlush(vehicle);
    }

    private Vehicle saveEntity(Vehicle vehicle, String licensePlate) {
        try {
            return vehicleRepository.saveAndFlush(vehicle);
        } catch (DataIntegrityViolationException exception) {
            throw new DuplicateVehicleLicensePlateException(licensePlate);
        }
    }

    private void ensureLicensePlateAvailable(String licensePlate, Long id) {
        boolean exists = id == null
                ? vehicleRepository.existsByLicensePlateIgnoreCase(licensePlate)
                : vehicleRepository.existsByLicensePlateIgnoreCaseAndIdNot(licensePlate, id);
        if (exists) {
            throw new DuplicateVehicleLicensePlateException(licensePlate);
        }
    }

    private void ensureNotScheduled(Vehicle vehicle) {
        if (routeRepository.existsByVehicle_IdAndStatusIn(
                vehicle.getId(), List.of(RouteStatus.PLANNED, RouteStatus.ACTIVE))
                || vehicle.getStatus() == VehicleStatus.IN_TRANSIT
                || routeRepository.existsByVehicle_IdAndStatusInAndEstimatedReturnTimeAfter(
                vehicle.getId(), List.of(RouteStatus.COMPLETED, RouteStatus.CANCELLED), OffsetDateTime.now())) {
            throw new VehicleScheduledException(vehicle.getId());
        }
    }

    private Vehicle lockVehicle(Long id) {
        return vehicleRepository.findByIdForUpdate(id).orElseThrow(() -> new VehicleNotFoundException(id));
    }

    private static void ensureNotArchived(Vehicle vehicle) {
        if (vehicle.isArchived()) {
            throw new InvalidVehicleStatusException("Archived vehicles cannot be changed.");
        }
    }

    private static String normalizeLicensePlate(String licensePlate) {
        return licensePlate.trim().toUpperCase(Locale.ROOT);
    }

    private VehicleResponse toResponse(Vehicle vehicle) {
        return new VehicleResponse(
                vehicle.getId(),
                vehicle.getLicensePlate(),
                vehicle.getVehicleType(),
                vehicle.getMake(),
                vehicle.getModel(),
                vehicle.getYear(),
                vehicle.getMaxWeightCapacityKg(),
                vehicle.getMaxVolumeM3(),
                vehicle.getStatus(),
                vehicle.getCurrentMileageKm(),
                vehicle.getTransmissionType(),
                vehicle.isMaintenanceAutoTriggered(),
                maintenanceService.componentsFor(vehicle),
                vehicle.isArchived()
        );
    }

    private static void validateTransmission(VehicleType type, TransmissionType transmissionType) {
        boolean valid = type == VehicleType.VAN
                ? transmissionType == TransmissionType.MANUAL || transmissionType == TransmissionType.AUTOMATIC
                : transmissionType == TransmissionType.AMT;
        if (!valid) {
            throw new InvalidVehicleStatusException(
                    type == VehicleType.VAN
                            ? "Vans must use MANUAL or AUTOMATIC transmission."
                            : "Rigid and articulated trucks must use AMT transmission."
            );
        }
    }
}
