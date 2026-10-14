package ro.logistics.smart_logistics.trailer.application;

public class DuplicateTrailerLicensePlateException extends RuntimeException {
    public DuplicateTrailerLicensePlateException(String licensePlate) {
        super("A trailer with license plate '" + licensePlate + "' already exists.");
    }
}
