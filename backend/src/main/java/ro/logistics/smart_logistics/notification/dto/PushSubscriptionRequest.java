package ro.logistics.smart_logistics.notification.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record PushSubscriptionRequest(
        @NotBlank @Size(max = 2048) String endpoint,
        @NotNull @Valid PushSubscriptionKeysRequest keys
) {
}
