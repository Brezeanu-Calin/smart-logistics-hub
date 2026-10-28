package ro.logistics.smart_logistics.finance.application;

public class InvalidFinanceRangeException extends RuntimeException {
    public InvalidFinanceRangeException() {
        super("The finance date range must have a valid start and end date.");
    }
}
