package ro.logistics.smart_logistics.shared.exception;

import ro.logistics.smart_logistics.driver.application.DriverInUseException;
import ro.logistics.smart_logistics.driver.application.DriverNotFoundException;
import ro.logistics.smart_logistics.driver.application.DuplicateDriverEmailException;
import ro.logistics.smart_logistics.driver.application.InvalidDriverStatusException;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(DriverNotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(DriverNotFoundException exception) {
        return response(HttpStatus.NOT_FOUND, exception.getMessage(), Map.of());
    }

    @ExceptionHandler({DuplicateDriverEmailException.class, DriverInUseException.class})
    public ResponseEntity<ApiError> handleConflict(RuntimeException exception) {
        return response(HttpStatus.CONFLICT, exception.getMessage(), Map.of());
    }

    @ExceptionHandler(InvalidDriverStatusException.class)
    public ResponseEntity<ApiError> handleInvalidStatus(InvalidDriverStatusException exception) {
        return response(HttpStatus.BAD_REQUEST, exception.getMessage(), Map.of());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException exception) {
        Map<String, String> fields = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors().forEach(error ->
                fields.putIfAbsent(error.getField(), error.getDefaultMessage())
        );
        return response(HttpStatus.BAD_REQUEST, "Request validation failed.", fields);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadableRequest(HttpMessageNotReadableException exception) {
        return response(HttpStatus.BAD_REQUEST, "Request body is malformed or contains an unsupported value.", Map.of());
    }

    private static ResponseEntity<ApiError> response(
            HttpStatus status,
            String message,
            Map<String, String> fields
    ) {
        return ResponseEntity.status(status)
                .body(new ApiError(Instant.now(), status.value(), status.getReasonPhrase(), message, fields));
    }
}
