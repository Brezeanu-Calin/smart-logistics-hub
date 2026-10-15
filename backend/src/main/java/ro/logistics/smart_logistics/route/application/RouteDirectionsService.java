package ro.logistics.smart_logistics.route.application;

import java.util.List;

public interface RouteDirectionsService {
    RouteTravelEstimate estimate(List<String> orderedCities);
}
