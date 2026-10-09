package ro.logistics.smart_logistics.driver.application;

public class DriverInUseException extends RuntimeException {
    public DriverInUseException(Long id) {
        super("Driver " + id + " cannot be deleted while referenced by a route.");
    }
}
