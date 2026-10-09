package ro.logistics.smart_logistics.vehicle.persistence;

import ro.logistics.smart_logistics.vehicle.domain.Vehicle;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface VehicleRepository extends JpaRepository<Vehicle, Long> {
    List<Vehicle> findAllByOrderByLicensePlateAsc();

    boolean existsByLicensePlateIgnoreCase(String licensePlate);

    boolean existsByLicensePlateIgnoreCaseAndIdNot(String licensePlate, Long id);
}
