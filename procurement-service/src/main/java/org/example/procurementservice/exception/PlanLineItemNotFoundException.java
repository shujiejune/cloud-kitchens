package org.example.procurementservice.exception;

/** Override target catalogItemId is not present in the stored plan. Maps to 404. */
public class PlanLineItemNotFoundException extends RuntimeException {
    public PlanLineItemNotFoundException(String message) {
        super(message);
    }
}
