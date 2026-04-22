package org.example.procurementservice.exception;

/** Every vendor API failed during plan generation or a retry. Maps to 503. */
public class VendorUnavailableException extends RuntimeException {
    public VendorUnavailableException(String message) {
        super(message);
    }

    public VendorUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
