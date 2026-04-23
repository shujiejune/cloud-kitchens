package org.example.authservice.service;

import org.example.authservice.dto.LoginRequest;
import org.example.authservice.dto.RegisterRequest;
import org.example.authservice.dto.AuthResponse;
import org.example.authservice.dto.OperatorResponse;
import org.example.authservice.entity.Operator;
import org.example.authservice.exception.AccountSuspendedException;
import org.example.authservice.exception.DuplicateEmailException;
import org.example.authservice.exception.InvalidCredentialsException;
import org.example.authservice.dao.OperatorDAO;
import org.example.authservice.mapper.AuthMapper;
import org.example.authservice.security.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Implementation of AuthService.
 *
 * Dependencies injected via constructor (Lombok @RequiredArgsConstructor):
 *   - OperatorDAO        : MySQL persistence
 *   - JwtService         : token signing / parsing (HS256, JJWT 0.12)
 *   - BCryptPasswordEncoder : password hashing / verification
 *   - StringRedisTemplate : token blocklist (logout)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthServiceImpl implements AuthService {

    private final OperatorDAO operatorDAO;
    private final JwtService jwtService;
    private final BCryptPasswordEncoder passwordEncoder;
    private final StringRedisTemplate redisTemplate;
    private final AuthMapper authMapper;

    // Redis key prefix for the JWT blocklist
    private static final String BLOCKLIST_PREFIX = "jwt:blocklist:";

    // ----------------------------------------------------------------
    // register
    // ----------------------------------------------------------------

    @Override
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (operatorDAO.existsByEmail(request.email())) {
            throw new DuplicateEmailException(
                    "An account with email '" + request.email() + "' already exists.");
        }

        Operator operator = Operator.builder()
                .email(request.email())
                .passwordHash(passwordEncoder.encode(request.password()))
                .companyName(request.companyName())
                .status(Operator.OperatorStatus.ACTIVE)
                .build();

        operator = operatorDAO.save(operator);
        log.info("Registered new operator id={} email={}", operator.getId(), operator.getEmail());

        return buildAuthResponse(operator);
    }

    // ----------------------------------------------------------------
    // login
    // ----------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        Operator operator = operatorDAO.findByEmail(request.email())
                .orElseThrow(() -> new InvalidCredentialsException("Invalid email or password."));

        if (!passwordEncoder.matches(request.password(), operator.getPasswordHash())) {
            throw new InvalidCredentialsException("Invalid email or password.");
        }

        if (operator.getStatus() == Operator.OperatorStatus.SUSPENDED) {
            throw new AccountSuspendedException(
                    "Account suspended. Please contact support.");
        }

        log.info("Operator id={} logged in", operator.getId());
        return buildAuthResponse(operator);
    }

    // ----------------------------------------------------------------
    // logout
    // ----------------------------------------------------------------

    @Override
    public void logout(String bearerToken) {
        // Strip "Bearer " prefix if present
        String token = bearerToken.startsWith("Bearer ")
                ? bearerToken.substring(7)
                : bearerToken;

        String jti            = jwtService.extractJti(token);
        Duration remaining    = jwtService.getRemainingValidity(token);

        // Write jti → "1" with TTL = remaining token lifetime.
        // The Gateway's JwtAuthFilter checks this key before accepting any token.
        redisTemplate.opsForValue().set(
                BLOCKLIST_PREFIX + jti,
                "1",
                remaining);

        log.info("JWT jti={} added to blocklist (TTL={})", jti, remaining);
    }

    // ----------------------------------------------------------------
    // getCurrentOperator
    // ----------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public OperatorResponse getCurrentOperator(Long operatorId) {
        Operator op = operatorDAO.findById(operatorId)
                .orElseThrow(() -> new InvalidCredentialsException("Operator not found."));
        return authMapper.toResponse(op);
    }

    // ----------------------------------------------------------------
    // Private helpers
    // ----------------------------------------------------------------

    private AuthResponse buildAuthResponse(Operator operator) {
        String token       = jwtService.generateToken(operator);
        LocalDateTime expiresAt = jwtService.getExpiresAt(token);
        return AuthResponse.of(token, expiresAt,
                operator.getId(), operator.getEmail(), operator.getCompanyName());
    }
}
