package org.example.catalogservice.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * One ingredient or supply item in an operator's purchasing catalog.
 *
 * MySQL table: catalog_items
 *
 * Tenant isolation:  operatorId is extracted from the JWT by the
 * JwtAuthFilter and stored in SecurityContext.  The CatalogService
 * always appends "AND operator_id = :operatorId" to every query.
 *
 * Bulk import:  POST /api/v1/catalog/items/bulk accepts a CSV file.
 * Apache Commons CSV parses it row-by-row; each row maps to one
 * CatalogItem with the same operatorId.
 */
@Entity
@Table(
        name = "catalog_items",
        indexes = {
                @Index(name = "idx_catalog_operator",          columnList = "operator_id"),
                @Index(name = "idx_catalog_operator_category", columnList = "operator_id, category")
        }
)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class CatalogItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * FK to operators.id in auth-service's schema.
     * Not a JPA relationship (cross-service) — stored as a plain Long.
     * Application enforces tenant scoping in the service layer.
     */
    @Column(name = "operator_id", nullable = false)
    private Long operatorId;

    /** Human-readable item name, e.g. "Chicken Breast". */
    @Column(nullable = false, length = 255)
    private String name;

    /** Grouping, e.g. "Protein", "Dairy", "Packaging". */
    @Column(length = 100)
    private String category;

    /** Unit of measure, e.g. "lb", "case", "each". */
    @Column(nullable = false, length = 50)
    private String unit;

    /**
     * How much the operator typically buys in one purchase cycle.
     * Used as the quantity input to plan generation.
     */
    @Column(name = "preferred_qty", nullable = false, precision = 10, scale = 2)
    private BigDecimal preferredQty;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
