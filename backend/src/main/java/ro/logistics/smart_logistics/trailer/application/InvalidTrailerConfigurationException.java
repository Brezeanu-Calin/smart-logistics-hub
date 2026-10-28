package ro.logistics.smart_logistics.trailer.application;

public class InvalidTrailerConfigurationException extends RuntimeException {
    public InvalidTrailerConfigurationException() {
        super("A compatible vehicle class is required for this trailer category.");
    }
}
