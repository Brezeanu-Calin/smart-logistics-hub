package ro.logistics.smart_logistics.goods.application;

import ro.logistics.smart_logistics.goods.domain.Goods;
import ro.logistics.smart_logistics.goods.domain.GoodsStatus;
import ro.logistics.smart_logistics.goods.domain.GoodsType;
import ro.logistics.smart_logistics.goods.dto.GoodsDTO;
import ro.logistics.smart_logistics.goods.persistence.GoodsRepository;
import ro.logistics.smart_logistics.route.persistence.RouteRepository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Year;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GoodsServiceTest {
    @Mock
    private GoodsRepository goodsRepository;
    @Mock
    private RouteRepository routeRepository;

    @InjectMocks
    private GoodsService goodsService;

    @Test
    void saveGoodsGeneratesTrackingCodeAndDefaultsStatus() {
        when(goodsRepository.existsByTrackingCode(anyString())).thenReturn(false);
        when(goodsRepository.saveAndFlush(any(Goods.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var response = goodsService.saveGoods(dto(" Cluj ", " Bucharest "));

        assertEquals(GoodsStatus.UNASSIGNED, response.status());
        assertEquals("Cluj", response.pickupCity());
        assertTrue(response.trackingCode().matches("TRK-" + Year.now().getValue() + "-\\d{4}"));
    }

    @Test
    void duplicateGoodsGeneratesFreshCodeAndResetsStatus() {
        Goods original = goods();
        original.assignToRoute();
        when(goodsRepository.findById(12L)).thenReturn(Optional.of(original));
        when(goodsRepository.existsByTrackingCode(anyString()))
                .thenAnswer(invocation -> original.getTrackingCode().equals(invocation.getArgument(0)));
        when(goodsRepository.saveAndFlush(any(Goods.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var duplicate = goodsService.duplicateGoods(12L);

        assertNotEquals(original.getTrackingCode(), duplicate.trackingCode());
        assertEquals(GoodsStatus.UNASSIGNED, duplicate.status());
        assertEquals(original.getClientName(), duplicate.clientName());
    }

    @Test
    void updateAndDeleteAreBlockedForAssignedGoods() {
        Goods assigned = goods();
        assigned.assignToRoute();
        when(goodsRepository.findByIdForUpdate(12L)).thenReturn(Optional.of(assigned));

        assertThrows(GoodsLockedException.class, () -> goodsService.updateGoods(12L, dto("Cluj", "Bucharest")));
        assertThrows(GoodsLockedException.class, () -> goodsService.delete(12L));

        verify(goodsRepository, never()).delete(any(Goods.class));
        verify(goodsRepository, never()).saveAndFlush(any(Goods.class));
    }

    @Test
    void updateAndDeleteAreBlockedForInTransitGoods() {
        Goods inTransit = goods();
        inTransit.assignToRoute();
        inTransit.startTransit();
        when(goodsRepository.findByIdForUpdate(12L)).thenReturn(Optional.of(inTransit));

        assertThrows(GoodsLockedException.class, () -> goodsService.updateGoods(12L, dto("Cluj", "Bucharest")));
        assertThrows(GoodsLockedException.class, () -> goodsService.delete(12L));

        verify(goodsRepository, never()).delete(any(Goods.class));
        verify(goodsRepository, never()).saveAndFlush(any(Goods.class));
    }

    @Test
    void routeLifecycleAdvancesGoodsStatus() {
        Goods goods = goods();
        goods.assignToRoute();
        assertEquals(GoodsStatus.ASSIGNED, goods.getStatus());
        goods.startTransit();
        assertEquals(GoodsStatus.IN_TRANSIT, goods.getStatus());
        goods.markDelivered();
        assertEquals(
                GoodsStatus.DELIVERED,
                goods.getStatus()
        );
    }

    @Test
    void saveGoodsRejectsEqualPickupAndDeliveryCities() {
        assertThrows(
                InvalidGoodsException.class,
                () -> goodsService.saveGoods(dto(" Cluj ", "cluj"))
        );
        verify(goodsRepository, never()).saveAndFlush(any(Goods.class));
    }

    @Test
    void saveGoodsRejectsNonPositiveWeight() {
        GoodsDTO invalid = new GoodsDTO(
                "Client",
                GoodsType.GENERAL,
                null,
                0.0,
                10.0,
                "Cluj",
                "Bucharest"
        );

        assertThrows(InvalidGoodsException.class, () -> goodsService.saveGoods(invalid));
        verify(goodsRepository, never()).saveAndFlush(any(Goods.class));
    }

    @Test
    void deleteGoodsListClearsOnlyProvidedAssociationList() {
        List<Goods> goodsList = new ArrayList<>(List.of(goods()));

        goodsService.deleteGoodsList(goodsList);

        assertEquals(List.of(), goodsList);
        verify(goodsRepository, never()).delete(any(Goods.class));
    }

    private static GoodsDTO dto(String pickupCity, String deliveryCity) {
        return new GoodsDTO(
                "Client",
                GoodsType.GENERAL,
                null,
                2500.0,
                10.0,
                pickupCity,
                deliveryCity
        );
    }

    private static Goods goods() {
        return new Goods(
                "TRK-2026-1234",
                "Client",
                GoodsType.GENERAL,
                null,
                2500.0,
                10.0,
                "Cluj",
                "Bucharest"
        );
    }
}
