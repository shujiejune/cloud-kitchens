package org.example.authservice.exception;

/** Login failure — email unknown or password mismatch. Single type so the response does not leak which half failed. Maps to 401. */
public class InvalidCredentialsException extends RuntimeException {
    public InvalidCredentialsException(String message) {
        super(message);
    }
}
