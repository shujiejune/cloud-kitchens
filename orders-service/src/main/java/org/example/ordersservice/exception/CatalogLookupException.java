package org.example.ordersservice.exception;

/** Feign call to catalog-service failed (network, 5xx, or malformed response).
 * Maps to 502. */
public class CatalogLookupException extends RuntimeException {
    public CatalogLookupException(String message, Throwable cause) {
        super(message, cause);
    }
}
