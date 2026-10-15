package ro.logistics.smart_logistics.route.persistence;

import ro.logistics.smart_logistics.route.domain.GeocodedCity;

import org.springframework.data.jpa.repository.JpaRepository;

public interface GeocodedCityRepository extends JpaRepository<GeocodedCity, String> {
}
