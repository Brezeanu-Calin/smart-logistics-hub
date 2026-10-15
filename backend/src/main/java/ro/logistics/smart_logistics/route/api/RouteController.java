package ro.logistics.smart_logistics.route.api;

import ro.logistics.smart_logistics.route.application.RouteService;
import ro.logistics.smart_logistics.route.dto.RouteCreateRequest;
import ro.logistics.smart_logistics.route.dto.RouteResponse;
import ro.logistics.smart_logistics.route.dto.RouteStatusRequest;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
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
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@Validated
@RestController
@RequestMapping("/api/routes")
public class RouteController {
    private final RouteService routeService;

    public RouteController(RouteService routeService) {
        this.routeService = routeService;
    }

    @GetMapping
    public List<RouteResponse> findAll() {
        return routeService.findAll();
    }

    @GetMapping("/{id}")
    public RouteResponse findById(@PathVariable @Positive Long id) {
        return routeService.findById(id);
    }

    @PostMapping
    public ResponseEntity<RouteResponse> create(@Valid @RequestBody RouteCreateRequest request) {
        RouteResponse created = routeService.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.id())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    @PatchMapping("/{id}/status")
    public RouteResponse updateStatus(
            @PathVariable @Positive Long id,
            @Valid @RequestBody RouteStatusRequest request
    ) {
        return routeService.updateStatus(id, request);
    }

    @PatchMapping("/{routeId}/goods/{goodsId}/deliver")
    public RouteResponse markGoodsDelivered(
            @PathVariable @Positive Long routeId,
            @PathVariable @Positive Long goodsId
    ) {
        return routeService.markGoodsDelivered(routeId, goodsId);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable @Positive Long id) {
        routeService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
