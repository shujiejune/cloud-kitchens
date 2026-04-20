package org.example.procurementservice.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * A submitted purchase order — the persisted result of an operator
 * confirming a purchase plan.
 *
 * MySQL table: orders
 *
 * Lifecycle:
 *   DRAFT            → plan generated, not yet submitted (plan lives in memory / MongoDB only)
 *   SUBMITTED        → operator confirmed; this row is created; fan-out to vendors begins
 *   PARTIAL_FAILURE  → at least one line item sub-order failed; operator can retry
 *   COMPLETE         → all line items CONFIRMED by vendors
 *
 * Partitioning (future):  partition by YEAR(submitted_at) once table grows large.
 *
 * Relationship:  one Order has many OrderLineItems (cascade persist/merge).
 */
@Entity
@Table(
        name = "orders",
        indexes = {
                @Index(name = "idx_orders_operator",           columnList = "operator_id"),
                @Index(name = "idx_orders_operator_submitted", columnList = "operator_id, submitted_at"),
                @Index(name = "idx_orders_status",             columnList = "status")
        }
)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Tenant owner — extracted from JWT, never user-supplied. */
    @Column(name = "operator_id", nullable = false)
    private Long operatorId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status = OrderStatus.SUBMITTED;

    /** Sum of all line item totals at submission time. */
    @Column(name = "total_cost", precision = 12, scale = 2)
    private BigDecimal totalCost;

    /**
     * totalCost vs. the baseline of buying everything from the single
     * cheapest overall vendor.  Displayed on the savings summary card.
     */
    @Column(name = "estimated_savings", precision = 12, scale = 2)
    private BigDecimal estimatedSavings;

    /** Set when the operator confirms the plan (= row creation time). */
    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** Cascade persist and merge — line items are always saved with their order. */
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<OrderLineItem> lineItems = new ArrayList<>();

    // ----------------------------------------------------------------
    // Enum
    // ----------------------------------------------------------------

    public enum OrderStatus {
        SUBMITTED, PARTIAL_FAILURE, COMPLETE
    }
}
