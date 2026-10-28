package ro.logistics.smart_logistics.goods.domain;

public enum GoodsType {
    GENERAL,
    REFRIGERATED,
    HAZARDOUS,
    OVERSIZED,
    BULK,
    LIQUID,
    CONTAINERIZED,
    VEHICLES,
    LIVESTOCK;

    public boolean requiresSpecializedTrailer() {
        return this != GENERAL;
    }
}
