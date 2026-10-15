package ro.logistics.smart_logistics.vehicle.application;

import ro.logistics.smart_logistics.vehicle.domain.Vehicle;
import ro.logistics.smart_logistics.vehicle.domain.VehicleStatus;
import ro.logistics.smart_logistics.vehicle.dto.VehicleRequest;
import ro.logistics.smart_logistics.vehicle.dto.VehicleResponse;
import ro.logistics.smart_logistics.vehicle.persistence.VehicleRepository;
import ro.logistics.smart_logistics.route.domain.RouteStatus;
import ro.logistics.smart_logistics.route.persistence.RouteRepository;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.time.OffsetDateTime;

@Service
@Transactional
public class VehicleService {
    private final VehicleRepository vehicleRepository;
    private final RouteRepository routeRepository;

    public VehicleService(VehicleRepository vehicleRepository, RouteRepository routeRepository) {
        this.vehicleRepository = vehicleRepository;
        this.routeRepository = routeRepository;
    }

    @Transactional(readOnly = true)
    public List<VehicleResponse> findAll() {
        return vehicleRepository.findAllByOrderByLicensePlateAsc().stream()
                .map(VehicleService::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public VehicleResponse findById(Long id) {
        return toResponse(findVehicle(id));
    }

    public VehicleResponse create(VehicleRequest request) {
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
                request.currentMileageKm()
        );
        return save(vehicle, licensePlate);
    }

    public VehicleResponse update(Long id, VehicleRequest request) {
        Vehicle vehicle = lockVehicle(id);
        ensureNotScheduled(vehicle);
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
                request.currentMileageKm()
        );
        return save(vehicle, licensePlate);
    }

    public VehicleResponse updateDispatcherManagedStatus(Long id, VehicleStatus status) {
        Vehicle vehicle = lockVehicle(id);
        if (vehicle.getStatus() == VehicleStatus.IN_TRANSIT) {
            throw new InvalidVehicleStatusException(
                    "A vehicle's status cannot be changed manually while it is IN_TRANSIT on a route."
            );
        }
        ensureNotScheduled(vehicle);
        try {
            vehicle.updateDispatcherManagedStatus(status);
        } catch (IllegalArgumentException exception) {
            throw new InvalidVehicleStatusException(exception.getMessage());
        }
        return toResponse(vehicleRepository.save(vehicle));
    }

    public void delete(Long id) {
        Vehicle vehicle = findVehicle(id);
        vehicleRepository.delete(vehicle);
        try {
            vehicleRepository.flush();
        } catch (DataIntegrityViolationException exception) {
            throw new VehicleInUseException(id);
        }
    }

    private VehicleResponse save(Vehicle vehicle, String licensePlate) {
        try {
            return toResponse(vehicleRepository.saveAndFlush(vehicle));
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

    private Vehicle findVehicle(Long id) {
        return vehicleRepository.findById(id).orElseThrow(() -> new VehicleNotFoundException(id));
    }

    private Vehicle lockVehicle(Long id) {
        return vehicleRepository.findByIdForUpdate(id).orElseThrow(() -> new VehicleNotFoundException(id));
    }

    private static String normalizeLicensePlate(String licensePlate) {
        return licensePlate.trim().toUpperCase(Locale.ROOT);
    }

    private static VehicleResponse toResponse(Vehicle vehicle) {
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
                vehicle.getCurrentMileageKm()
        );
    }
}
