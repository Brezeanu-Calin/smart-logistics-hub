package ro.logistics.smart_logistics.goods.application;

import ro.logistics.smart_logistics.goods.domain.Goods;
import ro.logistics.smart_logistics.goods.domain.GoodsStatus;
import ro.logistics.smart_logistics.goods.dto.GoodsDTO;
import ro.logistics.smart_logistics.goods.dto.GoodsResponse;
import ro.logistics.smart_logistics.goods.persistence.GoodsRepository;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Year;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

@Service
@Transactional
public class GoodsService {
    private static final int TRACKING_CODE_ATTEMPTS = 100;

    private final GoodsRepository goodsRepository;

    public GoodsService(GoodsRepository goodsRepository) {
        this.goodsRepository = goodsRepository;
    }

    @Transactional(readOnly = true)
    public List<GoodsResponse> findAll() {
        return goodsRepository.findAllByOrderByIdAsc().stream()
                .map(GoodsService::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<GoodsResponse> findUnassigned() {
        return goodsRepository.findAllByStatusOrderByIdAsc(GoodsStatus.UNASSIGNED).stream()
                .map(GoodsService::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public GoodsResponse findById(Long id) {
        return toResponse(findGoods(id));
    }

    public GoodsResponse saveGoods(GoodsDTO dto) {
        validate(dto);
        Goods goods = new Goods(
                generateTrackingCode(),
                normalize(dto.clientName()),
                dto.goodsType(),
                normalizeOptional(dto.description()),
                dto.weightKg(),
                dto.volumeM3(),
                normalize(dto.pickupAddress()),
                normalize(dto.deliveryAddress())
        );
        return save(goods);
    }

    public GoodsResponse updateGoods(Long id, GoodsDTO dto) {
        validate(dto);
        Goods goods = findGoods(id);
        ensureEditable(goods);
        goods.updateDetails(
                normalize(dto.clientName()),
                dto.goodsType(),
                normalizeOptional(dto.description()),
                dto.weightKg(),
                dto.volumeM3(),
                normalize(dto.pickupAddress()),
                normalize(dto.deliveryAddress())
        );
        return save(goods);
    }

    public GoodsResponse duplicateGoods(Long id) {
        Goods source = findGoods(id);
        return saveGoods(new GoodsDTO(
                source.getClientName(),
                source.getGoodsType(),
                source.getDescription(),
                source.getWeightKg(),
                source.getVolumeM3(),
                source.getPickupAddress(),
                source.getDeliveryAddress()
        ));
    }

    public GoodsResponse updateDispatcherManagedStatus(Long id, GoodsStatus status) {
        Goods goods = findGoods(id);
        try {
            goods.updateStatus(status);
        } catch (IllegalArgumentException exception) {
            throw new InvalidGoodsStatusException(exception.getMessage());
        }
        return save(goods);
    }

    public void delete(Long id) {
        Goods goods = findGoods(id);
        ensureEditable(goods);
        goodsRepository.delete(goods);
    }

    public void deleteGoodsList(List<Goods> goodsList) {
        goodsList.clear();
    }

    private GoodsResponse save(Goods goods) {
        try {
            return toResponse(goodsRepository.saveAndFlush(goods));
        } catch (DataIntegrityViolationException exception) {
            throw new DuplicateGoodsTrackingCodeException();
        }
    }

    private String generateTrackingCode() {
        int year = Year.now().getValue();
        for (int attempt = 0; attempt < TRACKING_CODE_ATTEMPTS; attempt++) {
            String code = String.format(
                    Locale.ROOT,
                    "TRK-%d-%04d",
                    year,
                    ThreadLocalRandom.current().nextInt(10_000)
            );
            if (!goodsRepository.existsByTrackingCode(code)) {
                return code;
            }
        }
        throw new TrackingCodeGenerationException();
    }

    private static void validate(GoodsDTO dto) {
        if (dto == null) {
            throw new InvalidGoodsException("Goods details are required.");
        }
        if (isBlank(dto.clientName())) {
            throw new InvalidGoodsException("Client name is required.");
        }
        if (dto.goodsType() == null) {
            throw new InvalidGoodsException("Goods type is required.");
        }
        if (dto.weightKg() == null || !Double.isFinite(dto.weightKg()) || dto.weightKg() <= 0) {
            throw new InvalidGoodsException("Weight must be a finite value greater than zero.");
        }
        if (dto.volumeM3() == null || !Double.isFinite(dto.volumeM3()) || dto.volumeM3() <= 0) {
            throw new InvalidGoodsException("Volume must be a finite value greater than zero.");
        }
        if (isBlank(dto.pickupAddress()) || isBlank(dto.deliveryAddress())) {
            throw new InvalidGoodsException("Pickup and delivery addresses are required.");
        }
        if (dto.pickupAddress().trim().equalsIgnoreCase(dto.deliveryAddress().trim())) {
            throw new InvalidGoodsException("Pickup and delivery addresses must be different.");
        }
        if (dto.clientName().trim().length() > 150
                || dto.pickupAddress().trim().length() > 500
                || dto.deliveryAddress().trim().length() > 500
                || (dto.description() != null && dto.description().length() > 1000)) {
            throw new InvalidGoodsException("One or more goods fields exceed their maximum length.");
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String normalize(String value) {
        return value.trim();
    }

    private static String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private Goods findGoods(Long id) {
        return goodsRepository.findById(id).orElseThrow(() -> new GoodsNotFoundException(id));
    }

    private void ensureEditable(Goods goods) {
        if (goods.getStatus() == GoodsStatus.ASSIGNED || goods.getStatus() == GoodsStatus.IN_TRANSIT) {
            throw new GoodsLockedException(goods.getId());
        }
    }

    private static GoodsResponse toResponse(Goods goods) {
        return new GoodsResponse(
                goods.getId(),
                goods.getTrackingCode(),
                goods.getClientName(),
                goods.getGoodsType(),
                goods.getDescription(),
                goods.getWeightKg(),
                goods.getVolumeM3(),
                goods.getPickupAddress(),
                goods.getDeliveryAddress(),
                goods.getStatus()
        );
    }
}
