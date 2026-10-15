package ro.logistics.smart_logistics.route.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "geocoded_cities")
public class GeocodedCity {
    @Id
    @Column(name = "city_hash", nullable = false, length = 64)
    private String cityHash;

    @Column(name = "normalized_city", nullable = false, length = 1000)
    private String normalizedCity;

    @Column(nullable = false)
    private Double latitude;

    @Column(nullable = false)
    private Double longitude;

    protected GeocodedCity() {
    }

    public GeocodedCity(String cityHash, String normalizedCity, Double latitude, Double longitude) {
        this.cityHash = cityHash;
        this.normalizedCity = normalizedCity;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public String getNormalizedCity() {
        return normalizedCity;
    }

    public Double getLatitude() {
        return latitude;
    }

    public Double getLongitude() {
        return longitude;
    }
}
