package ro.logistics.smart_logistics.assistant.dto;

import tools.jackson.databind.JsonNode;

public record AssistantAction(String type, String summary, Long id, JsonNode payload) {
}
