package org.example.authservice.dto;

import java.time.LocalDateTime;

/**
 * Response body for both POST /api/v1/auth/register and POST /api/v1/auth/login.
 *
 * The client stores the token in localStorage and attaches it as:
 *   Authorization: Bearer {token}
 * on every subsequent request.
 *
 * tokenType is always "Bearer" — included so the client doesn't need
 * to hardcode the scheme.
 *
 * expiresAt lets the client pre-emptively refresh before the token
 * expires rather than waiting for a 401.
 */
public record AuthResponse(
        String token,
        String tokenType,           // always "Bearer"
        LocalDateTime expiresAt,
        Long operatorId,
        String email,
        String companyName
) {
    /** Convenience factory that fixes tokenType = "Bearer". */
    public static AuthResponse of(
            String token,
            LocalDateTime expiresAt,
            Long operatorId,
            String email,
            String companyName) {
        return new AuthResponse(token, "Bearer", expiresAt, operatorId, email, companyName);
    }
}
