package org.example.catalogservice.exception;

/** Catalog item not found, or belongs to a different operator. Maps to 404. */
public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String message) {
        super(message);
    }
}
