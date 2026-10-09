package ro.logistics.smart_logistics.vehicle.application;

public class InvalidVehicleStatusException extends RuntimeException {
    public InvalidVehicleStatusException(String message) {
        super(message);
    }
}
