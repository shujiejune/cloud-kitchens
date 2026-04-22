package org.example.procurementservice.exception;

/** Purchase plan doc is missing (TTL expired) or not owned by this operator. Maps to 404. */
public class PlanNotFoundException extends RuntimeException {
    public PlanNotFoundException(String message) {
        super(message);
    }
}
