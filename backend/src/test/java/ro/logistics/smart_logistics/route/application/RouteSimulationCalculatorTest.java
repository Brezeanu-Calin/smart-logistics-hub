package ro.logistics.smart_logistics.route.application;

import ro.logistics.smart_logistics.driver.domain.Driver;
import ro.logistics.smart_logistics.driver.domain.LicenseCategory;
import ro.logistics.smart_logistics.route.domain.Route;
import ro.logistics.smart_logistics.route.dto.RouteSimulationPositionResponse;
import ro.logistics.smart_logistics.vehicle.domain.Vehicle;
import ro.logistics.smart_logistics.vehicle.domain.VehicleType;

import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RouteSimulationCalculatorTest {
    private static final OffsetDateTime ACTIVATED_AT = OffsetDateTime.parse("2026-10-27T12:00:00Z");

    @Test
    void movesFortyKilometersEveryThirtySecondsAlongRoute() {
        Route route = activeRoute();

        RouteSimulationPositionResponse position = RouteSimulationCalculator.calculate(
                route,
                ACTIVATED_AT.plusSeconds(30)
        );

        double expectedProgress = 40_000 / (Math.toRadians(10) * 6_371_000);
        assertEquals(expectedProgress, position.progress(), 0.0001);
        assertEquals(0, position.latitude(), 0.0001);
        assertEquals(10 * expectedProgress, position.longitude(), 0.0001);
        assertEquals(90, position.headingDegrees(), 0.0001);
        assertEquals("E", position.heading());
    }

    @Test
    void clampsPositionBeforeActivationAndAfterRouteCompletion() {
        Route route = activeRoute();

        RouteSimulationPositionResponse beforeActivation = RouteSimulationCalculator.calculate(
                route,
                ACTIVATED_AT.minusMinutes(1)
        );
        RouteSimulationPositionResponse afterCompletion = RouteSimulationCalculator.calculate(
                route,
                ACTIVATED_AT.plusHours(3)
        );

        assertEquals(0, beforeActivation.progress());
        assertEquals(0, beforeActivation.longitude(), 0.0001);
        assertEquals(1, afterCompletion.progress());
        assertEquals(10, afterCompletion.longitude(), 0.0001);
    }

    private static Route activeRoute() {
        Driver driver = new Driver("Alex", "alex@example.com", Set.of(LicenseCategory.B));
        Vehicle vehicle = new Vehicle("VAN-1", VehicleType.VAN, "Make", "Model", 2022, 5000.0, 20.0, 1000);
        Route route = new Route(
                driver,
                vehicle,
                null,
                ACTIVATED_AT,
                ACTIVATED_AT.plusHours(1),
                1_000_000L,
                3_600L,
                3_600L
        );
        route.addGeometryPoint(0.0, 0.0);
        route.addGeometryPoint(0.0, 10.0);
        route.activate(ACTIVATED_AT);
        return route;
    }
}
