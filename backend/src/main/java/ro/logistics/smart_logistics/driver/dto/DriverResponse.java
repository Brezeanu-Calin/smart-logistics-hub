package ro.logistics.smart_logistics.driver.dto;

import ro.logistics.smart_logistics.driver.domain.DriverStatus;
import ro.logistics.smart_logistics.driver.domain.LicenseCategory;

import java.util.Set;

public record DriverResponse(
        Long id,
        String name,
        String email,
        Set<LicenseCategory> licenseCategories,
        DriverStatus status
) {
}
