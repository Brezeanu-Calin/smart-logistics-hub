package ro.logistics.smart_logistics.notification.persistence;

import ro.logistics.smart_logistics.notification.domain.PushSubscription;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PushSubscriptionRepository extends JpaRepository<PushSubscription, Long> {
    Optional<PushSubscription> findByEndpointHash(String endpointHash);

    void deleteByEndpointHash(String endpointHash);
}
