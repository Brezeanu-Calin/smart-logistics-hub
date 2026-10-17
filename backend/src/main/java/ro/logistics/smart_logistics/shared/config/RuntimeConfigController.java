package ro.logistics.smart_logistics.shared.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/config")
public class RuntimeConfigController {
    private final String warehouseCity;

    public RuntimeConfigController(@Value("${routing.warehouse-city:Cluj-Napoca, Romania}") String warehouseCity) {
        this.warehouseCity = warehouseCity;
    }

    @GetMapping
    public WarehouseConfigResponse getConfig() {
        return new WarehouseConfigResponse(warehouseCity);
    }

    public record WarehouseConfigResponse(String warehouseCity) {
    }
}
