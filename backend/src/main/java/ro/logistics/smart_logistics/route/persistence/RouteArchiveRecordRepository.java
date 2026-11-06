package ro.logistics.smart_logistics.route.persistence;

import ro.logistics.smart_logistics.route.domain.RouteArchiveRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RouteArchiveRecordRepository extends JpaRepository<RouteArchiveRecord, Long> {
    Optional<RouteArchiveRecord> findByRouteId(Long routeId);
}
