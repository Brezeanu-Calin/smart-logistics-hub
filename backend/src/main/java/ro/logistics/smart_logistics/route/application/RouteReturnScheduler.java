package ro.logistics.smart_logistics.route.application;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class RouteReturnScheduler {
    private final RouteService routeService;

    public RouteReturnScheduler(RouteService routeService) {
        this.routeService = routeService;
    }

    @Scheduled(fixedDelayString = "${routing.return-check-interval-ms:60000}")
    public void releaseReturnedResources() {
        routeService.releaseDueReturns();
    }
}
