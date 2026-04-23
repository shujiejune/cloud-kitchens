package org.example.authservice.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;

/**
 * Response body for POST /api/v1/auth/register, POST /api/v1/auth/login,
 * and POST /api/v1/auth/refresh.
 *
 * refreshToken is only populated on register and login — not on token refresh
 * (to avoid unbounded refresh token chaining).
 *
 * expiresAt is for the access token; the refresh token always expires in 7 days.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AuthResponse(
        String token,
        String refreshToken,
        String tokenType,
        LocalDateTime expiresAt,
        Long operatorId,
        String email,
        String companyName
) {
    /** Full response including refresh token (register / login). */
    public static AuthResponse of(
            String token,
            String refreshToken,
            LocalDateTime expiresAt,
            Long operatorId,
            String email,
            String companyName) {
        return new AuthResponse(token, refreshToken, "Bearer", expiresAt, operatorId, email, companyName);
    }

    /** Access-only response (token refresh). */
    public static AuthResponse accessOnly(
            String token,
            LocalDateTime expiresAt,
            Long operatorId,
            String email,
            String companyName) {
        return new AuthResponse(token, null, "Bearer", expiresAt, operatorId, email, companyName);
    }
}
