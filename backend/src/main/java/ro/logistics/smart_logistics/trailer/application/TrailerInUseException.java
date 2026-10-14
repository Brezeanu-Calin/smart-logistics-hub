package ro.logistics.smart_logistics.trailer.application;

public class TrailerInUseException extends RuntimeException {
    public TrailerInUseException(Long id) {
        super("Trailer " + id + " cannot be deleted while referenced by a route.");
    }
}
