package org.example.ordersservice.entity;

import jakarta.persistence.*;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Read-only view of the orders table, owned and written by procurement-service.
 *
 * orders-service never INSERTs or UPDATEs this table — it only reads.
 * The @Table mapping is identical to procurement-service's Order entity
 * so both services point to the same MySQL rows.
 *
 * Design note: we use a separate class (not a shared library) to keep
 * the two services independently deployable.  If procurement-service
 * adds a column, orders-service picks it up on its next deploy — no
 * forced simultaneous release.
 *
 * Hibernate is set to validate=none / ddl-auto=none in orders-service
 * so it never attempts DDL on these tables.
 */
@Entity
@Table(name = "orders")
@Getter
@org.hibernate.annotations.Immutable   // Hibernate will never flush changes
public class OrderView {

    @Id
    private Long id;

    @Column(name = "operator_id", nullable = false)
    private Long operatorId;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "total_cost", precision = 12, scale = 2)
    private BigDecimal totalCost;

    @Column(name = "estimated_savings", precision = 12, scale = 2)
    private BigDecimal estimatedSavings;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "order", fetch = FetchType.LAZY)
    private List<OrderLineItemView> lineItems = new ArrayList<>();
}
