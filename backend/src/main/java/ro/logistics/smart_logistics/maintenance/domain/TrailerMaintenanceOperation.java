package ro.logistics.smart_logistics.maintenance.domain;

import ro.logistics.smart_logistics.trailer.domain.Trailer;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "trailer_maintenance_operations")
public class TrailerMaintenanceOperation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "trailer_id", nullable = false)
    private Trailer trailer;

    @Column(name = "component_key", nullable = false, length = 64)
    private String componentKey;

    @Column(name = "performed_at", nullable = false, columnDefinition = "datetimeoffset")
    private OffsetDateTime performedAt;

    @Column(name = "cost_eur", precision = 12, scale = 2)
    private BigDecimal costEur;

    protected TrailerMaintenanceOperation() {
    }

    public TrailerMaintenanceOperation(
            Trailer trailer,
            String componentKey,
            OffsetDateTime performedAt,
            BigDecimal costEur
    ) {
        this.trailer = trailer;
        this.componentKey = componentKey;
        this.performedAt = performedAt;
        this.costEur = costEur;
    }

    public String getComponentKey() {
        return componentKey;
    }

    public OffsetDateTime getPerformedAt() {
        return performedAt;
    }

    public BigDecimal getCostEur() {
        return costEur;
    }
}
