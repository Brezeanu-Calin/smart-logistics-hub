package ro.logistics.smart_logistics.route.application;

public class RouteConflictException extends RuntimeException {
    public RouteConflictException(String message) {
        super(message);
    }
}
