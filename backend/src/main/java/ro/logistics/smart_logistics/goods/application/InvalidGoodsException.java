package ro.logistics.smart_logistics.goods.application;

public class InvalidGoodsException extends RuntimeException {
    public InvalidGoodsException(String message) {
        super(message);
    }
}
