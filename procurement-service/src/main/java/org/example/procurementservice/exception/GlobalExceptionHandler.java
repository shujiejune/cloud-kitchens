package org.example.procurementservice.exception;

import lombok.extern.slf4j.Slf4j;
import org.example.procurementservice.dto.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.List;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    // ---- 404 ----

    @ExceptionHandler({
            PlanNotFoundException.class,
            PlanLineItemNotFoundException.class,
            OrderNotFoundException.class,
            LineItemNotFoundException.class,
            VendorNotFoundException.class
    })
    public ResponseEntity<ErrorResponse> handleNotFound(RuntimeException ex) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    // ---- 409 ----

    @ExceptionHandler({
            PlanSubmissionMismatchException.class,
            StalePriceSnapshotException.class,
            SubOrderRetryNotAllowedException.class
    })
    public ResponseEntity<ErrorResponse> handleConflict(RuntimeException ex) {
        return build(HttpStatus.CONFLICT, ex.getMessage());
    }

    // ---- 502 ----

    @ExceptionHandler(CatalogLookupException.class)
    public ResponseEntity<ErrorResponse> handleCatalogLookup(CatalogLookupException ex) {
        log.warn("Catalog lookup failed", ex);
        return build(HttpStatus.BAD_GATEWAY, ex.getMessage());
    }

    // ---- 503 ----

    @ExceptionHandler(VendorUnavailableException.class)
    public ResponseEntity<ErrorResponse> handleVendorUnavailable(VendorUnavailableException ex) {
        log.warn("All vendors unavailable", ex);
        return build(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage());
    }

    // ---- 400 ----

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        List<String> details = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .toList();
        HttpStatus status = HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status).body(
                ErrorResponse.of(status.value(), status.getReasonPhrase(), "Validation failed", details));
    }

    @ExceptionHandler({
            MissingServletRequestParameterException.class,
            MissingRequestHeaderException.class,
            MethodArgumentTypeMismatchException.class
    })
    public ResponseEntity<ErrorResponse> handleBadRequest(Exception ex) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    // ---- 500 (fallback) ----

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception ex) {
        log.error("Unhandled exception", ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error");
    }

    private ResponseEntity<ErrorResponse> build(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(
                ErrorResponse.of(status.value(), status.getReasonPhrase(), message));
    }
}
