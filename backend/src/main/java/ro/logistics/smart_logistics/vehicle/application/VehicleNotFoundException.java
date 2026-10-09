package ro.logistics.smart_logistics.vehicle.application;

public class VehicleNotFoundException extends RuntimeException {
    public VehicleNotFoundException(Long id) {
        super("Vehicle " + id + " was not found.");
    }
}
