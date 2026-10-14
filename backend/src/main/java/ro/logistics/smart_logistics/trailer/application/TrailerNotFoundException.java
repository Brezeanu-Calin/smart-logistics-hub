package ro.logistics.smart_logistics.trailer.application;

public class TrailerNotFoundException extends RuntimeException {
    public TrailerNotFoundException(Long id) {
        super("Trailer " + id + " was not found.");
    }
}
