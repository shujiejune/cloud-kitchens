package org.example.ordersservice.exception;

/** Order id unknown or belongs to a different operator. Maps to 404. */
public class OrderNotFoundException extends RuntimeException {
    public OrderNotFoundException(String message) {
        super(message);
    }
}
