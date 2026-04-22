package org.example.authservice.exception;

/** Login blocked because operator.status = SUSPENDED. Maps to 403. */
public class AccountSuspendedException extends RuntimeException {
    public AccountSuspendedException(String message) {
        super(message);
    }
}
