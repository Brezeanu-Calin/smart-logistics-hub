package ro.logistics.smart_logistics.notification.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PushSubscriptionKeysRequest(
        @NotBlank @Size(max = 256) String p256dh,
        @NotBlank @Size(max = 128) String auth
) {
}
