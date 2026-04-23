package org.example.authservice.security;

import org.example.authservice.entity.Operator;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.UUID;

/**
 * Encapsulates all JWT operations using JJWT 0.12.
 *
 * Algorithm : HS256 (HMAC-SHA-256)
 * Access token expiry  : 24 hours (jwt.expiration, in ms)
 * Refresh token expiry : 7 days
 * Claims : sub=operatorId (String), email, jti=UUID, type (access|refresh)
 */
@Component
public class JwtService {

    private final SecretKey secretKey;
    private final long expirationMs;

    public JwtService(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.expiration:86400000}") long expirationMs) {
        this.secretKey    = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
    }

    // ----------------------------------------------------------------
    // Token generation
    // ----------------------------------------------------------------

    /** Builds and signs a short-lived access JWT (24 h). */
    public String generateToken(Operator operator) {
        Instant now    = Instant.now();
        Instant expiry = now.plusMillis(expirationMs);

        return Jwts.builder()
                .subject(String.valueOf(operator.getId()))
                .claim("email", operator.getEmail())
                .claim("type", "access")
                .id(UUID.randomUUID().toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(secretKey)
                .compact();
    }

    /** Builds and signs a long-lived refresh JWT (7 days). */
    public String generateRefreshToken(Operator operator) {
        Instant now    = Instant.now();
        Instant expiry = now.plus(Duration.ofDays(7));

        return Jwts.builder()
                .subject(String.valueOf(operator.getId()))
                .claim("type", "refresh")
                .id(UUID.randomUUID().toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(secretKey)
                .compact();
    }

    // ----------------------------------------------------------------
    // Token parsing
    // ----------------------------------------------------------------

    /** Extracts the operatorId (subject claim) from a valid, non-expired token. */
    public Long extractOperatorId(String token) {
        return Long.parseLong(parseClaims(token).getSubject());
    }

    /** Extracts the email claim from a valid access token. */
    public String extractEmail(String token) {
        return parseClaims(token).get("email", String.class);
    }

    /** Extracts the jti claim — used as the Redis blocklist key on logout. */
    public String extractJti(String token) {
        return parseClaims(token).getId();
    }

    /** Returns true only if the token carries {@code type=refresh}. */
    public boolean isRefreshToken(String token) {
        return "refresh".equals(parseClaims(token).get("type", String.class));
    }

    /**
     * Returns the remaining validity duration of a token.
     * Used to set the Redis blocklist entry TTL on logout.
     */
    public Duration getRemainingValidity(String token) {
        Date expiration = parseClaims(token).getExpiration();
        long secondsLeft = (expiration.getTime() - System.currentTimeMillis()) / 1000;
        return Duration.ofSeconds(Math.max(secondsLeft, 1));
    }

    /** Returns the expiry as LocalDateTime — included in AuthResponse. */
    public LocalDateTime getExpiresAt(String token) {
        Date expiration = parseClaims(token).getExpiration();
        return expiration.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
    }

    /**
     * Returns true if the token's signature is valid and it has not expired.
     *
     * @throws JwtException if the token is malformed or the signature is invalid
     */
    public boolean isValid(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    // ----------------------------------------------------------------
    // Private helpers
    // ----------------------------------------------------------------

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
