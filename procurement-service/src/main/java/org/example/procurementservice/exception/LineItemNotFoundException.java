package org.example.procurementservice.exception;

/** OrderLineItem missing or not part of the target order. Maps to 404. */
public class LineItemNotFoundException extends RuntimeException {
    public LineItemNotFoundException(String message) {
        super(message);
    }
}
