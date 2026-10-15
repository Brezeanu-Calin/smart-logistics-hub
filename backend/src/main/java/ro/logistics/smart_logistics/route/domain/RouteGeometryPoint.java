package ro.logistics.smart_logistics.route.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
        name = "route_geometry_points",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_route_geometry_order",
                columnNames = {"route_id", "point_order"}
        )
)
public class RouteGeometryPoint {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "route_id", nullable = false)
    private Route route;

    @Column(name = "point_order", nullable = false)
    private Integer pointOrder;

    @Column(nullable = false)
    private Double latitude;

    @Column(nullable = false)
    private Double longitude;

    protected RouteGeometryPoint() {
    }

    public RouteGeometryPoint(Integer pointOrder, Double latitude, Double longitude) {
        this.pointOrder = pointOrder;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    void setRoute(Route route) {
        this.route = route;
    }

    public Integer getPointOrder() {
        return pointOrder;
    }

    public Double getLatitude() {
        return latitude;
    }

    public Double getLongitude() {
        return longitude;
    }
}
