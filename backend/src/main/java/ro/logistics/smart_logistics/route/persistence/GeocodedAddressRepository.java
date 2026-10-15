package ro.logistics.smart_logistics.route.persistence;

import ro.logistics.smart_logistics.route.domain.GeocodedAddress;

import org.springframework.data.jpa.repository.JpaRepository;

public interface GeocodedAddressRepository extends JpaRepository<GeocodedAddress, String> {
}
