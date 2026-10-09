package ro.logistics.smart_logistics.goods.application;

public class DuplicateGoodsTrackingCodeException extends RuntimeException {
    public DuplicateGoodsTrackingCodeException() {
        super("A generated tracking code already exists. Please retry the request.");
    }
}
