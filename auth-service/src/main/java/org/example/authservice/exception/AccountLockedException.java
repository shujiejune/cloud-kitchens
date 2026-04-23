package org.example.authservice.exception;

/** Thrown when an account is temporarily locked due to too many failed login attempts. Maps to 429. */
public class AccountLockedException extends RuntimeException {
    public AccountLockedException(String message) {
        super(message);
    }
}
