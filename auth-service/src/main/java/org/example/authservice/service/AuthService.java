package org.example.authservice.service;

import org.example.authservice.dto.LoginRequest;
import org.example.authservice.dto.RegisterRequest;
import org.example.authservice.dto.AuthResponse;
import org.example.authservice.dto.OperatorResponse;

/**
 * Contract for authentication and identity operations.
 *
 * Separating interface from implementation allows easy mocking in unit
 * tests and keeps the controller decoupled from implementation details.
 */
public interface AuthService {

    /**
     * Creates a new Operator account.
     *
     * Steps:
     *   1. Assert email is not already registered (throws DuplicateEmailException).
     *   2. BCrypt-hash the raw password (cost 12).
     *   3. Persist Operator with status=ACTIVE.
     *   4. Issue a JWT and return AuthResponse.
     *
     * @throws org.example.authservice.exception.DuplicateEmailException if email already exists
     */
    AuthResponse register(RegisterRequest request);

    /**
     * Authenticates an operator and issues a JWT.
     *
     * Steps:
     *   1. Load Operator by email (throws InvalidCredentialsException if not found).
     *   2. BCrypt.matches(rawPassword, passwordHash).
     *   3. Assert operator status is ACTIVE (throws AccountSuspendedException if SUSPENDED).
     *   4. Build JWT: sub=operatorId, email claim, jti=UUID, 24h expiry.
     *   5. Return AuthResponse.
     *
     * @throws org.example.authservice.exception.InvalidCredentialsException on bad email/password
     * @throws org.example.authservice.exception.AccountSuspendedException   if operator is SUSPENDED
     */
    AuthResponse login(LoginRequest request);

    /**
     * Invalidates a JWT by writing its jti to the Redis blocklist.
     *
     * The blocklist entry TTL = remaining token lifetime so Redis
     * auto-expires the entry when the token would have expired anyway.
     * The Gateway checks the blocklist on every request.
     *
     * @param bearerToken the raw "Bearer <token>" header value
     */
    void logout(String bearerToken);

    /**
     * Returns the public profile of the currently authenticated operator.
     * operatorId is extracted from the SecurityContext (set by the JWT filter).
     */
    OperatorResponse getCurrentOperator(Long operatorId);
}
