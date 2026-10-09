package ro.logistics.smart_logistics.driver.application;

public class DuplicateDriverEmailException extends RuntimeException {
    public DuplicateDriverEmailException(String email) {
        super("A driver with email '" + email + "' already exists.");
    }
}
