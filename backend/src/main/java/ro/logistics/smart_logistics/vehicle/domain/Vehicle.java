package ro.logistics.smart_logistics.vehicle.domain;

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
        name = "vehicles",
        uniqueConstraints = @UniqueConstraint(name = "uk_vehicles_license_plate", columnNames = "license_plate")
)
public class Vehicle {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "license_plate", nullable = false, length = 20)
    private String licensePlate;

    @Enumerated(EnumType.STRING)
    @Column(name = "vehicle_type", nullable = false, length = 30)
    private VehicleType vehicleType;

    @Column(nullable = false, length = 100)
    private String make;

    @Column(nullable = false, length = 100)
    private String model;

    private Integer year;

    @Column(nullable = false)
    private Double maxWeightCapacityKg;

    @Column(nullable = false)
    private Double maxVolumeM3;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private VehicleStatus status = VehicleStatus.AVAILABLE;

    @Column(nullable = false)
    private Integer currentMileageKm;

    @Enumerated(EnumType.STRING)
    @Column(name = "transmission_type", nullable = false, length = 20)
    private TransmissionType transmissionType;

    @Column(name = "maintenance_auto_triggered", nullable = false)
    private boolean maintenanceAutoTriggered;

    protected Vehicle() {
    }

    public Vehicle(
            String licensePlate,
            VehicleType vehicleType,
            String make,
            String model,
            Integer year,
            Double maxWeightCapacityKg,
            Double maxVolumeM3,
            Integer currentMileageKm,
            TransmissionType transmissionType
    ) {
        this.licensePlate = licensePlate;
        this.vehicleType = vehicleType;
        this.make = make;
        this.model = model;
        this.year = year;
        this.maxWeightCapacityKg = maxWeightCapacityKg;
        this.maxVolumeM3 = maxVolumeM3;
        this.currentMileageKm = currentMileageKm;
        this.transmissionType = transmissionType;
    }

    public Vehicle(
            String licensePlate,
            VehicleType vehicleType,
            String make,
            String model,
            Integer year,
            Double maxWeightCapacityKg,
            Double maxVolumeM3,
            Integer currentMileageKm
    ) {
        this(
                licensePlate,
                vehicleType,
                make,
                model,
                year,
                maxWeightCapacityKg,
                maxVolumeM3,
                currentMileageKm,
                vehicleType == VehicleType.VAN ? TransmissionType.MANUAL : TransmissionType.AMT
        );
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
            VehicleType vehicleType,
            String make,
            String model,
            Integer year,
            Double maxWeightCapacityKg,
            Double maxVolumeM3,
            Integer currentMileageKm,
            TransmissionType transmissionType
    ) {
        this.licensePlate = licensePlate;
        this.vehicleType = vehicleType;
        this.make = make;
        this.model = model;
        this.year = year;
        this.maxWeightCapacityKg = maxWeightCapacityKg;
        this.maxVolumeM3 = maxVolumeM3;
        this.currentMileageKm = currentMileageKm;
        this.transmissionType = transmissionType;
    }

    public void updateDetails(
            String licensePlate,
            VehicleType vehicleType,
            String make,
            String model,
            Integer year,
            Double maxWeightCapacityKg,
            Double maxVolumeM3,
            Integer currentMileageKm
    ) {
        updateDetails(
                licensePlate,
                vehicleType,
                make,
                model,
                year,
                maxWeightCapacityKg,
                maxVolumeM3,
                currentMileageKm,
                vehicleType == VehicleType.VAN ? TransmissionType.MANUAL : TransmissionType.AMT
        );
    }

    public void updateDispatcherManagedStatus(VehicleStatus status) {
        if (status == VehicleStatus.IN_TRANSIT) {
            throw new IllegalArgumentException("IN_TRANSIT is managed by route assignment.");
        }
        this.status = status;
        this.maintenanceAutoTriggered = false;
    }

    public void markInTransit() {
        this.status = VehicleStatus.IN_TRANSIT;
    }

    public void markAvailable() {
        this.status = maintenanceAutoTriggered ? VehicleStatus.MAINTENANCE : VehicleStatus.AVAILABLE;
    }

    public void markMaintenance() {
        this.status = VehicleStatus.MAINTENANCE;
    }

    public void markAutomaticMaintenance() {
        this.maintenanceAutoTriggered = true;
        if (status != VehicleStatus.IN_TRANSIT) {
            this.status = VehicleStatus.MAINTENANCE;
        }
    }

    public void clearAutomaticMaintenance() {
        this.maintenanceAutoTriggered = false;
        if (status == VehicleStatus.MAINTENANCE) {
            this.status = VehicleStatus.AVAILABLE;
        }
    }

    public void setMaintenanceAutoTriggered(boolean maintenanceAutoTriggered) {
        this.maintenanceAutoTriggered = maintenanceAutoTriggered;
    }

    public Long getId() {
        return id;
    }

    public String getLicensePlate() {
        return licensePlate;
    }

    public VehicleType getVehicleType() {
        return vehicleType;
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

    public VehicleStatus getStatus() {
        return status;
    }

    public Integer getCurrentMileageKm() {
        return currentMileageKm;
    }

    public TransmissionType getTransmissionType() {
        return transmissionType;
    }

    public boolean isMaintenanceAutoTriggered() {
        return maintenanceAutoTriggered;
    }
}
