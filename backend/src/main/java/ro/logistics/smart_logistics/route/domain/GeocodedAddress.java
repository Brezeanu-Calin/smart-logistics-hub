package ro.logistics.smart_logistics.route.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "geocoded_addresses")
public class GeocodedAddress {
    @Id
    @Column(name = "address_hash", nullable = false, length = 64)
    private String addressHash;

    @Column(name = "normalized_address", nullable = false, length = 1000)
    private String normalizedAddress;

    @Column(nullable = false)
    private Double latitude;

    @Column(nullable = false)
    private Double longitude;

    protected GeocodedAddress() {
    }

    public GeocodedAddress(String addressHash, String normalizedAddress, Double latitude, Double longitude) {
        this.addressHash = addressHash;
        this.normalizedAddress = normalizedAddress;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public String getNormalizedAddress() {
        return normalizedAddress;
    }

    public Double getLatitude() {
        return latitude;
    }

    public Double getLongitude() {
        return longitude;
    }
}
