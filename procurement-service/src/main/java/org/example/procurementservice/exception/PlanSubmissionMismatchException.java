package org.example.procurementservice.exception;

/** SubmitOrderRequest.lineItems disagree with the stored plan (catalog id, vendor id,
 * quantity, or unit price). Maps to 409. */
public class PlanSubmissionMismatchException extends RuntimeException {
    public PlanSubmissionMismatchException(String message) {
        super(message);
    }
}
