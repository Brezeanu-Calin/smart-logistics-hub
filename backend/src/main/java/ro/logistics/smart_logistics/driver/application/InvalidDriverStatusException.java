package ro.logistics.smart_logistics.driver.application;

public class InvalidDriverStatusException extends RuntimeException {
    public InvalidDriverStatusException() {
        super("Driver status IN_TRANSIT is managed by route assignment.");
    }

    public InvalidDriverStatusException(String message) {
        super(message);
    }
}
