package ro.logistics.smart_logistics.goods.application;

public class InvalidGoodsStatusException extends RuntimeException {
    public InvalidGoodsStatusException(String message) {
        super(message);
    }
}
