package org.example.authservice.service;

import org.example.authservice.dao.OperatorDAO;
import org.example.authservice.dto.AuthResponse;
import org.example.authservice.dto.ForgotPasswordRequest;
import org.example.authservice.dto.LoginRequest;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock OperatorDAO operatorDAO;
    @Mock JwtService jwtService;
    @Mock BCryptPasswordEncoder passwordEncoder;
    @Mock StringRedisTemplate redisTemplate;
    @Mock ValueOperations<String, String> valueOps;
    @Mock AuthMapper authMapper;

    @InjectMocks AuthServiceImpl authService;

    private Operator activeOperator;

    @BeforeEach
    void setUp() {
        activeOperator = Operator.builder()
                .id(1L)
                .email("chef@example.com")
                .passwordHash("$2a$12$hashedpassword")
                .companyName("Ghost Kitchen A")
                .status(Operator.OperatorStatus.ACTIVE)
                .build();

        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);
    }

    // ──────────────────── register ────────────────────

    @Test
    void register_success_returnsAuthResponseWithTokens() {
        var request = new RegisterRequest("chef@example.com", "password123", "Ghost Kitchen A");
        when(operatorDAO.existsByEmail("chef@example.com")).thenReturn(false);
        when(operatorDAO.save(any())).thenReturn(activeOperator);
        when(passwordEncoder.encode("password123")).thenReturn("$2a$12$hashedpassword");
        when(jwtService.generateToken(activeOperator)).thenReturn("access-token");
        when(jwtService.generateRefreshToken(activeOperator)).thenReturn("refresh-token");
        when(jwtService.getExpiresAt("access-token")).thenReturn(LocalDateTime.now().plusHours(24));

        AuthResponse response = authService.register(request);

        assertThat(response.token()).isEqualTo("access-token");
        assertThat(response.refreshToken()).isEqualTo("refresh-token");
        assertThat(response.operatorId()).isEqualTo(1L);
    }

    @Test
    void register_duplicateEmail_throwsDuplicateEmailException() {
        var request = new RegisterRequest("chef@example.com", "password123", "Ghost Kitchen A");
        when(operatorDAO.existsByEmail("chef@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(DuplicateEmailException.class);
        verify(operatorDAO, never()).save(any());
    }

    // ──────────────────── login ────────────────────

    @Test
    void login_success_resetsFailedAttemptsAndReturnsTokens() {
        var request = new LoginRequest("chef@example.com", "correctpassword");
        when(valueOps.get("auth:failed_attempts:chef@example.com")).thenReturn(null);
        when(operatorDAO.findByEmail("chef@example.com")).thenReturn(Optional.of(activeOperator));
        when(passwordEncoder.matches("correctpassword", "$2a$12$hashedpassword")).thenReturn(true);
        when(jwtService.generateToken(activeOperator)).thenReturn("access-token");
        when(jwtService.generateRefreshToken(activeOperator)).thenReturn("refresh-token");
        when(jwtService.getExpiresAt("access-token")).thenReturn(LocalDateTime.now().plusHours(24));

        AuthResponse response = authService.login(request);

        assertThat(response.token()).isEqualTo("access-token");
        verify(redisTemplate).delete("auth:failed_attempts:chef@example.com");
    }

    @Test
    void login_wrongPassword_recordsFailedAttemptAndThrows() {
        var request = new LoginRequest("chef@example.com", "wrongpassword");
        when(valueOps.get("auth:failed_attempts:chef@example.com")).thenReturn(null);
        when(operatorDAO.findByEmail("chef@example.com")).thenReturn(Optional.of(activeOperator));
        when(passwordEncoder.matches("wrongpassword", "$2a$12$hashedpassword")).thenReturn(false);
        when(valueOps.increment("auth:failed_attempts:chef@example.com")).thenReturn(1L);

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(InvalidCredentialsException.class);
        verify(valueOps).increment("auth:failed_attempts:chef@example.com");
    }

    @Test
    void login_accountLocked_throwsAccountLockedException() {
        var request = new LoginRequest("chef@example.com", "anypassword");
        when(valueOps.get("auth:failed_attempts:chef@example.com")).thenReturn("5");

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(AccountLockedException.class);
        verify(operatorDAO, never()).findByEmail(anyString());
    }

    @Test
    void login_suspendedAccount_throwsAccountSuspendedException() {
        activeOperator.setStatus(Operator.OperatorStatus.SUSPENDED);
        var request = new LoginRequest("chef@example.com", "correctpassword");
        when(valueOps.get("auth:failed_attempts:chef@example.com")).thenReturn(null);
        when(operatorDAO.findByEmail("chef@example.com")).thenReturn(Optional.of(activeOperator));
        when(passwordEncoder.matches("correctpassword", "$2a$12$hashedpassword")).thenReturn(true);

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(AccountSuspendedException.class);
    }

    // ──────────────────── logout ────────────────────

    @Test
    void logout_addsJtiToRedisBlocklist() {
        when(jwtService.extractJti("token123")).thenReturn("jti-uuid");
        when(jwtService.getRemainingValidity("token123")).thenReturn(Duration.ofHours(23));

        authService.logout("Bearer token123");

        verify(valueOps).set(eq("jwt:blocklist:jti-uuid"), eq("1"), eq(Duration.ofHours(23)));
    }

    // ──────────────────── refresh ────────────────────

    @Test
    void refresh_validRefreshToken_returnsNewAccessToken() {
        var request = new RefreshRequest("valid-refresh-token");
        when(jwtService.isRefreshToken("valid-refresh-token")).thenReturn(true);
        when(jwtService.extractOperatorId("valid-refresh-token")).thenReturn(1L);
        when(operatorDAO.findById(1L)).thenReturn(Optional.of(activeOperator));
        when(jwtService.generateToken(activeOperator)).thenReturn("new-access-token");
        when(jwtService.getExpiresAt("new-access-token")).thenReturn(LocalDateTime.now().plusHours(24));

        AuthResponse response = authService.refresh(request);

        assertThat(response.token()).isEqualTo("new-access-token");
        assertThat(response.refreshToken()).isNull();
    }

    @Test
    void refresh_accessTokenProvided_throwsInvalidCredentialsException() {
        var request = new RefreshRequest("access-token-not-refresh");
        when(jwtService.isRefreshToken("access-token-not-refresh")).thenReturn(false);

        assertThatThrownBy(() -> authService.refresh(request))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    // ──────────────────── forgotPassword ────────────────────

    @Test
    void forgotPassword_alwaysReturnsSuccessMessage() {
        var request = new ForgotPasswordRequest("anyone@example.com");

        var response = authService.forgotPassword(request);

        assertThat(response.message()).isNotBlank();
        assertThat(response.resetToken()).isNotBlank();
        verify(valueOps).set(anyString(), eq("anyone@example.com"), any(Duration.class));
    }

    // ──────────────────── resetPassword ────────────────────

    @Test
    void resetPassword_validToken_updatesPasswordAndDeletesKey() {
        var request = new ResetPasswordRequest("valid-hex-token", "NewPass123!");
        when(valueOps.get("auth:reset:valid-hex-token")).thenReturn("chef@example.com");
        when(operatorDAO.findByEmail("chef@example.com")).thenReturn(Optional.of(activeOperator));
        when(passwordEncoder.encode("NewPass123!")).thenReturn("$2a$12$newhash");

        authService.resetPassword(request);

        ArgumentCaptor<Operator> captor = ArgumentCaptor.forClass(Operator.class);
        verify(operatorDAO).save(captor.capture());
        assertThat(captor.getValue().getPasswordHash()).isEqualTo("$2a$12$newhash");
        verify(redisTemplate).delete("auth:reset:valid-hex-token");
    }

    @Test
    void resetPassword_invalidToken_throwsInvalidCredentialsException() {
        var request = new ResetPasswordRequest("expired-token", "NewPass123!");
        when(valueOps.get("auth:reset:expired-token")).thenReturn(null);

        assertThatThrownBy(() -> authService.resetPassword(request))
                .isInstanceOf(InvalidCredentialsException.class);
        verify(operatorDAO, never()).findByEmail(anyString());
    }

    // ──────────────────── getCurrentOperator ────────────────────

    @Test
    void getCurrentOperator_success_returnsResponse() {
        when(operatorDAO.findById(1L)).thenReturn(Optional.of(activeOperator));

        authService.getCurrentOperator(1L);

        verify(authMapper).toResponse(activeOperator);
    }

    @Test
    void getCurrentOperator_notFound_throwsInvalidCredentialsException() {
        when(operatorDAO.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.getCurrentOperator(99L))
                .isInstanceOf(InvalidCredentialsException.class);
    }
}
