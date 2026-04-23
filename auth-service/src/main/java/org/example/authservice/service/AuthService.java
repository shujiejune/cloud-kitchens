package org.example.authservice.service;

import org.example.authservice.dto.AuthResponse;
import org.example.authservice.dto.ForgotPasswordRequest;
import org.example.authservice.dto.ForgotPasswordResponse;
import org.example.authservice.dto.LoginRequest;
import org.example.authservice.dto.RefreshRequest;
import org.example.authservice.dto.RegisterRequest;
import org.example.authservice.dto.ResetPasswordRequest;
import org.example.authservice.dto.OperatorResponse;

/**
 * Contract for authentication and identity operations.
 */
public interface AuthService {

    /**
     * Creates a new Operator account and returns an AuthResponse with access + refresh tokens.
     *
     * @throws org.example.authservice.exception.DuplicateEmailException if email already exists
     */
    AuthResponse register(RegisterRequest request);

    /**
     * Authenticates an operator and returns an AuthResponse with access + refresh tokens.
     * Records failed attempts in Redis and locks the account after 5 consecutive failures.
     *
     * @throws org.example.authservice.exception.InvalidCredentialsException on bad email/password
     * @throws org.example.authservice.exception.AccountSuspendedException   if operator is SUSPENDED
     * @throws org.example.authservice.exception.AccountLockedException      if too many failed attempts
     */
    AuthResponse login(LoginRequest request);

    /**
     * Invalidates a JWT by writing its jti to the Redis blocklist.
     *
     * @param bearerToken the raw "Bearer <token>" header value
     */
    void logout(String bearerToken);

    /**
     * Issues a new access token from a valid, non-expired refresh token.
     * The refresh token itself is not rotated.
     *
     * @throws org.example.authservice.exception.InvalidCredentialsException on invalid/expired refresh token
     */
    AuthResponse refresh(RefreshRequest request);

    /**
     * Generates a password-reset token and stores it in Redis (15-min TTL).
     * In production, this token would be e-mailed; in dev it is returned in the response body.
     *
     * Always returns HTTP 200 regardless of whether the email is registered
     * (prevents account enumeration).
     */
    ForgotPasswordResponse forgotPassword(ForgotPasswordRequest request);

    /**
     * Validates the reset token, updates the operator's password hash, and deletes the token.
     *
     * @throws org.example.authservice.exception.InvalidCredentialsException on invalid/expired token
     */
    void resetPassword(ResetPasswordRequest request);

    /**
     * Returns the public profile of the currently authenticated operator.
     */
    OperatorResponse getCurrentOperator(Long operatorId);
}
