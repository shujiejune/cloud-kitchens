package org.example.ordersservice.exception;

/** Line item id is not part of the requested order. Maps to 404. */
public class LineItemNotFoundException extends RuntimeException {
    public LineItemNotFoundException(String message) {
        super(message);
    }
}
