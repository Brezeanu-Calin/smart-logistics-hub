package ro.logistics.smart_logistics.route.api;

import ro.logistics.smart_logistics.route.application.RouteService;
import ro.logistics.smart_logistics.route.dto.RouteCreateRequest;
import ro.logistics.smart_logistics.route.dto.RouteResponse;
import ro.logistics.smart_logistics.route.dto.RouteSimulationPositionResponse;
import ro.logistics.smart_logistics.route.dto.RouteStatusRequest;
import ro.logistics.smart_logistics.notification.application.OperationalNotification;
import ro.logistics.smart_logistics.route.domain.RouteStatus;
import ro.logistics.smart_logistics.route.domain.RouteStopType;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@Validated
@RestController
@RequestMapping("/api/routes")
public class RouteController {
    private final RouteService routeService;
    private final ApplicationEventPublisher eventPublisher;

    public RouteController(RouteService routeService, ApplicationEventPublisher eventPublisher) {
        this.routeService = routeService;
        this.eventPublisher = eventPublisher;
    }

    @GetMapping
    public List<RouteResponse> findAll() {
        return routeService.findAll();
    }

    @GetMapping("/simulations")
    public List<RouteSimulationPositionResponse> findActiveSimulationPositions() {
        return routeService.findActiveSimulationPositions();
    }

    @GetMapping("/{id}")
    public RouteResponse findById(@PathVariable @Positive Long id) {
        return routeService.findById(id);
    }

    @PostMapping("/{id}/directions")
    public RouteResponse recalculateDirections(@PathVariable @Positive Long id) {
        return routeService.recalculateDirections(id);
    }

    @PostMapping
    public ResponseEntity<RouteResponse> create(@Valid @RequestBody RouteCreateRequest request) {
        RouteResponse created = routeService.create(request);
        eventPublisher.publishEvent(new OperationalNotification(
                "Route planned",
                "Route #" + created.id() + " has been planned.",
                "app"
        ));
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.id())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    @PutMapping("/{id}")
    public RouteResponse updatePlanned(
            @PathVariable @Positive Long id,
            @Valid @RequestBody RouteCreateRequest request
    ) {
        return routeService.updatePlanned(id, request);
    }

    @PatchMapping("/{id}/status")
    public RouteResponse updateStatus(
            @PathVariable @Positive Long id,
            @Valid @RequestBody RouteStatusRequest request
    ) {
        RouteResponse updated = routeService.updateStatus(id, request);
        if (updated.status() == RouteStatus.CANCELLED) {
            eventPublisher.publishEvent(new OperationalNotification(
                    "Route cancelled",
                    "Route #" + updated.id() + " has been cancelled.",
                    "app"
            ));
        }
        return updated;
    }

    @PatchMapping("/{routeId}/goods/{goodsId}/deliver")
    public RouteResponse markGoodsDelivered(
            @PathVariable @Positive Long routeId,
            @PathVariable @Positive Long goodsId
    ) {
        RouteResponse updated = routeService.markGoodsDelivered(routeId, goodsId);
        String trackingCode = updated.stops().stream()
                .filter(stop -> stop.goodsId().equals(goodsId) && stop.type() == RouteStopType.DELIVERY)
                .map(stop -> stop.trackingCode())
                .findFirst()
                .orElse("freight item #" + goodsId);
        eventPublisher.publishEvent(new OperationalNotification(
                "Delivery confirmed",
                "Delivery confirmed for " + trackingCode + ".",
                "app"
        ));
        return updated;
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable @Positive Long id) {
        routeService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
