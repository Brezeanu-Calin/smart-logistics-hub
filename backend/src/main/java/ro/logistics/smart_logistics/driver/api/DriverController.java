package ro.logistics.smart_logistics.driver.api;

import ro.logistics.smart_logistics.driver.application.DriverService;
import ro.logistics.smart_logistics.driver.dto.DriverRequest;
import ro.logistics.smart_logistics.driver.dto.DriverResponse;
import ro.logistics.smart_logistics.driver.dto.DriverStatusRequest;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
@Validated
@RequestMapping("/api/drivers")
public class DriverController {
    private final DriverService driverService;

    public DriverController(DriverService driverService) {
        this.driverService = driverService;
    }

    @GetMapping
    public List<DriverResponse> findAll() {
        return driverService.findAll();
    }

    @GetMapping("/archive")
    public List<DriverResponse> findArchived() {
        return driverService.findArchived();
    }

    @GetMapping("/{id}")
    public DriverResponse findById(@PathVariable @Positive Long id) {
        return driverService.findById(id);
    }

    @PostMapping
    public ResponseEntity<DriverResponse> create(@Valid @RequestBody DriverRequest request) {
        DriverResponse created = driverService.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.id())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    @PutMapping("/{id}")
    public DriverResponse update(
            @PathVariable @Positive Long id,
            @Valid @RequestBody DriverRequest request
    ) {
        return driverService.update(id, request);
    }

    @PatchMapping("/{id}/status")
    public DriverResponse updateStatus(
            @PathVariable @Positive Long id,
            @Valid @RequestBody DriverStatusRequest request
    ) {
        return driverService.updateDispatcherManagedStatus(id, request.status());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable @Positive Long id) {
        driverService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
