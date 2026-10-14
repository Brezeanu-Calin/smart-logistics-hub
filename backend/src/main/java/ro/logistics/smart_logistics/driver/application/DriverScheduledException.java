package ro.logistics.smart_logistics.driver.application;

public class DriverScheduledException extends RuntimeException {
    public DriverScheduledException(Long id) {
        super("Driver " + id + " cannot be changed while assigned to a planned or active route.");
    }
}
