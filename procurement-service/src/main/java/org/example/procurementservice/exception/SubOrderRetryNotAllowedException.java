package org.example.procurementservice.exception;

/** Retry requested for a line item whose subOrderStatus is not FAILED. Maps to 409. */
public class SubOrderRetryNotAllowedException extends RuntimeException {
    public SubOrderRetryNotAllowedException(String message) {
        super(message);
    }
}
