package ro.logistics.smart_logistics.driver.application;

import ro.logistics.smart_logistics.driver.domain.Driver;
import ro.logistics.smart_logistics.driver.domain.DriverStatus;
import ro.logistics.smart_logistics.driver.dto.DriverRequest;
import ro.logistics.smart_logistics.driver.dto.DriverResponse;
import ro.logistics.smart_logistics.driver.persistence.DriverRepository;
import ro.logistics.smart_logistics.route.domain.RouteStatus;
import ro.logistics.smart_logistics.route.persistence.RouteRepository;

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
public class DriverService {
    private final DriverRepository driverRepository;
    private final RouteRepository routeRepository;

    public DriverService(DriverRepository driverRepository, RouteRepository routeRepository) {
        this.driverRepository = driverRepository;
        this.routeRepository = routeRepository;
    }

    @Transactional(readOnly = true)
    public List<DriverResponse> findAll() {
        return driverRepository.findAllByArchivedFalseOrderByNameAsc().stream()
                .map(DriverService::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<DriverResponse> findArchived() {
        return driverRepository.findAllByArchivedTrueOrderByNameAsc().stream()
                .map(DriverService::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public DriverResponse findById(@Positive Long id) {
        return toResponse(findDriver(id));
    }

    public DriverResponse create(@Valid DriverRequest request) {
        String email = normalizeEmail(request.email());
        ensureEmailAvailable(email, null);
        Driver driver = new Driver(request.name().trim(), email, request.licenseCategories());
        return save(driver, email);
    }

    public DriverResponse update(@Positive Long id, @Valid DriverRequest request) {
        Driver driver = lockDriver(id);
        ensureNotArchived(driver);
        ensureNotScheduled(driver);
        String email = normalizeEmail(request.email());
        ensureEmailAvailable(email, id);
        driver.updateDetails(request.name().trim(), email, request.licenseCategories());
        return save(driver, email);
    }

    public DriverResponse updateDispatcherManagedStatus(@Positive Long id, @NotNull DriverStatus status) {
        Driver driver = lockDriver(id);
        ensureNotArchived(driver);
        if (driver.getStatus() == DriverStatus.IN_TRANSIT || driver.getStatus() == DriverStatus.RETURNING) {
            throw new InvalidDriverStatusException(
                    "A driver's status cannot be changed manually while they are IN_TRANSIT or RETURNING."
            );
        }
        ensureNotScheduled(driver);
        try {
            driver.updateDispatcherManagedStatus(status);
        } catch (IllegalArgumentException exception) {
            throw new InvalidDriverStatusException(exception.getMessage());
        }
        return toResponse(driverRepository.save(driver));
    }

    public void delete(@Positive Long id) {
        Driver driver = lockDriver(id);
        ensureNotScheduled(driver);
        if (driver.isArchived()) {
            return;
        }
        driver.archive();
        driverRepository.saveAndFlush(driver);
    }

    private DriverResponse save(Driver driver, String email) {
        try {
            return toResponse(driverRepository.saveAndFlush(driver));
        } catch (DataIntegrityViolationException exception) {
            throw new DuplicateDriverEmailException(email);
        }
    }

    private void ensureEmailAvailable(String email, Long id) {
        boolean exists = id == null
                ? driverRepository.existsByEmailIgnoreCase(email)
                : driverRepository.existsByEmailIgnoreCaseAndIdNot(email, id);
        if (exists) {
            throw new DuplicateDriverEmailException(email);
        }
    }

    private static void ensureNotArchived(Driver driver) {
        if (driver.isArchived()) {
            throw new InvalidDriverStatusException("Archived drivers cannot be changed.");
        }
    }

    private void ensureNotScheduled(Driver driver) {
        if (routeRepository.existsByDriver_IdAndStatusIn(
                driver.getId(), List.of(RouteStatus.PLANNED, RouteStatus.ACTIVE))
                || driver.getStatus() == DriverStatus.IN_TRANSIT
                || driver.getStatus() == DriverStatus.RETURNING
                || routeRepository.existsByDriver_IdAndStatusInAndEstimatedReturnTimeAfter(
                driver.getId(), List.of(RouteStatus.COMPLETED, RouteStatus.CANCELLED), OffsetDateTime.now())) {
            throw new DriverScheduledException(driver.getId());
        }
    }

    private Driver findDriver(Long id) {
        return driverRepository.findById(id).orElseThrow(() -> new DriverNotFoundException(id));
    }

    private Driver lockDriver(Long id) {
        return driverRepository.findByIdForUpdate(id).orElseThrow(() -> new DriverNotFoundException(id));
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static DriverResponse toResponse(Driver driver) {
        return new DriverResponse(
                driver.getId(),
                driver.getName(),
                driver.getEmail(),
                driver.getLicenseCategories(),
                driver.getStatus(),
                driver.isArchived()
        );
    }
}
