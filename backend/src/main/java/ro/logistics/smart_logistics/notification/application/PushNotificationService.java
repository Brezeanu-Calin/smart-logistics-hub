package ro.logistics.smart_logistics.notification.application;

import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import org.apache.http.HttpResponse;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.jose4j.lang.JoseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import ro.logistics.smart_logistics.notification.domain.PushSubscription;
import ro.logistics.smart_logistics.notification.dto.PushConfigResponse;
import ro.logistics.smart_logistics.notification.dto.PushSubscriptionRequest;
import ro.logistics.smart_logistics.notification.persistence.PushSubscriptionRepository;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.Security;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;

@Service
public class PushNotificationService {
    private static final Logger logger = LoggerFactory.getLogger(PushNotificationService.class);
    private static final Set<String> ALLOWED_PUSH_HOSTS = Set.of(
            "fcm.googleapis.com",
            "push.apple.com",
            "push.services.mozilla.com"
    );

    private final PushSubscriptionRepository subscriptionRepository;
    private final ObjectMapper objectMapper;
    private final String publicKey;
    private final String privateKey;
    private final String subject;

    public PushNotificationService(
            PushSubscriptionRepository subscriptionRepository,
            ObjectMapper objectMapper,
            @Value("${notifications.vapid.public-key:}") String publicKey,
            @Value("${notifications.vapid.private-key:}") String privateKey,
            @Value("${notifications.vapid.subject:mailto:admin@example.com}") String subject
    ) {
        this.subscriptionRepository = subscriptionRepository;
        this.objectMapper = objectMapper;
        this.publicKey = publicKey.trim();
        this.privateKey = privateKey.trim();
        this.subject = subject.trim();
        if (isConfigured()) {
            Security.addProvider(new BouncyCastleProvider());
            try {
                new PushService(this.publicKey, this.privateKey, this.subject);
            } catch (GeneralSecurityException exception) {
                throw new IllegalStateException("The configured VAPID key pair is invalid.", exception);
            }
        } else if (!this.publicKey.isEmpty() || !this.privateKey.isEmpty()) {
            throw new IllegalStateException("Both VAPID_PUBLIC_KEY and VAPID_PRIVATE_KEY must be configured.");
        }
    }

    public PushConfigResponse configuration() {
        return new PushConfigResponse(isConfigured(), isConfigured() ? publicKey : "");
    }

    public void subscribe(PushSubscriptionRequest request) {
        requireConfigured();
        validateSubscription(request);
        String hash = endpointHash(request.endpoint());
        PushSubscription subscription = subscriptionRepository.findByEndpointHash(hash)
                .map(existing -> {
                    existing.updateKeys(request.keys().p256dh(), request.keys().auth());
                    return existing;
                })
                .orElseGet(() -> new PushSubscription(
                        hash,
                        request.endpoint(),
                        request.keys().p256dh(),
                        request.keys().auth()
                ));
        subscriptionRepository.save(subscription);
    }

    public void unsubscribe(PushSubscriptionRequest request) {
        validateSubscription(request);
        subscriptionRepository.deleteByEndpointHash(endpointHash(request.endpoint()));
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void send(OperationalNotification notification) {
        if (!isConfigured()) {
            return;
        }
        List<PushSubscription> subscriptions = subscriptionRepository.findAll();
        if (subscriptions.isEmpty()) {
            return;
        }
        String payload;
        try {
            payload = objectMapper.writeValueAsString(notification);
        } catch (JacksonException exception) {
            logger.error("Could not encode an operational push notification.", exception);
            return;
        }

        for (PushSubscription subscription : subscriptions) {
            try {
                Notification push = new Notification(
                        subscription.getEndpoint(),
                        subscription.getP256dhKey(),
                        subscription.getAuthKey(),
                        payload.getBytes(StandardCharsets.UTF_8),
                        300
                );
                HttpResponse response = new PushService(publicKey, privateKey, subject).send(push);
                int statusCode = response.getStatusLine().getStatusCode();
                if (statusCode == 404 || statusCode == 410) {
                    subscriptionRepository.deleteByEndpointHash(subscription.getEndpointHash());
                } else if (statusCode < 200 || statusCode >= 300) {
                    logger.warn(
                            "Push provider rejected notification for subscription {} with HTTP {}.",
                            subscription.getEndpointHash(),
                            statusCode
                    );
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                logger.warn("Push delivery was interrupted for subscription {}.", subscription.getEndpointHash());
                return;
            } catch (GeneralSecurityException | IOException | JoseException | ExecutionException exception) {
                logger.error("Push delivery failed for subscription {}.", subscription.getEndpointHash(), exception);
            }
        }
    }

    private boolean isConfigured() {
        return !publicKey.isEmpty() && !privateKey.isEmpty();
    }

    private void requireConfigured() {
        if (!isConfigured()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Push notifications are not configured.");
        }
    }

    private static void validateSubscription(PushSubscriptionRequest request) {
        try {
            URI endpoint = URI.create(request.endpoint());
            String host = endpoint.getHost();
            if (!"https".equalsIgnoreCase(endpoint.getScheme())
                    || host == null
                    || endpoint.getPort() != -1 && endpoint.getPort() != 443
                    || endpoint.getUserInfo() != null
                    || !isAllowedPushHost(host)) {
                throw new InvalidPushSubscriptionException("Push endpoint must use a supported HTTPS push service.");
            }
            byte[] publicKeyBytes = Base64.getUrlDecoder().decode(request.keys().p256dh());
            byte[] authBytes = Base64.getUrlDecoder().decode(request.keys().auth());
            if (publicKeyBytes.length != 65 || authBytes.length != 16) {
                throw new InvalidPushSubscriptionException("Push subscription keys have an invalid length.");
            }
        } catch (IllegalArgumentException exception) {
            throw new InvalidPushSubscriptionException("Push subscription endpoint or keys are malformed.", exception);
        }
    }

    private static boolean isAllowedPushHost(String host) {
        String normalized = host.toLowerCase();
        return ALLOWED_PUSH_HOSTS.contains(normalized)
                || normalized.endsWith(".push.apple.com")
                || normalized.endsWith(".push.services.mozilla.com")
                || normalized.endsWith(".notify.windows.com");
    }

    private static String endpointHash(String endpoint) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(endpoint.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }
}
