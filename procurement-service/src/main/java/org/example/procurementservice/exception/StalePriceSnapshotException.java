package org.example.procurementservice.exception;

/** Override requires a snapshot that is missing or older than the staleness threshold.
 * Maps to 409 — client should re-run plan generation with refreshPrices=true. */
public class StalePriceSnapshotException extends RuntimeException {
    public StalePriceSnapshotException(String message) {
        super(message);
    }
}
