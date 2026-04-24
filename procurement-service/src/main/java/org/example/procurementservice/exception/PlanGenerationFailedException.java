package org.example.procurementservice.exception;

public class PlanGenerationFailedException extends RuntimeException {
    public PlanGenerationFailedException(String message) {
        super(message);
    }
}
