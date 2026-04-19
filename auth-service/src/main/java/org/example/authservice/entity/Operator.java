package org.example.authservice.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * Represents a CloudKitchens tenant (food operator).
 *
 * MySQL table: operators
 *
 * Multi-tenancy:  every other table that stores operator data
 * carries an operator_id FK back to this row.  All service-layer
 * queries MUST include a WHERE operator_id = :operatorId predicate
 * to prevent cross-tenant data leakage.
 *
 * Authentication flow:
 *   1. POST /auth/register  → persist a new Operator (status = ACTIVE).
 *   2. POST /auth/login     → load by email, BCrypt.matches(), issue JWT
 *                             with sub = operator.getId().toString().
 */
@Entity
@Table(
        name = "operators",
        indexes = {
                @Index(name = "idx_operators_email",  columnList = "email",  unique = true),
                @Index(name = "idx_operators_status", columnList = "status")
        }
)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Operator {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Login identifier — must be globally unique. */
    @Column(nullable = false, unique = true, length = 255)
    private String email;

    /**
     * BCrypt hash of the raw password (cost factor 12).
     * Never returned in any API response.
     */
    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "company_name", nullable = false, length = 255)
    private String companyName;

    /**
     * ACTIVE   — normal account, can log in and use the system.
     * SUSPENDED — blocked by an admin; JWT issuance must be refused.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OperatorStatus status = OperatorStatus.ACTIVE;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // ----------------------------------------------------------------
    // Enum
    // ----------------------------------------------------------------

    public enum OperatorStatus {
        ACTIVE, SUSPENDED
    }
}
