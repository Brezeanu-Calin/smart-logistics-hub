package ro.logistics.smart_logistics.notification.persistence;

import ro.logistics.smart_logistics.notification.domain.MobilePushSubscription;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MobilePushSubscriptionRepository extends JpaRepository<MobilePushSubscription, Long> {
    Optional<MobilePushSubscription> findByExpoPushToken(String expoPushToken);

    void deleteByExpoPushToken(String expoPushToken);
}
