package org.example.procurementservice.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/**
 * One line of a submitted order — one catalog item assigned to one vendor.
 *
 * MySQL table: order_line_items
 *
 * Prices are captured at submission time so the historical record is
 * immutable even if vendor prices change afterward.
 *
 * Sub-order lifecycle (vendorSubOrderStatus):
 *   PENDING   → fan-out call to vendor not yet made (or queued)
 *   CONFIRMED → vendor API accepted the sub-order (returned a vendorOrderRef)
 *   FAILED    → vendor API returned a 4xx/5xx or timed out
 *
 * Retry:  POST /api/v1/orders/{orderId}/retry/{lineItemId} re-attempts only the
 *         FAILED line items without re-running the full plan.
 */
@Entity
@Table(
        name = "order_line_items",
        indexes = {
                @Index(name = "idx_oli_order",        columnList = "order_id"),
                @Index(name = "idx_oli_vendor",       columnList = "vendor_id"),
                @Index(name = "idx_oli_catalog_item", columnList = "catalog_item_id")
        }
)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class OrderLineItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    /**
     * FK to catalog_items.id (catalog-service schema).
     * Cross-service FK — stored as a plain Long, not a JPA relationship.
     */
    @Column(name = "catalog_item_id", nullable = false)
    private Long catalogItemId;

    /**
     * FK to vendors.id — which vendor was assigned for this item.
     * JPA relationship within the same service schema.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vendor_id", nullable = false)
    private Vendor vendor;

    /** Quantity to purchase (copied from catalog_items.preferred_qty at plan time). */
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal quantity;

    /** Unit price at the time of plan generation (captured from price snapshot). */
    @Column(name = "unit_price", nullable = false, precision = 10, scale = 4)
    private BigDecimal unitPrice;

    /** quantity × unitPrice, pre-computed and stored for fast aggregation. */
    @Column(name = "line_total", nullable = false, precision = 12, scale = 2)
    private BigDecimal lineTotal;

    /**
     * The vendor's own order reference number — set when vendorSubOrderStatus
     * transitions to CONFIRMED.  Null while PENDING or FAILED.
     */
    @Column(name = "vendor_order_ref", length = 255)
    private String vendorOrderRef;

    @Enumerated(EnumType.STRING)
    @Column(name = "sub_order_status", nullable = false, length = 20)
    private SubOrderStatus subOrderStatus = SubOrderStatus.PENDING;

    // ----------------------------------------------------------------
    // Enum
    // ----------------------------------------------------------------

    public enum SubOrderStatus {
        PENDING, CONFIRMED, FAILED
    }
}
