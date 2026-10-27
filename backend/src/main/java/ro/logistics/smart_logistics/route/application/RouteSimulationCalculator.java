package ro.logistics.smart_logistics.route.application;

import ro.logistics.smart_logistics.route.domain.Route;
import ro.logistics.smart_logistics.route.domain.RouteGeometryPoint;
import ro.logistics.smart_logistics.route.dto.RouteSimulationPositionResponse;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

final class RouteSimulationCalculator {
    private static final double EARTH_RADIUS_METERS = 6_371_000;
    private static final double SIMULATION_SPEED_METERS_PER_SECOND = 40_000.0 / 30;
    private static final List<String> COMPASS_DIRECTIONS = List.of("N", "NE", "E", "SE", "S", "SW", "W", "NW");

    private RouteSimulationCalculator() {
    }

    static RouteSimulationPositionResponse calculate(Route route, OffsetDateTime now) {
        OffsetDateTime activatedAt = route.getActivatedAt();
        if (activatedAt == null) {
            throw new IllegalStateException("An active route must have an activation timestamp.");
        }
        if (now == null) {
            throw new IllegalArgumentException("Simulation time is required.");
        }

        List<RouteGeometryPoint> points = route.getGeometryPoints();
        if (points.size() < 2) {
            throw new IllegalStateException("An active route must have saved route geometry.");
        }

        List<Segment> segments = new ArrayList<>(points.size() - 1);
        double totalDistance = 0;
        for (int index = 1; index < points.size(); index++) {
            RouteGeometryPoint start = points.get(index - 1);
            RouteGeometryPoint end = points.get(index);
            double length = distanceMeters(start.getLatitude(), start.getLongitude(), end.getLatitude(), end.getLongitude());
            if (length <= 0) {
                continue;
            }
            double bearing = bearingDegrees(start.getLatitude(), start.getLongitude(), end.getLatitude(), end.getLongitude());
            segments.add(new Segment(start, end, totalDistance, length, bearing));
            totalDistance += length;
        }
        if (segments.isEmpty() || totalDistance <= 0) {
            throw new IllegalStateException("An active route must have non-zero route geometry.");
        }

        double elapsedSeconds = Math.max(0, Duration.between(activatedAt, now).toMillis() / 1000.0);
        double targetDistance = elapsedSeconds * SIMULATION_SPEED_METERS_PER_SECOND;
        double progress = Math.min(1, targetDistance / totalDistance);
        targetDistance = Math.min(totalDistance, targetDistance);
        Segment segment = segments.getLast();
        for (Segment candidate : segments) {
            if (targetDistance <= candidate.startDistance() + candidate.length()) {
                segment = candidate;
                break;
            }
        }

        double fraction = Math.max(0, Math.min(1,
                (targetDistance - segment.startDistance()) / segment.length()));
        double latitude = segment.start().getLatitude()
                + (segment.end().getLatitude() - segment.start().getLatitude()) * fraction;
        double longitude = segment.start().getLongitude()
                + (segment.end().getLongitude() - segment.start().getLongitude()) * fraction;
        int headingIndex = (int) Math.round(segment.bearingDegrees() / 45) % COMPASS_DIRECTIONS.size();

        return new RouteSimulationPositionResponse(
                route.getId(),
                latitude,
                longitude,
                segment.bearingDegrees(),
                COMPASS_DIRECTIONS.get(headingIndex),
                progress,
                now
        );
    }

    private static double distanceMeters(double startLatitude, double startLongitude, double endLatitude, double endLongitude) {
        double radians = Math.PI / 180;
        double latitudeDelta = (endLatitude - startLatitude) * radians;
        double longitudeDelta = (endLongitude - startLongitude) * radians;
        double startLatitudeRadians = startLatitude * radians;
        double endLatitudeRadians = endLatitude * radians;
        double haversine = Math.sin(latitudeDelta / 2) * Math.sin(latitudeDelta / 2)
                + Math.cos(startLatitudeRadians) * Math.cos(endLatitudeRadians)
                * Math.sin(longitudeDelta / 2) * Math.sin(longitudeDelta / 2);
        double boundedHaversine = Math.max(0, Math.min(1, haversine));
        return EARTH_RADIUS_METERS * 2 * Math.atan2(
                Math.sqrt(boundedHaversine),
                Math.sqrt(1 - boundedHaversine)
        );
    }

    private static double bearingDegrees(double startLatitude, double startLongitude, double endLatitude, double endLongitude) {
        double radians = Math.PI / 180;
        double startLatitudeRadians = startLatitude * radians;
        double endLatitudeRadians = endLatitude * radians;
        double longitudeDelta = (endLongitude - startLongitude) * radians;
        double y = Math.sin(longitudeDelta) * Math.cos(endLatitudeRadians);
        double x = Math.cos(startLatitudeRadians) * Math.sin(endLatitudeRadians)
                - Math.sin(startLatitudeRadians) * Math.cos(endLatitudeRadians) * Math.cos(longitudeDelta);
        return (Math.atan2(y, x) * 180 / Math.PI + 360) % 360;
    }

    private record Segment(
            RouteGeometryPoint start,
            RouteGeometryPoint end,
            double startDistance,
            double length,
            double bearingDegrees
    ) {
    }
}
