package ro.logistics.smart_logistics.assistant.dto;

import tools.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record AssistantChatRequest(
        @NotEmpty @Size(max = 24) List<@Valid AssistantMessage> messages,
        @NotNull JsonNode context
) {
}
