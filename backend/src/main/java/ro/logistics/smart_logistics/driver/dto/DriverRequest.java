package ro.logistics.smart_logistics.driver.dto;

import ro.logistics.smart_logistics.driver.domain.LicenseCategory;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.Set;

public record DriverRequest(
        @NotBlank @Size(max = 100) String name,
        @NotBlank @Email @Size(max = 254) String email,
        @NotEmpty Set<LicenseCategory> licenseCategories
) {
}
