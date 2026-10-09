package ro.logistics.smart_logistics.driver.application;

public class DriverNotFoundException extends RuntimeException {
    public DriverNotFoundException(Long id) {
        super("Driver " + id + " was not found.");
    }
}
