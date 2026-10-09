package ro.logistics.smart_logistics.goods.application;

public class GoodsLockedException extends RuntimeException {
    public GoodsLockedException(Long id) {
        super("Goods " + id + " cannot be edited or deleted while its status is ASSIGNED or IN_TRANSIT.");
    }
}
