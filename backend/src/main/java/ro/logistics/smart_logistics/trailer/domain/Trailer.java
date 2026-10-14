package ro.logistics.smart_logistics.trailer.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.util.Locale;

@Entity
@Table(
        name = "trailers",
        uniqueConstraints = @UniqueConstraint(name = "uk_trailers_license_plate", columnNames = "license_plate")
)
public class Trailer {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "license_plate", nullable = false, length = 20)
    private String licensePlate;

    @Enumerated(EnumType.STRING)
    @Column(name = "trailer_type", nullable = false, length = 32)
    private TrailerType trailerType;

    @Column(nullable = false, length = 100)
    private String make;

    @Column(nullable = false, length = 100)
    private String model;

    @Column(nullable = false)
    private Integer year;

    @Column(name = "max_weight_capacity_kg", nullable = false)
    private Double maxWeightCapacityKg;

    @Column(name = "max_volume_m3", nullable = false)
    private Double maxVolumeM3;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TrailerStatus status = TrailerStatus.AVAILABLE;

    protected Trailer() {
    }

    public Trailer(
            String licensePlate,
            TrailerType trailerType,
            String make,
            String model,
            Integer year,
            Double maxWeightCapacityKg,
            Double maxVolumeM3
    ) {
        this.licensePlate = licensePlate;
        this.trailerType = trailerType;
        this.make = make;
        this.model = model;
        this.year = year;
        this.maxWeightCapacityKg = maxWeightCapacityKg;
        this.maxVolumeM3 = maxVolumeM3;
    }

    @PrePersist
    @PreUpdate
    private void normalizeLicensePlate() {
        if (licensePlate != null) {
            licensePlate = licensePlate.trim().toUpperCase(Locale.ROOT);
        }
    }

    public void updateDetails(
            String licensePlate,
            TrailerType trailerType,
            String make,
            String model,
            Integer year,
            Double maxWeightCapacityKg,
            Double maxVolumeM3
    ) {
        this.licensePlate = licensePlate;
        this.trailerType = trailerType;
        this.make = make;
        this.model = model;
        this.year = year;
        this.maxWeightCapacityKg = maxWeightCapacityKg;
        this.maxVolumeM3 = maxVolumeM3;
    }

    public void updateDispatcherManagedStatus(TrailerStatus status) {
        if (status == TrailerStatus.IN_TRANSIT) {
            throw new IllegalArgumentException("IN_TRANSIT is managed by route assignment.");
        }
        this.status = status;
    }

    public void markInTransit() {
        this.status = TrailerStatus.IN_TRANSIT;
    }

    public void markAvailable() {
        this.status = TrailerStatus.AVAILABLE;
    }

    public Long getId() {
        return id;
    }

    public String getLicensePlate() {
        return licensePlate;
    }

    public TrailerType getTrailerType() {
        return trailerType;
    }

    public String getMake() {
        return make;
    }

    public String getModel() {
        return model;
    }

    public Integer getYear() {
        return year;
    }

    public Double getMaxWeightCapacityKg() {
        return maxWeightCapacityKg;
    }

    public Double getMaxVolumeM3() {
        return maxVolumeM3;
    }

    public TrailerStatus getStatus() {
        return status;
    }
}
