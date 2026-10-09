package ro.logistics.smart_logistics.goods.application;

public class GoodsNotFoundException extends RuntimeException {
    public GoodsNotFoundException(Long id) {
        super("Goods " + id + " was not found.");
    }
}
