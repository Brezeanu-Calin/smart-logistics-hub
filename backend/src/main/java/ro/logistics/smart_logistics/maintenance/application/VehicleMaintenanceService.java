package ro.logistics.smart_logistics.maintenance.application;

import ro.logistics.smart_logistics.maintenance.domain.VehicleMaintenanceOperation;
import ro.logistics.smart_logistics.maintenance.dto.VehicleMaintenanceComponentResponse;
import ro.logistics.smart_logistics.maintenance.dto.VehicleMaintenanceOperationRequest;
import ro.logistics.smart_logistics.maintenance.persistence.VehicleMaintenanceOperationRepository;
import ro.logistics.smart_logistics.route.domain.RouteStatus;
import ro.logistics.smart_logistics.route.persistence.RouteRepository;
import ro.logistics.smart_logistics.vehicle.application.InvalidVehicleStatusException;
import ro.logistics.smart_logistics.vehicle.application.VehicleNotFoundException;
import ro.logistics.smart_logistics.vehicle.domain.Vehicle;
import ro.logistics.smart_logistics.vehicle.domain.VehicleStatus;
import ro.logistics.smart_logistics.vehicle.persistence.VehicleRepository;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Transactional
public class VehicleMaintenanceService {
    private static final int AUTO_MAINTENANCE_THRESHOLD_PERCENT = 10;
    private static final List<RouteStatus> SCHEDULED_ROUTE_STATUSES =
            List.of(RouteStatus.PLANNED, RouteStatus.ACTIVE);

    private final VehicleMaintenanceOperationRepository operationRepository;
    private final VehicleRepository vehicleRepository;
    private final RouteRepository routeRepository;

    public VehicleMaintenanceService(
            VehicleMaintenanceOperationRepository operationRepository,
            VehicleRepository vehicleRepository,
            RouteRepository routeRepository
    ) {
        this.operationRepository = operationRepository;
        this.vehicleRepository = vehicleRepository;
        this.routeRepository = routeRepository;
    }

    @Transactional(readOnly = true)
    public List<VehicleMaintenanceComponentResponse> componentsFor(Vehicle vehicle) {
        return componentResponses(vehicle, operationsFor(vehicle), OffsetDateTime.now());
    }

    public Vehicle recordOperation(Long vehicleId, VehicleMaintenanceOperationRequest request) {
        Vehicle vehicle = vehicleRepository.findByIdForUpdate(vehicleId)
                .orElseThrow(() -> new VehicleNotFoundException(vehicleId));
        if (vehicle.getStatus() == VehicleStatus.IN_TRANSIT
                || routeRepository.existsByVehicle_IdAndStatusIn(vehicleId, SCHEDULED_ROUTE_STATUSES)) {
            throw new InvalidVehicleStatusException(
                    "Maintenance cannot be recorded while the vehicle is assigned to a planned or active route."
            );
        }

        VehicleMaintenanceCatalog.Definition definition = definitionFor(vehicle, request.componentKey());
        if (request.odometerKm() > vehicle.getCurrentMileageKm()) {
            throw new InvalidMaintenanceOperationException(
                    "The service odometer cannot exceed the vehicle's current odometer."
            );
        }
        operationRepository.saveAndFlush(new VehicleMaintenanceOperation(
                vehicle,
                definition.key(),
                request.performedAt(),
                request.odometerKm(),
                request.costEur()
        ));
        updateAutomaticMaintenanceState(vehicle, OffsetDateTime.now());
        return vehicleRepository.saveAndFlush(vehicle);
    }

    public void completeMaintenance(Vehicle vehicle) {
        if (vehicle.getStatus() == VehicleStatus.IN_TRANSIT
                || routeRepository.existsByVehicle_IdAndStatusIn(vehicle.getId(), SCHEDULED_ROUTE_STATUSES)) {
            throw new InvalidVehicleStatusException(
                    "Maintenance cannot be completed while the vehicle is assigned to a planned or active route."
            );
        }

        OffsetDateTime completedAt = OffsetDateTime.now();
        List<VehicleMaintenanceOperation> completedOperations = VehicleMaintenanceCatalog.forVehicle(vehicle).stream()
                .filter(VehicleMaintenanceCatalog.Definition::hasHealthInterval)
                .map(definition -> new VehicleMaintenanceOperation(
                        vehicle,
                        definition.key(),
                        completedAt,
                        vehicle.getCurrentMileageKm(),
                        null
                ))
                .toList();
        operationRepository.saveAll(completedOperations);
        vehicle.clearAutomaticMaintenance();
    }

