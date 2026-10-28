package ro.logistics.smart_logistics.maintenance.application;

import ro.logistics.smart_logistics.maintenance.domain.VehicleMaintenanceOperation;
import ro.logistics.smart_logistics.maintenance.persistence.VehicleMaintenanceOperationRepository;
import ro.logistics.smart_logistics.route.persistence.RouteRepository;
import ro.logistics.smart_logistics.vehicle.domain.Vehicle;
import ro.logistics.smart_logistics.vehicle.domain.VehicleStatus;
import ro.logistics.smart_logistics.vehicle.domain.VehicleType;
import ro.logistics.smart_logistics.vehicle.persistence.VehicleRepository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VehicleMaintenanceServiceTest {
    @Mock
    private VehicleMaintenanceOperationRepository operationRepository;
    @Mock
    private VehicleRepository vehicleRepository;
    @Mock
    private RouteRepository routeRepository;

    @InjectMocks
    private VehicleMaintenanceService maintenanceService;

    @Test
    void lowerServiceIntervalAndLastOperationDetermineComponentHealth() throws Exception {
        Vehicle vehicle = vehicle();
        OffsetDateTime now = OffsetDateTime.parse("2026-10-17T10:00:00Z");
        List<VehicleMaintenanceOperation> history = healthyHistory(vehicle, now);
        history.removeIf(operation -> operation.getComponentKey().equals("engine-oil-filter"));
        history.add(new VehicleMaintenanceOperation(
                vehicle,
                "engine-oil-filter",
                now.minusMonths(11),
                vehicle.getCurrentMileageKm() - 14_000,
                java.math.BigDecimal.valueOf(120)
        ));
        when(operationRepository.findAllByVehicle_IdOrderByPerformedAtDescIdDesc(1L)).thenReturn(history);

        var engineOil = maintenanceService.componentsFor(vehicle).stream()
                .filter(component -> component.key().equals("engine-oil-filter"))
                .findFirst()
                .orElseThrow();

        assertEquals(15_000, engineOil.serviceIntervalKm());
        assertTrue(engineOil.healthPercent() <= 10);
        assertEquals(1_000, engineOil.distanceRemainingKm());
    }

    @Test
    void dueComponentAutomaticallyPlacesAvailableVehicleInMaintenance() throws Exception {
        Vehicle vehicle = vehicle();
        OffsetDateTime now = OffsetDateTime.parse("2026-10-17T10:00:00Z");
        List<VehicleMaintenanceOperation> history = healthyHistory(vehicle, now);
        history.removeIf(operation -> operation.getComponentKey().equals("engine-oil-filter"));
        history.add(new VehicleMaintenanceOperation(
                vehicle,
                "engine-oil-filter",
                now.minusMonths(11),
                vehicle.getCurrentMileageKm() - 14_000,
                java.math.BigDecimal.valueOf(120)
        ));
        when(operationRepository.findAllByVehicle_IdOrderByPerformedAtDescIdDesc(1L)).thenReturn(history);

        maintenanceService.updateAutomaticMaintenanceState(vehicle, now);

        assertEquals(VehicleStatus.MAINTENANCE, vehicle.getStatus());
        assertTrue(vehicle.isMaintenanceAutoTriggered());
    }

    @Test
    void completingMaintenanceRecordsAllHealthTrackedComponentsAndMakesVehicleAvailable() throws Exception {
        Vehicle vehicle = vehicle();
        vehicle.markMaintenance();

        maintenanceService.completeMaintenance(vehicle);

        int healthTrackedComponents = (int) VehicleMaintenanceCatalog.forVehicle(vehicle).stream()
                .filter(VehicleMaintenanceCatalog.Definition::hasHealthInterval)
                .count();
        verify(operationRepository).saveAll(argThat(operations -> {
            List<VehicleMaintenanceOperation> completedOperations =
                    StreamSupport.stream(operations.spliterator(), false).toList();
            return completedOperations.size() == healthTrackedComponents
                    && completedOperations.stream().allMatch(operation ->
                    operation.getOdometerKm().equals(vehicle.getCurrentMileageKm())
                            && operation.getCostEur() == null);
        }));
        assertEquals(VehicleStatus.AVAILABLE, vehicle.getStatus());
        assertTrue(!vehicle.isMaintenanceAutoTriggered());
    }

    @Test
    void manualAndAutomaticVansUseOnlyTheirMatchingTransmissionSchedule() throws Exception {
        Vehicle manualVan = vehicle();
        Vehicle automaticVan = new Vehicle(
                "VAN-2", VehicleType.VAN, "Make", "Model", 2022, 5000.0, 20.0, 100000,
                ro.logistics.smart_logistics.vehicle.domain.TransmissionType.AUTOMATIC
        );

        Set<String> manualKeys = VehicleMaintenanceCatalog.forVehicle(manualVan).stream()
                .map(VehicleMaintenanceCatalog.Definition::key)
                .collect(Collectors.toSet());
        Set<String> automaticKeys = VehicleMaintenanceCatalog.forVehicle(automaticVan).stream()
                .map(VehicleMaintenanceCatalog.Definition::key)
                .collect(Collectors.toSet());

        assertTrue(manualKeys.contains("transmission-manual"));
        assertTrue(!manualKeys.contains("transmission-automatic"));
        assertTrue(automaticKeys.contains("transmission-automatic"));
        assertTrue(!automaticKeys.contains("transmission-manual"));
    }

    private List<VehicleMaintenanceOperation> healthyHistory(Vehicle vehicle, OffsetDateTime now) {
        List<VehicleMaintenanceOperation> history = new ArrayList<>();
        for (VehicleMaintenanceCatalog.Definition definition : VehicleMaintenanceCatalog.forVehicle(vehicle)) {
            history.add(new VehicleMaintenanceOperation(
                    vehicle,
                    definition.key(),
                    now.minusDays(30),
                    vehicle.getCurrentMileageKm(),
                    java.math.BigDecimal.valueOf(120)
            ));
        }
        return history;
    }

    private static Vehicle vehicle() throws Exception {
        Vehicle vehicle = new Vehicle("VAN-1", VehicleType.VAN, "Make", "Model", 2022, 5000.0, 20.0, 100000);
        Field id = Vehicle.class.getDeclaredField("id");
        id.setAccessible(true);
        id.set(vehicle, 1L);
        return vehicle;
    }
}
