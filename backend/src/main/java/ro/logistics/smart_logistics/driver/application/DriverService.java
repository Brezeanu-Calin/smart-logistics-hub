package ro.logistics.smart_logistics.driver.application;

import ro.logistics.smart_logistics.driver.domain.Driver;
import ro.logistics.smart_logistics.driver.domain.DriverStatus;
import ro.logistics.smart_logistics.driver.dto.DriverRequest;
import ro.logistics.smart_logistics.driver.dto.DriverResponse;
import ro.logistics.smart_logistics.driver.persistence.DriverRepository;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

@Service
@Transactional
public class DriverService {
    private final DriverRepository driverRepository;

    public DriverService(DriverRepository driverRepository) {
        this.driverRepository = driverRepository;
    }

    @Transactional(readOnly = true)
    public List<DriverResponse> findAll() {
        return driverRepository.findAllByOrderByNameAsc().stream()
                .map(DriverService::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public DriverResponse findById(Long id) {
        return toResponse(findDriver(id));
    }

    public DriverResponse create(DriverRequest request) {
        String email = normalizeEmail(request.email());
        ensureEmailAvailable(email, null);
        Driver driver = new Driver(request.name().trim(), email, request.licenseCategories());
        return save(driver, email);
    }

    public DriverResponse update(Long id, DriverRequest request) {
        Driver driver = findDriver(id);
        String email = normalizeEmail(request.email());
        ensureEmailAvailable(email, id);
        driver.updateDetails(request.name().trim(), email, request.licenseCategories());
        return save(driver, email);
    }

    public DriverResponse updateDispatcherManagedStatus(Long id, DriverStatus status) {
        Driver driver = findDriver(id);
        if (driver.getStatus() == DriverStatus.IN_TRANSIT) {
            throw new InvalidDriverStatusException(
                    "A driver's status cannot be changed manually while they are IN_TRANSIT on a route."
            );
        }
        try {
            driver.updateDispatcherManagedStatus(status);
        } catch (IllegalArgumentException exception) {
            throw new InvalidDriverStatusException(exception.getMessage());
        }
        return toResponse(driverRepository.save(driver));
    }

    public void delete(Long id) {
        Driver driver = findDriver(id);
        driverRepository.delete(driver);
        try {
            driverRepository.flush();
        } catch (DataIntegrityViolationException exception) {
            throw new DriverInUseException(id);
        }
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

    private Driver findDriver(Long id) {
        return driverRepository.findById(id).orElseThrow(() -> new DriverNotFoundException(id));
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
                driver.getStatus()
        );
    }
}
