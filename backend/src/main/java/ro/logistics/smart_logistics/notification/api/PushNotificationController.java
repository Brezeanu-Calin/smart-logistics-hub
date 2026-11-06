package ro.logistics.smart_logistics.notification.api;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import ro.logistics.smart_logistics.notification.application.PushNotificationService;
import ro.logistics.smart_logistics.notification.dto.PushConfigResponse;
import ro.logistics.smart_logistics.notification.dto.PushSubscriptionRequest;
import ro.logistics.smart_logistics.notification.dto.MobilePushSubscriptionRequest;

@RestController
@RequestMapping("/api/notifications")
public class PushNotificationController {
    private final PushNotificationService notificationService;

    public PushNotificationController(PushNotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping("/config")
    public PushConfigResponse configuration() {
        return notificationService.configuration();
    }

    @PostMapping("/subscription")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void subscribe(@Valid @RequestBody PushSubscriptionRequest request) {
        notificationService.subscribe(request);
    }

    @DeleteMapping("/subscription")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unsubscribe(@Valid @RequestBody PushSubscriptionRequest request) {
        notificationService.unsubscribe(request);
    }

    @PostMapping("/mobile-subscription")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void subscribeMobile(@Valid @RequestBody MobilePushSubscriptionRequest request) {
        notificationService.subscribeMobile(request);
    }

    @DeleteMapping("/mobile-subscription")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unsubscribeMobile(@Valid @RequestBody MobilePushSubscriptionRequest request) {
        notificationService.unsubscribeMobile(request);
    }
}
