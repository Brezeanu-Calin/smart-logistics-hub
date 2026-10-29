package ro.logistics.smart_logistics.notification.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Entity
@Table(name = "push_subscriptions")
public class PushSubscription {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "endpoint_hash", nullable = false, unique = true, length = 64, columnDefinition = "char(64)")
    private String endpointHash;

    @Column(nullable = false, length = 2048)
    private String endpoint;

    @Column(name = "p256dh_key", nullable = false, length = 256)
    private String p256dhKey;

    @Column(name = "auth_key", nullable = false, length = 128)
    private String authKey;

    @Column(name = "created_at", nullable = false, columnDefinition = "datetimeoffset")
    private OffsetDateTime createdAt;

    protected PushSubscription() {
    }

    public PushSubscription(String endpointHash, String endpoint, String p256dhKey, String authKey) {
        this.endpointHash = endpointHash;
        this.endpoint = endpoint;
        this.p256dhKey = p256dhKey;
        this.authKey = authKey;
        this.createdAt = OffsetDateTime.now(ZoneOffset.UTC);
    }

    public void updateKeys(String p256dhKey, String authKey) {
        this.p256dhKey = p256dhKey;
        this.authKey = authKey;
    }

    public Long getId() {
        return id;
    }

    public String getEndpointHash() {
        return endpointHash;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public String getP256dhKey() {
        return p256dhKey;
    }

    public String getAuthKey() {
        return authKey;
    }
}
