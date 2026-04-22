package org.example.authservice.exception;

/** Registration attempted with an email already present in operators. Maps to 409. */
public class DuplicateEmailException extends RuntimeException {
    public DuplicateEmailException(String message) {
        super(message);
    }
}
