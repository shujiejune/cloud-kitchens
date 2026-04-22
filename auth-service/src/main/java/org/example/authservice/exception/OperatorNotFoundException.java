package org.example.authservice.exception;

/** Operator id from a valid JWT no longer exists in the DB (deleted after token issuance). Maps to 404. */
public class OperatorNotFoundException extends RuntimeException {
    public OperatorNotFoundException(Long operatorId) {
        super("Operator not found: " + operatorId);
    }
}
