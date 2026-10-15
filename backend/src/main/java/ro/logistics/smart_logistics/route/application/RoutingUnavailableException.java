package ro.logistics.smart_logistics.route.application;

public class RoutingUnavailableException extends RuntimeException {
    public RoutingUnavailableException(String message) {
        super(message);
    }

    public RoutingUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
