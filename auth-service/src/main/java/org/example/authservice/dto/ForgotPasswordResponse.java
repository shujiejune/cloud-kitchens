package org.example.authservice.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Response for POST /auth/forgot-password.
 * In production the resetToken is never included (email only).
 * In dev/test it is returned directly for convenience.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ForgotPasswordResponse(
        String message,
        String resetToken
) {}
