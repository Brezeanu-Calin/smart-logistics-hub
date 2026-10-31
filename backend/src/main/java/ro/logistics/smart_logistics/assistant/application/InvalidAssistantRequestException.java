package ro.logistics.smart_logistics.assistant.application;

public class InvalidAssistantRequestException extends RuntimeException {
    public InvalidAssistantRequestException(String message) {
        super(message);
    }
}
