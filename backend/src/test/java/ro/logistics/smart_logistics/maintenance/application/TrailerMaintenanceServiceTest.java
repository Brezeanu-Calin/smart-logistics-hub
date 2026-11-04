package ro.logistics.smart_logistics.maintenance.application;

import ro.logistics.smart_logistics.maintenance.domain.TrailerMaintenanceOperation;
import ro.logistics.smart_logistics.maintenance.persistence.TrailerMaintenanceOperationRepository;
import ro.logistics.smart_logistics.notification.application.OperationalNotification;
import ro.logistics.smart_logistics.route.persistence.RouteRepository;
import ro.logistics.smart_logistics.trailer.domain.Trailer;
import ro.logistics.smart_logistics.trailer.domain.TrailerStatus;
import ro.logistics.smart_logistics.trailer.domain.TrailerType;
import ro.logistics.smart_logistics.trailer.persistence.TrailerRepository;
import ro.logistics.smart_logistics.vehicle.domain.VehicleType;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.lang.reflect.Field;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.StreamSupport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TrailerMaintenanceServiceTest {
    @Mock
    private TrailerMaintenanceOperationRepository operationRepository;
    @Mock
    private TrailerRepository trailerRepository;
    @Mock
    private RouteRepository routeRepository;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private TrailerMaintenanceService maintenanceService;

    @Test
    void trailerWithoutServiceHistoryIsAutomaticallyHeldForMaintenance() throws Exception {
        Trailer trailer = trailer();
        when(operationRepository.findAllByTrailer_IdOrderByPerformedAtDescIdDesc(1L)).thenReturn(List.of());

        maintenanceService.updateAutomaticMaintenanceState(trailer, OffsetDateTime.parse("2026-10-17T10:00:00Z"));

        assertEquals(TrailerStatus.MAINTENANCE, trailer.getStatus());
        assertTrue(trailer.isMaintenanceAutoTriggered());
        assertTrue(maintenanceService.componentsFor(trailer).stream()
                .allMatch(component -> component.healthPercent() == 0));
        verify(eventPublisher, times(1)).publishEvent(any(OperationalNotification.class));
    }

    @Test
    void completingMaintenanceResetsEveryTrailerComponent() throws Exception {
        Trailer trailer = trailer();
        trailer.markAutomaticMaintenance();

        maintenanceService.completeMaintenance(trailer);

        verify(operationRepository).saveAll(argThat(operations -> {
            List<TrailerMaintenanceOperation> completed =
                    StreamSupport.stream(operations.spliterator(), false).toList();
            return completed.size() == 4
                    && completed.stream().allMatch(operation -> operation.getCostEur() == null);
        }));
        assertEquals(TrailerStatus.AVAILABLE, trailer.getStatus());
        assertTrue(!trailer.isMaintenanceAutoTriggered());
    }

    private static Trailer trailer() throws Exception {
        Trailer trailer = new Trailer(
                "CJ-TR-1",
                TrailerType.BOX_TRAILER,
                VehicleType.VAN,
                "Make",
                "Model",
                2022,
                8000.0,
                28.0
        );
        Field id = Trailer.class.getDeclaredField("id");
        id.setAccessible(true);
        id.set(trailer, 1L);
        return trailer;
    }
}
