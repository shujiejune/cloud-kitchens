package org.example.procurementservice.exception;

/** Vendor id unknown or inactive (e.g. override target). Maps to 404. */
public class VendorNotFoundException extends RuntimeException {
    public VendorNotFoundException(String message) {
        super(message);
    }
}
