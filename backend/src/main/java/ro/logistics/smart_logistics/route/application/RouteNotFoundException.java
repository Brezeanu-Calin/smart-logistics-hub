package ro.logistics.smart_logistics.route.application;

public class RouteNotFoundException extends RuntimeException {
    public RouteNotFoundException(Long id) {
        super("Route " + id + " was not found.");
    }
}
