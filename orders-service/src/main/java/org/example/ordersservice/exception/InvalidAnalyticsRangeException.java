package org.example.ordersservice.exception;

/** Analytics `days` query parameter is outside the allowed range [1, 365].
 * Maps to 400. */
public class InvalidAnalyticsRangeException extends RuntimeException {
    public InvalidAnalyticsRangeException(String message) {
        super(message);
    }
}
