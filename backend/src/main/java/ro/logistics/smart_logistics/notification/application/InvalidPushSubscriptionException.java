package ro.logistics.smart_logistics.notification.application;

public class InvalidPushSubscriptionException extends RuntimeException {
    public InvalidPushSubscriptionException(String message) {
        super(message);
    }

    public InvalidPushSubscriptionException(String message, Throwable cause) {
        super(message, cause);
    }
}
