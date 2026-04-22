package org.example.authservice.controller;

import org.example.authservice.dto.LoginRequest;
import org.example.authservice.dto.RegisterRequest;
import org.example.authservice.dto.AuthResponse;
import org.example.authservice.dto.OperatorResponse;
import org.example.authservice.security.JwtService;
import org.example.authservice.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for authentication endpoints.
 *
 * All paths are under /api/v1/auth.
 * POST /register and POST /login are permit-all in the Security config.
 * POST /logout and GET /me require a valid JWT (enforced by the Gateway
 * and by this service's own JwtAuthFilter).
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Auth", description = "Operator registration, login, and logout")
public class AuthController {

    private final AuthService authService;
    private final JwtService jwtService;

    // ----------------------------------------------------------------
    // POST /api/v1/auth/register
    // ----------------------------------------------------------------

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Register a new operator account")
    public AuthResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    // ----------------------------------------------------------------
    // POST /api/v1/auth/login
    // ----------------------------------------------------------------

    @PostMapping("/login")
    @Operation(summary = "Login and receive a JWT")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    // ----------------------------------------------------------------
    // POST /api/v1/auth/logout
    // ----------------------------------------------------------------

    /**
     * Adds the supplied JWT's jti to the Redis blocklist so the Gateway
     * refuses all future requests carrying that token.
     *
     * The token is read from the Authorization header because the client
     * may call this endpoint immediately before clearing localStorage —
     * we can't rely on the body.
     */
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Invalidate the current JWT (server-side blocklist)")
    public ResponseEntity<Void> logout(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader) {
        authService.logout(authHeader);
        return ResponseEntity.noContent().build();
    }

    // ----------------------------------------------------------------
    // GET /api/v1/auth/me
    // ----------------------------------------------------------------

    /**
     * Returns the public profile of the currently authenticated operator.
     *
     * @AuthenticationPrincipal injects the operatorId Long that the
     * JwtAuthFilter placed into the SecurityContext principal.
     */
    @GetMapping("/me")
    @Operation(summary = "Get the current operator's profile")
    public OperatorResponse me(@AuthenticationPrincipal Long operatorId) {
        return authService.getCurrentOperator(operatorId);
    }
}
