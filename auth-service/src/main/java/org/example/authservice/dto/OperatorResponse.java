package org.example.authservice.dto;

import java.time.LocalDateTime;

/**
 * Safe public view of an Operator — never includes passwordHash.
 *
 * Returned by GET /api/v1/auth/me and used internally by other services
 * that call auth-service via Feign to resolve operator details.
 */
public record OperatorResponse(
        Long id,
        String email,
        String companyName,
        String status,
        LocalDateTime createdAt
) {}
