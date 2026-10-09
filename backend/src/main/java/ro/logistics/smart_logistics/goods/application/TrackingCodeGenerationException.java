package ro.logistics.smart_logistics.goods.application;

public class TrackingCodeGenerationException extends RuntimeException {
    public TrackingCodeGenerationException() {
        super("Could not generate an available goods tracking code.");
    }
}
