package ro.logistics.smart_logistics.notification.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record MobilePushSubscriptionRequest(
        @NotBlank @Size(max = 256) String token
) {
}
