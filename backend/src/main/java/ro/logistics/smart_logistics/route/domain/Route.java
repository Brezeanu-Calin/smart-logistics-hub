package ro.logistics.smart_logistics.route.domain;

import ro.logistics.smart_logistics.driver.domain.Driver;
import ro.logistics.smart_logistics.goods.domain.Goods;
import ro.logistics.smart_logistics.trailer.domain.Trailer;
import ro.logistics.smart_logistics.vehicle.domain.Vehicle;

import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "routes")
public class Route {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "driver_id", nullable = false)
    private Driver driver;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vehicle_id", nullable = false)
    private Vehicle vehicle;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trailer_id")
    private Trailer trailer;

    @Column(name = "departure_time", nullable = false, columnDefinition = "datetimeoffset")
    private OffsetDateTime departureTime;

    @Column(name = "estimated_arrival_time", nullable = false, columnDefinition = "datetimeoffset")
    private OffsetDateTime estimatedArrivalTime;

    @Column(name = "estimated_return_time", columnDefinition = "datetimeoffset")
    private OffsetDateTime estimatedReturnTime;

    @Column(name = "returned_at", columnDefinition = "datetimeoffset")
    private OffsetDateTime returnedAt;

    @Column(name = "distance_meters", nullable = false)
    private Long distanceMeters;

    @Column(name = "driving_duration_seconds", nullable = false)
    private Long drivingDurationSeconds;

    @Column(name = "return_duration_seconds", nullable = false)
    private Long returnDurationSeconds;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RouteStatus status = RouteStatus.PLANNED;

    @OneToMany(mappedBy = "route", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("stopOrder ASC")
    private List<RouteStop> stops = new ArrayList<>();

    protected Route() {
    }

    public Route(
            Driver driver,
            Vehicle vehicle,
            Trailer trailer,
            OffsetDateTime departureTime,
            OffsetDateTime estimatedArrivalTime,
            Long distanceMeters,
            Long drivingDurationSeconds,
            Long returnDurationSeconds
    ) {
        this.driver = driver;
        this.vehicle = vehicle;
        this.trailer = trailer;
        this.departureTime = departureTime;
        this.estimatedArrivalTime = estimatedArrivalTime;
        this.estimatedReturnTime = estimatedArrivalTime;
        this.distanceMeters = distanceMeters;
        this.drivingDurationSeconds = drivingDurationSeconds;
        this.returnDurationSeconds = returnDurationSeconds;
    }

    public void addStop(RouteStop stop) {
        stop.setRoute(this);
        stops.add(stop);
    }

    public void activate() {
        requireStatus(RouteStatus.PLANNED);
        status = RouteStatus.ACTIVE;
    }

    public void complete() {
        requireStatus(RouteStatus.ACTIVE);
        status = RouteStatus.COMPLETED;
    }

    public void setEstimatedReturnTime(OffsetDateTime estimatedReturnTime) {
        this.estimatedReturnTime = estimatedReturnTime;
    }

    public void clearEstimatedReturnTime() {
        this.estimatedReturnTime = null;
    }

    public void markReturned(OffsetDateTime returnedAt) {
        if (estimatedReturnTime == null || this.returnedAt != null) {
            throw new IllegalStateException("This route has no pending return.");
        }
        this.returnedAt = returnedAt;
    }

    public void cancel() {
        if (status != RouteStatus.PLANNED && status != RouteStatus.ACTIVE) {
            throw new IllegalStateException("Only PLANNED or ACTIVE routes can be cancelled.");
        }
        status = RouteStatus.CANCELLED;
    }

    private void requireStatus(RouteStatus requiredStatus) {
        if (status != requiredStatus) {
            throw new IllegalStateException("Route must be " + requiredStatus + " for this operation.");
        }
    }

    public Long getId() {
        return id;
    }

    public Driver getDriver() {
        return driver;
    }

    public Vehicle getVehicle() {
        return vehicle;
    }

    public Trailer getTrailer() {
        return trailer;
    }

    public OffsetDateTime getDepartureTime() {
        return departureTime;
    }

    public OffsetDateTime getEstimatedArrivalTime() {
        return estimatedArrivalTime;
    }

    public OffsetDateTime getEstimatedReturnTime() {
        return estimatedReturnTime;
    }

    public OffsetDateTime getReturnedAt() {
        return returnedAt;
    }

    public Long getDistanceMeters() {
        return distanceMeters;
    }

    public Long getDrivingDurationSeconds() {
        return drivingDurationSeconds;
    }

    public Long getReturnDurationSeconds() {
        return returnDurationSeconds;
    }

    public RouteStatus getStatus() {
        return status;
    }

    public List<RouteStop> getStops() {
        return List.copyOf(stops);
    }

    public List<Goods> getGoods() {
        return stops.stream().map(RouteStop::getGoods).distinct().toList();
    }
}