    public void updateAutomaticMaintenanceState(Vehicle vehicle, OffsetDateTime now) {
        List<VehicleMaintenanceComponentResponse> components =
                componentResponses(vehicle, operationsFor(vehicle), now);
        boolean maintenanceDue = components.stream()
                .anyMatch(component -> component.healthPercent() != null
                        && component.healthPercent() <= AUTO_MAINTENANCE_THRESHOLD_PERCENT);

        if (maintenanceDue) {
            vehicle.markAutomaticMaintenance();
        } else if (vehicle.isMaintenanceAutoTriggered() && vehicle.getStatus() != VehicleStatus.IN_TRANSIT) {
            vehicle.clearAutomaticMaintenance();
        }
    }

    @Scheduled(fixedDelayString = "${maintenance.check-interval-ms:60000}")
    public void checkDueMaintenance() {
        OffsetDateTime now = OffsetDateTime.now();
        for (Vehicle vehicle : vehicleRepository.findAllForUpdate()) {
            updateAutomaticMaintenanceState(vehicle, now);
        }
    }

    private List<VehicleMaintenanceOperation> operationsFor(Vehicle vehicle) {
        return vehicle.getId() == null
                ? List.of()
                : operationRepository.findAllByVehicle_IdOrderByPerformedAtDescIdDesc(vehicle.getId());
    }

    private static List<VehicleMaintenanceComponentResponse> componentResponses(
            Vehicle vehicle,
            List<VehicleMaintenanceOperation> operations,
            OffsetDateTime now
    ) {
        Map<String, VehicleMaintenanceOperation> latestByComponent = new HashMap<>();
        for (VehicleMaintenanceOperation operation : operations) {
            latestByComponent.putIfAbsent(operation.getComponentKey(), operation);
        }

        List<VehicleMaintenanceComponentResponse> responses = new ArrayList<>();
        for (VehicleMaintenanceCatalog.Definition definition : VehicleMaintenanceCatalog.forVehicle(vehicle)) {
            VehicleMaintenanceOperation latest = latestByComponent.get(definition.key());
            Integer health = null;
            Integer remainingKm = null;
            Integer remainingDays = null;
            if (definition.hasHealthInterval()) {
                if (latest == null) {
                    health = 0;
                    remainingKm = definition.serviceIntervalKm();
                    remainingDays = definition.serviceIntervalMonths() == null
                            ? null
                            : definition.serviceIntervalMonths() * 30;
                } else {
                    double distanceUsedFraction = 0;
                    if (definition.serviceIntervalKm() != null) {
                        long kmSinceService = Math.max(
                                0L,
                                (long) vehicle.getCurrentMileageKm() - latest.getOdometerKm()
                        );
                        distanceUsedFraction = kmSinceService / (double) definition.serviceIntervalKm();
                        remainingKm = (int) Math.max(0L, definition.serviceIntervalKm() - kmSinceService);
                    }

                    double timeUsedFraction = 0;
                    if (definition.serviceIntervalMonths() != null) {
                        OffsetDateTime dueAt = latest.getPerformedAt().plusMonths(definition.serviceIntervalMonths());
                        long totalMillis = Duration.between(latest.getPerformedAt(), dueAt).toMillis();
                        long elapsedMillis = Math.max(0L, Duration.between(latest.getPerformedAt(), now).toMillis());
                        timeUsedFraction = totalMillis <= 0 ? 1 : elapsedMillis / (double) totalMillis;
                        remainingDays = (int) Math.max(0L, Duration.between(now, dueAt).toDays());
                    }

                    double usedFraction = Math.min(1, Math.max(distanceUsedFraction, timeUsedFraction));
                    health = (int) Math.round((1 - usedFraction) * 100);
                }
            }

            responses.add(new VehicleMaintenanceComponentResponse(
                    definition.key(),
                    definition.label(),
                    definition.serviceIntervalKm(),
                    definition.serviceIntervalMonths(),
                    definition.inspectionNote(),
                    latest == null ? null : latest.getPerformedAt(),
                    latest == null ? null : latest.getOdometerKm(),
                    latest == null ? null : latest.getCostEur(),
                    health,
                    remainingKm,
                    remainingDays
            ));
        }
        return List.copyOf(responses);
    }

    private static VehicleMaintenanceCatalog.Definition definitionFor(Vehicle vehicle, String componentKey) {
        return VehicleMaintenanceCatalog.forVehicle(vehicle).stream()
                .filter(definition -> definition.key().equals(componentKey))
                .findFirst()
                .orElseThrow(() -> new InvalidMaintenanceOperationException(
                        "Component " + componentKey + " is not maintained for this vehicle."
                ));
    }
}
