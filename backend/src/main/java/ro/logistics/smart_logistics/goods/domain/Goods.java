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

    @Column(name = "volume_m3", nullable = false)
    private Double volumeM3;

    @Column(name = "pickup_address", nullable = false, length = 500)
    private String pickupAddress;

    @Column(name = "delivery_address", nullable = false, length = 500)
    private String deliveryAddress;

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
            Double volumeM3,
            String pickupAddress,
            String deliveryAddress
    ) {
        this.trackingCode = trackingCode;
        updateDetails(clientName, goodsType, description, weightKg, volumeM3, pickupAddress, deliveryAddress);
    }

    public void updateDetails(
            String clientName,
            GoodsType goodsType,
            String description,
            Double weightKg,
            Double volumeM3,
            String pickupAddress,
            String deliveryAddress
    ) {
        this.clientName = clientName;
        this.goodsType = goodsType;
        this.description = description;
        this.weightKg = weightKg;
        this.volumeM3 = volumeM3;
        this.pickupAddress = pickupAddress;
        this.deliveryAddress = deliveryAddress;
    }

    public void updateStatus(GoodsStatus status) {
        GoodsStatus expectedNextStatus = switch (this.status) {
            case UNASSIGNED -> GoodsStatus.ASSIGNED;
            case ASSIGNED -> GoodsStatus.IN_TRANSIT;
            case IN_TRANSIT -> GoodsStatus.DELIVERED;
            case DELIVERED -> null;
        };
        if (status == null || status != expectedNextStatus) {
            throw new IllegalArgumentException(
                    "Goods status must advance one step through UNASSIGNED, ASSIGNED, IN_TRANSIT, DELIVERED."
            );
        }
        this.status = status;
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

    public Double getVolumeM3() {
        return volumeM3;
    }

    public String getPickupAddress() {
        return pickupAddress;
    }

    public String getDeliveryAddress() {
        return deliveryAddress;
    }

    public GoodsStatus getStatus() {
        return status;
    }
}
