package ro.logistics.smart_logistics.vehicle.application;

public class DuplicateVehicleLicensePlateException extends RuntimeException {
    public DuplicateVehicleLicensePlateException(String licensePlate) {
        super("A vehicle with license plate '" + licensePlate + "' already exists.");
    }
}
