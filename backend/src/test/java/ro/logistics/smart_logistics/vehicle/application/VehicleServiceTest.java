package ro.logistics.smart_logistics.vehicle.application;

import ro.logistics.smart_logistics.vehicle.domain.Vehicle;
import ro.logistics.smart_logistics.vehicle.domain.VehicleStatus;
import ro.logistics.smart_logistics.vehicle.domain.VehicleType;
import ro.logistics.smart_logistics.vehicle.dto.VehicleRequest;
import ro.logistics.smart_logistics.vehicle.persistence.VehicleRepository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VehicleServiceTest {
    @Mock
    private VehicleRepository vehicleRepository;

    @InjectMocks
    private VehicleService vehicleService;

    @Test
    void createNormalizesLicensePlateAndDefaultsStatusToAvailable() {
        when(vehicleRepository.existsByLicensePlateIgnoreCase("B 123 ABC")).thenReturn(false);
        when(vehicleRepository.saveAndFlush(any(Vehicle.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var response = vehicleService.create(request("  b 123 abc  "));

        assertEquals("B 123 ABC", response.licensePlate());
        assertEquals(VehicleType.TRUCK, response.vehicleType());
        assertEquals(VehicleStatus.AVAILABLE, response.status());
    }

    @Test
    void dispatcherCannotSetInTransitManually() {
        Vehicle vehicle = vehicle();
        when(vehicleRepository.findById(12L)).thenReturn(Optional.of(vehicle));

        assertThrows(
                InvalidVehicleStatusException.class,
                () -> vehicleService.updateDispatcherManagedStatus(12L, VehicleStatus.IN_TRANSIT)
        );

        verify(vehicleRepository, never()).save(any(Vehicle.class));
    }

    @Test
    void dispatcherCannotOverrideRouteOwnedInTransitStatus() {
        Vehicle vehicle = vehicle();
        vehicle.markInTransit();
        when(vehicleRepository.findById(12L)).thenReturn(Optional.of(vehicle));

        assertThrows(
                InvalidVehicleStatusException.class,
                () -> vehicleService.updateDispatcherManagedStatus(12L, VehicleStatus.MAINTENANCE)
        );

        verify(vehicleRepository, never()).save(any(Vehicle.class));
    }

    @Test
    void createRejectsDuplicateLicensePlateIgnoringCase() {
        when(vehicleRepository.existsByLicensePlateIgnoreCase("B 123 ABC")).thenReturn(true);

        assertThrows(
                DuplicateVehicleLicensePlateException.class,
                () -> vehicleService.create(request("b 123 abc"))
        );

        verify(vehicleRepository, never()).saveAndFlush(any(Vehicle.class));
    }

    private static VehicleRequest request(String licensePlate) {
        return new VehicleRequest(
                licensePlate,
                VehicleType.TRUCK,
                "Volvo",
                "FH",
                2022,
                18000.0,
                80.0,
                120000
        );
    }

    private static Vehicle vehicle() {
        return new Vehicle("B 123 ABC", VehicleType.TRUCK, "Volvo", "FH", 2022, 18000.0, 80.0, 120000);
    }
}
