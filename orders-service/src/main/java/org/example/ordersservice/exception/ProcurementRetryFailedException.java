package org.example.ordersservice.exception;

/** Feign call to procurement-service's internal retry endpoint failed
 * (network, 5xx, or circuit open). Maps to 502. */
public class ProcurementRetryFailedException extends RuntimeException {
    public ProcurementRetryFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}
