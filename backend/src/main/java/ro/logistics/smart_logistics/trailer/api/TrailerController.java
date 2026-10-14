package ro.logistics.smart_logistics.trailer.api;

import ro.logistics.smart_logistics.trailer.application.TrailerService;
import ro.logistics.smart_logistics.trailer.dto.TrailerRequest;
import ro.logistics.smart_logistics.trailer.dto.TrailerResponse;
import ro.logistics.smart_logistics.trailer.dto.TrailerStatusRequest;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@Validated
@RestController
@RequestMapping("/api/trailers")
public class TrailerController {
    private final TrailerService trailerService;

    public TrailerController(TrailerService trailerService) {
        this.trailerService = trailerService;
    }

    @GetMapping
    public List<TrailerResponse> findAll() {
        return trailerService.findAll();
    }

    @GetMapping("/{id}")
    public TrailerResponse findById(@PathVariable @Positive Long id) {
        return trailerService.findById(id);
    }

    @PostMapping
    public ResponseEntity<TrailerResponse> create(@Valid @RequestBody TrailerRequest request) {
        TrailerResponse created = trailerService.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.id())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    @PutMapping("/{id}")
    public TrailerResponse update(@PathVariable @Positive Long id, @Valid @RequestBody TrailerRequest request) {
        return trailerService.update(id, request);
    }

    @PatchMapping("/{id}/status")
    public TrailerResponse updateStatus(
            @PathVariable @Positive Long id,
            @Valid @RequestBody TrailerStatusRequest request
    ) {
        return trailerService.updateDispatcherManagedStatus(id, request.status());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable @Positive Long id) {
        trailerService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
