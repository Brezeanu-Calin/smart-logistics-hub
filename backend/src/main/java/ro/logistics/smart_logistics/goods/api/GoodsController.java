package ro.logistics.smart_logistics.goods.api;

import ro.logistics.smart_logistics.goods.application.GoodsService;
import ro.logistics.smart_logistics.goods.dto.GoodsDTO;
import ro.logistics.smart_logistics.goods.dto.GoodsResponse;
import ro.logistics.smart_logistics.goods.dto.GoodsStatusRequest;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
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
@RequestMapping("/api/goods")
public class GoodsController {
    private final GoodsService goodsService;

    public GoodsController(GoodsService goodsService) {
        this.goodsService = goodsService;
    }

    @GetMapping
    public List<GoodsResponse> findAll() {
        return goodsService.findAll();
    }

    @GetMapping("/unassigned")
    public List<GoodsResponse> findUnassigned() {
        return goodsService.findUnassigned();
    }

    @GetMapping("/{id}")
    public GoodsResponse findById(@PathVariable Long id) {
        return goodsService.findById(id);
    }

    @PostMapping
    public ResponseEntity<GoodsResponse> create(@Valid @RequestBody GoodsDTO dto) {
        return created(goodsService.saveGoods(dto));
    }

    @PostMapping("/{id}/duplicate")
    public ResponseEntity<GoodsResponse> duplicate(@PathVariable Long id) {
        return created(goodsService.duplicateGoods(id));
    }

    @PutMapping("/{id}")
    public GoodsResponse update(@PathVariable Long id, @Valid @RequestBody GoodsDTO dto) {
        return goodsService.updateGoods(id, dto);
    }

    @PatchMapping("/{id}/status")
    public GoodsResponse updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody GoodsStatusRequest request
    ) {
        return goodsService.updateDispatcherManagedStatus(id, request.status());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        goodsService.delete(id);
        return ResponseEntity.noContent().build();
    }

    private ResponseEntity<GoodsResponse> created(GoodsResponse goods) {
        URI location = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/goods/{id}")
                .buildAndExpand(goods.id())
                .toUri();
        return ResponseEntity.created(location).body(goods);
    }
}
