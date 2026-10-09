package ro.logistics.smart_logistics.driver.application;

import ro.logistics.smart_logistics.driver.domain.Driver;
import ro.logistics.smart_logistics.driver.domain.DriverStatus;
import ro.logistics.smart_logistics.driver.domain.LicenseCategory;
import ro.logistics.smart_logistics.driver.dto.DriverRequest;
import ro.logistics.smart_logistics.driver.persistence.DriverRepository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DriverServiceTest {
    @Mock
    private DriverRepository driverRepository;

    @InjectMocks
    private DriverService driverService;

    @Test
    void createNormalizesEmailAndDefaultsStatusToAvailable() {
        when(driverRepository.existsByEmailIgnoreCase("driver@example.com")).thenReturn(false);
        when(driverRepository.saveAndFlush(any(Driver.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var response = driverService.create(new DriverRequest(
                "  Alex Driver  ",
                "  DRIVER@EXAMPLE.COM ",
                Set.of(LicenseCategory.B, LicenseCategory.BE)
        ));

        assertEquals("Alex Driver", response.name());
        assertEquals("driver@example.com", response.email());
        assertEquals(Set.of(LicenseCategory.B, LicenseCategory.BE), response.licenseCategories());
        assertEquals(DriverStatus.AVAILABLE, response.status());
    }

    @Test
    void dispatcherCannotSetInTransitManually() {
        Driver driver = new Driver("Alex Driver", "driver@example.com", Set.of(LicenseCategory.B));
        when(driverRepository.findById(7L)).thenReturn(Optional.of(driver));

        assertThrows(
                InvalidDriverStatusException.class,
                () -> driverService.updateDispatcherManagedStatus(7L, DriverStatus.IN_TRANSIT)
        );

        verify(driverRepository, never()).save(any(Driver.class));
    }

    @Test
    void dispatcherCannotOverrideRouteOwnedInTransitStatus() {
        Driver driver = new Driver("Alex Driver", "driver@example.com", Set.of(LicenseCategory.B));
        driver.markInTransit();
        when(driverRepository.findById(7L)).thenReturn(Optional.of(driver));

        assertThrows(
                InvalidDriverStatusException.class,
                () -> driverService.updateDispatcherManagedStatus(7L, DriverStatus.ON_LEAVE)
        );

        verify(driverRepository, never()).save(any(Driver.class));
    }
}
