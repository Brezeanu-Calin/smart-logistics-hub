package ro.logistics.smart_logistics.maintenance.application;

import ro.logistics.smart_logistics.maintenance.domain.TrailerMaintenanceOperation;
import ro.logistics.smart_logistics.maintenance.application.InvalidMaintenanceOperationException;
import ro.logistics.smart_logistics.maintenance.dto.TrailerMaintenanceComponentResponse;
import ro.logistics.smart_logistics.maintenance.dto.TrailerMaintenanceOperationRequest;
import ro.logistics.smart_logistics.maintenance.dto.TrailerMaintenanceRecordResponse;
import ro.logistics.smart_logistics.maintenance.persistence.TrailerMaintenanceOperationRepository;
import ro.logistics.smart_logistics.route.domain.RouteStatus;
import ro.logistics.smart_logistics.route.persistence.RouteRepository;
import ro.logistics.smart_logistics.trailer.application.TrailerNotFoundException;
import ro.logistics.smart_logistics.trailer.domain.Trailer;
import ro.logistics.smart_logistics.trailer.domain.TrailerStatus;
import ro.logistics.smart_logistics.trailer.persistence.TrailerRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Transactional
public class TrailerMaintenanceService {
    private static final int SERVICE_INTERVAL_MONTHS = 12;
    private static final List<RouteStatus> SCHEDULED_STATUSES = List.of(RouteStatus.PLANNED, RouteStatus.ACTIVE);
    private static final List<ComponentDefinition> COMPONENTS = List.of(
            new ComponentDefinition("brakes", "Brakes"),
            new ComponentDefinition("tyres", "Tyres"),
            new ComponentDefinition("lights", "Lights & electrical"),
            new ComponentDefinition("chassis", "Chassis & coupling")
    );

    private final TrailerMaintenanceOperationRepository operationRepository;
    private final TrailerRepository trailerRepository;
    private final RouteRepository routeRepository;

    public TrailerMaintenanceService(
            TrailerMaintenanceOperationRepository operationRepository,
            TrailerRepository trailerRepository,
            RouteRepository routeRepository
    ) {
        this.operationRepository = operationRepository;
        this.trailerRepository = trailerRepository;
        this.routeRepository = routeRepository;
    }

    @Transactional(readOnly = true)
    public List<TrailerMaintenanceComponentResponse> componentsFor(Trailer trailer) {
        return componentResponses(trailer, operationsFor(trailer), OffsetDateTime.now());
    }

    @Transactional(readOnly = true)
    public List<TrailerMaintenanceRecordResponse> historyFor(Trailer trailer) {
        return operationsFor(trailer).stream()
                .map(operation -> new TrailerMaintenanceRecordResponse(
                        operation.getComponentKey(),
                        componentFor(operation.getComponentKey()).label(),
                        operation.getPerformedAt(),
                        operation.getCostEur()
                ))
                .toList();
    }

    public void recordOperation(Long trailerId, TrailerMaintenanceOperationRequest request) {
        Trailer trailer = trailerRepository.findByIdForUpdate(trailerId)
                .orElseThrow(() -> new TrailerNotFoundException(trailerId));
        if (trailer.getStatus() == TrailerStatus.IN_TRANSIT
                || routeRepository.existsByTrailer_IdAndStatusIn(trailerId, SCHEDULED_STATUSES)) {
            throw new InvalidMaintenanceOperationException(
                    "Maintenance cannot be recorded while the trailer is assigned to a planned or active route."
            );
        }
        ComponentDefinition definition = componentFor(request.componentKey());
        operationRepository.saveAndFlush(new TrailerMaintenanceOperation(
                trailer,
                definition.key(),
                request.performedAt(),
                request.costEur()
        ));
    }

    private List<TrailerMaintenanceOperation> operationsFor(Trailer trailer) {
        return trailer.getId() == null
                ? List.of()
                : operationRepository.findAllByTrailer_IdOrderByPerformedAtDescIdDesc(trailer.getId());
    }

    private static List<TrailerMaintenanceComponentResponse> componentResponses(
            Trailer trailer,
            List<TrailerMaintenanceOperation> operations,
            OffsetDateTime now
    ) {
        Map<String, TrailerMaintenanceOperation> latestByComponent = new HashMap<>();
        for (TrailerMaintenanceOperation operation : operations) {
            latestByComponent.putIfAbsent(operation.getComponentKey(), operation);
        }

        return COMPONENTS.stream().map(definition -> {
            TrailerMaintenanceOperation latest = latestByComponent.get(definition.key());
            if (latest == null) {
                return new TrailerMaintenanceComponentResponse(
                        definition.key(), definition.label(), SERVICE_INTERVAL_MONTHS,
                        null, null, null, null
                );
            }
            OffsetDateTime dueAt = latest.getPerformedAt().plusMonths(SERVICE_INTERVAL_MONTHS);
            long totalMillis = Duration.between(latest.getPerformedAt(), dueAt).toMillis();
            long elapsedMillis = Math.max(0L, Duration.between(latest.getPerformedAt(), now).toMillis());
            int health = (int) Math.round((1 - Math.min(1, totalMillis <= 0
                    ? 1 : elapsedMillis / (double) totalMillis)) * 100);
            return new TrailerMaintenanceComponentResponse(
                    definition.key(),
                    definition.label(),
                    SERVICE_INTERVAL_MONTHS,
                    latest.getPerformedAt(),
                    latest.getCostEur(),
                    health,
                    (int) Math.max(0L, Duration.between(now, dueAt).toDays())
            );
        }).toList();
    }

    private static ComponentDefinition componentFor(String key) {
        return COMPONENTS.stream()
                .filter(component -> component.key().equals(key))
                .findFirst()
                .orElseThrow(() -> new InvalidMaintenanceOperationException(
                        "Trailer component " + key + " is not tracked."
                ));
    }

    private record ComponentDefinition(String key, String label) {
    }
}
