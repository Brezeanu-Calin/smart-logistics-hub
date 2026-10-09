package ro.logistics.smart_logistics.driver.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

@Entity
@Table(
        name = "drivers",
        uniqueConstraints = @UniqueConstraint(name = "uk_drivers_email", columnNames = "email")
)
public class Driver {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 254)
    private String email;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "driver_license_categories",
            joinColumns = @JoinColumn(name = "driver_id"),
            uniqueConstraints = @UniqueConstraint(
                    name = "uk_driver_license_category",
                    columnNames = {"driver_id", "license_category"}
            )
    )
    @Column(name = "license_category", nullable = false, length = 2)
    @Enumerated(EnumType.STRING)
    private Set<LicenseCategory> licenseCategories = new HashSet<>();

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private DriverStatus status = DriverStatus.AVAILABLE;

    protected Driver() {
    }

    public Driver(String name, String email, Set<LicenseCategory> licenseCategories) {
        this.name = name;
        this.email = email;
        this.licenseCategories = new HashSet<>(licenseCategories);
    }

    @PrePersist
    @PreUpdate
    private void normalizeEmail() {
        if (email != null) {
            email = email.trim().toLowerCase(Locale.ROOT);
        }
    }

    public void updateDetails(String name, String email, Set<LicenseCategory> licenseCategories) {
        this.name = name;
        this.email = email;
        this.licenseCategories = new HashSet<>(licenseCategories);
    }

    public void updateDispatcherManagedStatus(DriverStatus status) {
        if (status == DriverStatus.IN_TRANSIT) {
            throw new IllegalArgumentException("IN_TRANSIT is managed by route assignment.");
        }
        this.status = status;
    }

    public void markInTransit() {
        this.status = DriverStatus.IN_TRANSIT;
    }

    public void markAvailable() {
        this.status = DriverStatus.AVAILABLE;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    public Set<LicenseCategory> getLicenseCategories() {
        return Set.copyOf(licenseCategories);
    }

    public DriverStatus getStatus() {
        return status;
    }
}
