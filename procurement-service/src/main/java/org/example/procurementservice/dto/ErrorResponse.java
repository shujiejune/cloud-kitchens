package org.example.procurementservice.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

/**
 * Uniform error body — same shape used in auth-service and catalog-service
 * so the dashboard can render any 4xx/5xx response generically.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
        Instant timestamp,
        int status,
        String error,
        String message,
        List<String> details
) {
    public static ErrorResponse of(int status, String error, String message) {
        return new ErrorResponse(Instant.now(), status, error, message, null);
    }

    public static ErrorResponse of(int status, String error, String message, List<String> details) {
        return new ErrorResponse(Instant.now(), status, error, message, details);
    }
}
