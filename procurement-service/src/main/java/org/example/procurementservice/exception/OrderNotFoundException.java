package org.example.procurementservice.exception;

/** Order row missing or not owned by this operator. Maps to 404. */
public class OrderNotFoundException extends RuntimeException {
    public OrderNotFoundException(String message) {
        super(message);
    }
}
