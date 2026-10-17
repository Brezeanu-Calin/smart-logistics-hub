package ro.logistics.smart_logistics.maintenance.application;

public class InvalidMaintenanceOperationException extends RuntimeException {
    public InvalidMaintenanceOperationException(String message) {
        super(message);
    }
}
