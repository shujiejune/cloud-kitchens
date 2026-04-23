package org.example.authservice.service;

import io.jsonwebtoken.JwtException;
import org.example.authservice.dao.OperatorDAO;
import org.example.authservice.dto.AuthResponse;
import org.example.authservice.dto.ForgotPasswordRequest;
import org.example.authservice.dto.ForgotPasswordResponse;
import org.example.authservice.dto.LoginRequest;
import org.example.authservice.dto.OperatorResponse;
import org.example.authservice.dto.RefreshRequest;
import org.example.authservice.dto.RegisterRequest;
import org.example.authservice.dto.ResetPasswordRequest;
import org.example.authservice.entity.Operator;
import org.example.authservice.exception.AccountLockedException;
import org.example.authservice.exception.AccountSuspendedException;
import org.example.authservice.exception.DuplicateEmailException;
import org.example.authservice.exception.InvalidCredentialsException;
import org.example.authservice.mapper.AuthMapper;
import org.example.authservice.security.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthServiceImpl implements AuthService {

    private final OperatorDAO operatorDAO;
    private final JwtService jwtService;
    private final BCryptPasswordEncoder passwordEncoder;
    private final StringRedisTemplate redisTemplate;
    private final AuthMapper authMapper;

    private static final String BLOCKLIST_PREFIX      = "jwt:blocklist:";
    private static final String FAILED_ATTEMPTS_PREFIX = "auth:failed_attempts:";
    private static final String RESET_TOKEN_PREFIX     = "auth:reset:";

    private static final int      LOCKOUT_THRESHOLD = 5;
    private static final Duration LOCKOUT_DURATION   = Duration.ofMinutes(30);
    private static final Duration RESET_TOKEN_TTL    = Duration.ofMinutes(15);

    private final SecureRandom secureRandom = new SecureRandom();

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
        checkNotLocked(request.email());

        Optional<Operator> operatorOpt = operatorDAO.findByEmail(request.email());

        if (operatorOpt.isEmpty()
                || !passwordEncoder.matches(request.password(), operatorOpt.get().getPasswordHash())) {
            recordFailedAttempt(request.email());
            throw new InvalidCredentialsException("Invalid email or password.");
        }

        Operator operator = operatorOpt.get();

        if (operator.getStatus() == Operator.OperatorStatus.SUSPENDED) {
            throw new AccountSuspendedException("Account suspended. Please contact support.");
        }

        resetFailedAttempts(request.email());
        log.info("Operator id={} logged in", operator.getId());
        return buildAuthResponse(operator);
    }

    // ----------------------------------------------------------------
    // logout
    // ----------------------------------------------------------------

    @Override
    public void logout(String bearerToken) {
        String token = bearerToken.startsWith("Bearer ")
                ? bearerToken.substring(7)
                : bearerToken;

        String jti         = jwtService.extractJti(token);
        Duration remaining = jwtService.getRemainingValidity(token);

        redisTemplate.opsForValue().set(BLOCKLIST_PREFIX + jti, "1", remaining);
        log.info("JWT jti={} added to blocklist (TTL={})", jti, remaining);
    }

    // ----------------------------------------------------------------
    // refresh
    // ----------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public AuthResponse refresh(RefreshRequest request) {
        String token = request.refreshToken();
        try {
            if (!jwtService.isRefreshToken(token)) {
                throw new InvalidCredentialsException("Provided token is not a refresh token.");
            }
            Long operatorId = jwtService.extractOperatorId(token);
            Operator operator = operatorDAO.findById(operatorId)
                    .orElseThrow(() -> new InvalidCredentialsException("Operator not found."));
            if (operator.getStatus() == Operator.OperatorStatus.SUSPENDED) {
                throw new AccountSuspendedException("Account suspended. Please contact support.");
            }
            String accessToken = jwtService.generateToken(operator);
            LocalDateTime expiresAt = jwtService.getExpiresAt(accessToken);
            return AuthResponse.accessOnly(accessToken, expiresAt,
                    operator.getId(), operator.getEmail(), operator.getCompanyName());
        } catch (JwtException e) {
            throw new InvalidCredentialsException("Invalid or expired refresh token.");
        }
    }

    // ----------------------------------------------------------------
    // forgotPassword
    // ----------------------------------------------------------------

    @Override
    public ForgotPasswordResponse forgotPassword(ForgotPasswordRequest request) {
        byte[] bytes = new byte[16];
        secureRandom.nextBytes(bytes);
        String resetToken = HexFormat.of().formatHex(bytes);

        redisTemplate.opsForValue().set(RESET_TOKEN_PREFIX + resetToken, request.email(), RESET_TOKEN_TTL);
        log.info("Password reset token generated for email={}", request.email());

        // In production: send an email with the reset link instead of returning the token.
        return new ForgotPasswordResponse(
                "If that email is registered, a reset link has been sent.", resetToken);
    }

    // ----------------------------------------------------------------
    // resetPassword
    // ----------------------------------------------------------------

    @Override
    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        String key   = RESET_TOKEN_PREFIX + request.token();
        String email = redisTemplate.opsForValue().get(key);

        if (email == null) {
            throw new InvalidCredentialsException("Invalid or expired password reset token.");
        }

        Operator operator = operatorDAO.findByEmail(email)
                .orElseThrow(() -> new InvalidCredentialsException("Operator not found."));

        operator.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        operatorDAO.save(operator);
        redisTemplate.delete(key);

        log.info("Password reset for operator email={}", email);
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
        String accessToken   = jwtService.generateToken(operator);
        String refreshToken  = jwtService.generateRefreshToken(operator);
        LocalDateTime expiresAt = jwtService.getExpiresAt(accessToken);
        return AuthResponse.of(accessToken, refreshToken, expiresAt,
                operator.getId(), operator.getEmail(), operator.getCompanyName());
    }

    private void checkNotLocked(String email) {
        String countStr = redisTemplate.opsForValue().get(FAILED_ATTEMPTS_PREFIX + email);
        if (countStr != null && Integer.parseInt(countStr) >= LOCKOUT_THRESHOLD) {
            throw new AccountLockedException(
                    "Account temporarily locked due to too many failed login attempts. Try again in 30 minutes.");
        }
    }

    private void recordFailedAttempt(String email) {
        String key = FAILED_ATTEMPTS_PREFIX + email;
        Long count = redisTemplate.opsForValue().increment(key);
        if (Long.valueOf(1).equals(count)) {
            redisTemplate.expire(key, LOCKOUT_DURATION);
        }
    }

    private void resetFailedAttempts(String email) {
        redisTemplate.delete(FAILED_ATTEMPTS_PREFIX + email);
    }
}
