package ro.logistics.smart_logistics.trailer.application;

public class InvalidTrailerStatusException extends RuntimeException {
    public InvalidTrailerStatusException(String message) {
        super(message);
    }
}
