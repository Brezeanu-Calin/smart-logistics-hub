package ro.logistics.smart_logistics.vehicle.application;

public class VehicleScheduledException extends RuntimeException {
    public VehicleScheduledException(Long id) {
        super("Vehicle " + id + " cannot be changed while assigned to a route or returning to the depot.");
    }
}
