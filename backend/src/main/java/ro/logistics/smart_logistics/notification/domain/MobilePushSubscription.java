package ro.logistics.smart_logistics.notification.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "mobile_push_subscriptions")
public class MobilePushSubscription {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "expo_push_token", nullable = false, unique = true, length = 256)
    private String expoPushToken;

    protected MobilePushSubscription() {
    }

    public MobilePushSubscription(String expoPushToken) {
        this.expoPushToken = expoPushToken;
    }

    public String getExpoPushToken() {
        return expoPushToken;
    }
}
