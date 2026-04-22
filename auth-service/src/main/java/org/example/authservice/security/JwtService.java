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
 * Expiry     : 24 hours (configurable via app.jwt.expiration-hours)
 * Claims     : sub=operatorId (String), email, jti=UUID
 *
 * The secret key is read from application.yml — must be at least
 * 32 bytes (256 bits) for HS256.  In production this value comes
 * from AWS Secrets Manager injected as an environment variable.
 */
@Component
public class JwtService {

    private final SecretKey secretKey;
    private final long expirationHours;

    public JwtService(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.expiration-hours:24}") long expirationHours) {
        this.secretKey     = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationHours = expirationHours;
    }

    // ----------------------------------------------------------------
    // Token generation
    // ----------------------------------------------------------------

    /**
     * Builds and signs a JWT for the given operator.
     * The token is signed using HS256 with the configured secret key.
     */
    public String generateToken(Operator operator) {
        Instant now    = Instant.now();
        Instant expiry = now.plusSeconds(expirationHours * 3600);

        return Jwts.builder()
                .subject(String.valueOf(operator.getId()))
                .claim("email", operator.getEmail())
                .id(UUID.randomUUID().toString())   // jti — unique per token, used for blocklist
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

    /** Extracts the email claim from a valid token. */
    public String extractEmail(String token) {
        return parseClaims(token).get("email", String.class);
    }

    /** Extracts the jti claim — used as the Redis blocklist key on logout. */
    public String extractJti(String token) {
        return parseClaims(token).getId();
    }

    /**
     * Returns the remaining validity duration of a token.
     * Used to set the Redis blocklist entry TTL on logout so the
     * Redis key auto-expires when the token would have expired anyway.
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
     * Does NOT check the Redis blocklist — that is the Gateway's responsibility.
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
