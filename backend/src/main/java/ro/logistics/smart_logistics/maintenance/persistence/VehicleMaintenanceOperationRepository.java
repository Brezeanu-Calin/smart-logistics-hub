package ro.logistics.smart_logistics.maintenance.persistence;

import ro.logistics.smart_logistics.maintenance.domain.VehicleMaintenanceOperation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.List;

public interface VehicleMaintenanceOperationRepository
        extends JpaRepository<VehicleMaintenanceOperation, Long> {
    List<VehicleMaintenanceOperation> findAllByVehicle_IdOrderByPerformedAtDescIdDesc(Long vehicleId);

    List<VehicleMaintenanceOperation> findAllByPerformedAtGreaterThanEqualAndPerformedAtLessThan(
            OffsetDateTime from,
            OffsetDateTime to
    );
}
