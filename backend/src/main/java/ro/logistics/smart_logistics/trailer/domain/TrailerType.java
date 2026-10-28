package ro.logistics.smart_logistics.trailer.domain;

import ro.logistics.smart_logistics.goods.domain.GoodsType;

import java.util.Set;

public enum TrailerType {
    VAN_TRAILER(Set.of(GoodsType.GENERAL)),
    RIGID_TRUCK_TRAILER(Set.of(GoodsType.GENERAL)),
    ARTICULATED_TRUCK_TRAILER(Set.of(GoodsType.GENERAL)),
    BOX_TRAILER(Set.of(GoodsType.GENERAL)),
    CURTAIN_SIDE_TRAILER(Set.of(GoodsType.GENERAL, GoodsType.OVERSIZED)),
    REFRIGERATED_TRAILER(Set.of(GoodsType.GENERAL, GoodsType.REFRIGERATED)),
    HAZARDOUS_TRAILER(Set.of(GoodsType.GENERAL, GoodsType.HAZARDOUS)),
    TANKER_TRAILER(Set.of(GoodsType.LIQUID, GoodsType.HAZARDOUS)),
    FLATBED_TRAILER(Set.of(GoodsType.GENERAL, GoodsType.OVERSIZED, GoodsType.VEHICLES)),
    LOW_LOADER_TRAILER(Set.of(GoodsType.OVERSIZED, GoodsType.VEHICLES)),
    CONTAINER_CHASSIS_TRAILER(Set.of(GoodsType.CONTAINERIZED)),
    BULK_TIPPER_TRAILER(Set.of(GoodsType.BULK)),
    LIVESTOCK_TRAILER(Set.of(GoodsType.LIVESTOCK));

    private final Set<GoodsType> supportedGoodsTypes;

    TrailerType(Set<GoodsType> supportedGoodsTypes) {
        this.supportedGoodsTypes = supportedGoodsTypes;
    }

    public boolean supports(GoodsType goodsType) {
        return supportedGoodsTypes.contains(goodsType);
    }
}
