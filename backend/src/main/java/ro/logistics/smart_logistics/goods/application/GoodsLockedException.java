package ro.logistics.smart_logistics.goods.application;

public class GoodsLockedException extends RuntimeException {
    public GoodsLockedException(Long id) {
        super("Goods " + id + " can only be edited or deleted while its status is UNASSIGNED.");
    }
}
