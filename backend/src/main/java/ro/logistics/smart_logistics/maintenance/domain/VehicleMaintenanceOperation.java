package ro.logistics.smart_logistics.maintenance.domain;

import ro.logistics.smart_logistics.vehicle.domain.Vehicle;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

@Entity
@Table(name = "vehicle_maintenance_operations")
public class VehicleMaintenanceOperation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vehicle_id", nullable = false)
    private Vehicle vehicle;

    @Column(name = "component_key", nullable = false, length = 64)
    private String componentKey;

    @Column(name = "performed_at", nullable = false, columnDefinition = "datetimeoffset")
    private OffsetDateTime performedAt;

    @Column(name = "odometer_km", nullable = false)
    private Integer odometerKm;

    protected VehicleMaintenanceOperation() {
    }

    public VehicleMaintenanceOperation(
            Vehicle vehicle,
            String componentKey,
            OffsetDateTime performedAt,
            Integer odometerKm
    ) {
        this.vehicle = vehicle;
        this.componentKey = componentKey;
        this.performedAt = performedAt;
        this.odometerKm = odometerKm;
    }

    public Long getId() {
        return id;
    }

    public String getComponentKey() {
        return componentKey;
    }

    public OffsetDateTime getPerformedAt() {
        return performedAt;
    }

    public Integer getOdometerKm() {
        return odometerKm;
    }
}
