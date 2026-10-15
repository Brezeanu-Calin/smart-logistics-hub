package ro.logistics.smart_logistics.route.domain;

import ro.logistics.smart_logistics.goods.domain.Goods;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
        name = "route_stops",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_route_stop_order", columnNames = {"route_id", "stop_order"}),
                @UniqueConstraint(
                        name = "uk_route_goods_stop_type",
                        columnNames = {"route_id", "goods_id", "stop_type"}
                )
        }
)
public class RouteStop {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "route_id", nullable = false)
    private Route route;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "goods_id", nullable = false)
    private Goods goods;

    @Enumerated(EnumType.STRING)
    @Column(name = "stop_type", nullable = false, length = 12)
    private RouteStopType stopType;

    @Column(name = "stop_order", nullable = false)
    private Integer stopOrder;

    @Column(nullable = false, length = 500)
    private String city;

    @Column
    private Double latitude;

    @Column
    private Double longitude;

    protected RouteStop() {
    }

    public RouteStop(Goods goods, RouteStopType stopType, Integer stopOrder) {
        this(goods, stopType, stopOrder, null, null);
    }

    public RouteStop(
            Goods goods,
            RouteStopType stopType,
            Integer stopOrder,
            Double latitude,
            Double longitude
    ) {
        this.goods = goods;
        this.stopType = stopType;
        this.stopOrder = stopOrder;
        this.latitude = latitude;
        this.longitude = longitude;
        this.city = stopType == RouteStopType.PICKUP
                ? goods.getPickupCity()
                : goods.getDeliveryCity();
    }

    void setRoute(Route route) {
        this.route = route;
    }

    public Long getId() {
        return id;
    }

    public Goods getGoods() {
        return goods;
    }

    public RouteStopType getStopType() {
        return stopType;
    }

    public Integer getStopOrder() {
        return stopOrder;
    }

    public String getCity() {
        return city;
    }

    public Double getLatitude() {
        return latitude;
    }

    public Double getLongitude() {
        return longitude;
    }
}
