package ro.logistics.smart_logistics.maintenance.persistence;

import ro.logistics.smart_logistics.maintenance.domain.TrailerMaintenanceOperation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.time.OffsetDateTime;

public interface TrailerMaintenanceOperationRepository
        extends JpaRepository<TrailerMaintenanceOperation, Long> {
    List<TrailerMaintenanceOperation> findAllByTrailer_IdOrderByPerformedAtDescIdDesc(Long trailerId);

    List<TrailerMaintenanceOperation> findAllByPerformedAtGreaterThanEqualAndPerformedAtLessThan(
            OffsetDateTime from,
            OffsetDateTime to
    );
}
