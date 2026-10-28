package ro.logistics.smart_logistics.goods.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;

@Entity
@Table(
        name = "goods",
        uniqueConstraints = @UniqueConstraint(name = "uk_goods_tracking_code", columnNames = "tracking_code")
)
public class Goods {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tracking_code", nullable = false, length = 13)
    private String trackingCode;

    @Column(name = "client_name", nullable = false, length = 150)
    private String clientName;

    @Enumerated(EnumType.STRING)
    @Column(name = "goods_type", nullable = false, length = 20)
    private GoodsType goodsType;

    @Column(length = 1000)
    private String description;

    @Column(name = "weight_kg", nullable = false)
    private Double weightKg;

    @Column(name = "revenue_eur", precision = 12, scale = 2)
    private BigDecimal revenueEur;

    @Column(name = "volume_m3", nullable = false)
    private Double volumeM3;

    @Column(name = "pickup_city", nullable = false, length = 500)
    private String pickupCity;

    @Column(name = "delivery_city", nullable = false, length = 500)
    private String deliveryCity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private GoodsStatus status = GoodsStatus.UNASSIGNED;

    protected Goods() {
    }

    public Goods(
            String trackingCode,
            String clientName,
            GoodsType goodsType,
            String description,
            Double weightKg,
            BigDecimal revenueEur,
            Double volumeM3,
            String pickupCity,
            String deliveryCity
    ) {
        this.trackingCode = trackingCode;
        updateDetails(clientName, goodsType, description, weightKg, revenueEur, volumeM3, pickupCity, deliveryCity);
    }

    public void updateDetails(
            String clientName,
            GoodsType goodsType,
            String description,
            Double weightKg,
            BigDecimal revenueEur,
            Double volumeM3,
            String pickupCity,
            String deliveryCity
    ) {
        this.clientName = clientName;
        this.goodsType = goodsType;
        this.description = description;
        this.weightKg = weightKg;
        this.revenueEur = revenueEur;
        this.volumeM3 = volumeM3;
        this.pickupCity = pickupCity;
        this.deliveryCity = deliveryCity;
    }

    public void assignToRoute() {
        requireStatus(GoodsStatus.UNASSIGNED);
        this.status = GoodsStatus.ASSIGNED;
    }

    public void startTransit() {
        requireStatus(GoodsStatus.ASSIGNED);
        this.status = GoodsStatus.IN_TRANSIT;
    }

    public void markDelivered() {
        requireStatus(GoodsStatus.IN_TRANSIT);
        this.status = GoodsStatus.DELIVERED;
    }

    public void releaseFromRoute() {
        if (status != GoodsStatus.ASSIGNED && status != GoodsStatus.IN_TRANSIT) {
            throw new IllegalStateException("Only goods assigned to an active route can be released.");
        }
        status = GoodsStatus.UNASSIGNED;
    }

    public void archive() {
        requireStatus(GoodsStatus.DELIVERED);
        status = GoodsStatus.ARCHIVED;
    }

    private void requireStatus(GoodsStatus requiredStatus) {
        if (status != requiredStatus) {
            throw new IllegalStateException("Goods must be " + requiredStatus + " for this route operation.");
        }
    }

    public Long getId() {
        return id;
    }

    public String getTrackingCode() {
        return trackingCode;
    }

    public String getClientName() {
        return clientName;
    }

    public GoodsType getGoodsType() {
        return goodsType;
    }

    public String getDescription() {
        return description;
    }

    public Double getWeightKg() {
        return weightKg;
    }

    public BigDecimal getRevenueEur() {
        return revenueEur;
    }

    public Double getVolumeM3() {
        return volumeM3;
    }

    public String getPickupCity() {
        return pickupCity;
    }

    public String getDeliveryCity() {
        return deliveryCity;
    }

    public GoodsStatus getStatus() {
        return status;
    }
}
