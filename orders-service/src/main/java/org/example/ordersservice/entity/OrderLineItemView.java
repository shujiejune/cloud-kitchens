package org.example.ordersservice.entity;

import jakarta.persistence.*;
import lombok.Getter;

import java.math.BigDecimal;

/**
 * Read-only view of order_line_items, owned and written by procurement-service.
 *
 * See OrderView for the design rationale.
 *
 * The vendorName is not stored in this table — it lives in the vendors table
 * which procurement-service owns.  orders-service joins to vendors via JPQL
 * or fetches the vendor name through a Feign call if needed.  For the
 * analytics endpoints (spend-by-vendor), a native query joining
 * order_line_items → vendors is the most efficient approach.
 */
@Entity
@Table(name = "order_line_items")
@Getter
@org.hibernate.annotations.Immutable
public class OrderLineItemView {

    @Id
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private OrderView order;

    @Column(name = "catalog_item_id", nullable = false)
    private Long catalogItemId;

    @Column(name = "vendor_id", nullable = false)
    private Long vendorId;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal quantity;

    @Column(name = "unit_price", nullable = false, precision = 10, scale = 4)
    private BigDecimal unitPrice;

    @Column(name = "line_total", nullable = false, precision = 12, scale = 2)
    private BigDecimal lineTotal;

    @Column(name = "vendor_order_ref", length = 255)
    private String vendorOrderRef;

    @Column(name = "sub_order_status", nullable = false, length = 20)
    private String subOrderStatus;
}
