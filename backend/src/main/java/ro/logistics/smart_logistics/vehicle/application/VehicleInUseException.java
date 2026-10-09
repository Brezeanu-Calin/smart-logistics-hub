package ro.logistics.smart_logistics.vehicle.application;

public class VehicleInUseException extends RuntimeException {
    public VehicleInUseException(Long id) {
        super("Vehicle " + id + " cannot be deleted while referenced by a route.");
    }
}
