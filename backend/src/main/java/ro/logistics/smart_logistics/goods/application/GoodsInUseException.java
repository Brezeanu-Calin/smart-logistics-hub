package ro.logistics.smart_logistics.goods.application;

public class GoodsInUseException extends RuntimeException {
    public GoodsInUseException(Long id) {
        super("Goods " + id + " cannot be deleted because route history references it.");
    }
}
