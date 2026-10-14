package ro.logistics.smart_logistics.route.persistence;

import ro.logistics.smart_logistics.route.domain.Route;
import ro.logistics.smart_logistics.route.domain.RouteStatus;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface RouteRepository extends JpaRepository<Route, Long> {
    @EntityGraph(attributePaths = {"driver", "vehicle", "trailer", "stops", "stops.goods"})
    List<Route> findAllByOrderByDepartureTimeAsc();

    @Override
    @EntityGraph(attributePaths = {"driver", "vehicle", "trailer", "stops", "stops.goods"})
    Optional<Route> findById(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Route r where r.id = :id")
    Optional<Route> findByIdForUpdate(@Param("id") Long id);

    @Query("""
            select case when count(r) > 0 then true else false end
            from Route r
            where r.status in :statuses
              and r.driver.id = :driverId
              and r.departureTime < :arrivalTime
              and r.estimatedArrivalTime > :departureTime
            """)
    boolean existsDriverScheduleOverlap(
            @Param("driverId") Long driverId,
            @Param("statuses") List<RouteStatus> statuses,
            @Param("departureTime") OffsetDateTime departureTime,
            @Param("arrivalTime") OffsetDateTime arrivalTime
    );

    @Query("""
            select case when count(r) > 0 then true else false end
            from Route r
            where r.status in :statuses
              and r.vehicle.id = :vehicleId
              and r.departureTime < :arrivalTime
              and r.estimatedArrivalTime > :departureTime
            """)
    boolean existsVehicleScheduleOverlap(
            @Param("vehicleId") Long vehicleId,
            @Param("statuses") List<RouteStatus> statuses,
            @Param("departureTime") OffsetDateTime departureTime,
            @Param("arrivalTime") OffsetDateTime arrivalTime
    );

    @Query("""
            select case when count(r) > 0 then true else false end
            from Route r
            where r.status in :statuses
              and r.trailer.id = :trailerId
              and r.departureTime < :arrivalTime
              and r.estimatedArrivalTime > :departureTime
            """)
    boolean existsTrailerScheduleOverlap(
            @Param("trailerId") Long trailerId,
            @Param("statuses") List<RouteStatus> statuses,
            @Param("departureTime") OffsetDateTime departureTime,
            @Param("arrivalTime") OffsetDateTime arrivalTime
    );

    boolean existsByDriver_IdAndStatusAndIdNot(Long driverId, RouteStatus status, Long id);

    boolean existsByVehicle_IdAndStatusAndIdNot(Long vehicleId, RouteStatus status, Long id);

    boolean existsByTrailer_IdAndStatusAndIdNot(Long trailerId, RouteStatus status, Long id);

    boolean existsByTrailer_IdAndStatusIn(Long trailerId, List<RouteStatus> statuses);

    boolean existsByDriver_IdAndStatusIn(Long driverId, List<RouteStatus> statuses);

    boolean existsByVehicle_IdAndStatusIn(Long vehicleId, List<RouteStatus> statuses);

    boolean existsByStops_Goods_Id(Long goodsId);
}
