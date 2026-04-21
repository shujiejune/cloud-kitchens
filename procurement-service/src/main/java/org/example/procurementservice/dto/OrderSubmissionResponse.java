package org.example.procurementservice.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Response for POST /api/v1/procurement/plan/{planId}/submit.
 *
 * Returns immediately after all synchronous fan-out calls complete
 * (or fail).  The operator sees per-line-item sub-order status so they
 * know which vendor confirmations succeeded and which need a retry.
 *
 * HTTP status:
 *   201 Created           — all sub-orders CONFIRMED.
 *   207 Multi-Status      — at least one sub-order FAILED; order still persisted.
 */
public record OrderSubmissionResponse(
        Long orderId,
        String orderStatus,           // SUBMITTED | PARTIAL_FAILURE | COMPLETE
        BigDecimal totalCost,
        BigDecimal estimatedSavings,
        LocalDateTime submittedAt,
        List<LineItemStatus> lineItems
) {

    /**
     * Per-line-item result of the vendor fan-out.
     *
     * @param lineItemId       PK of the order_line_items row
     * @param catalogItemId    FK to catalog_items.id
     * @param vendorId         FK to vendors.id
     * @param vendorName       Display name (denormalized for convenience)
     * @param quantity         Ordered quantity
     * @param lineTotal        quantity × unitPrice
     * @param subOrderStatus   PENDING | CONFIRMED | FAILED
     * @param vendorOrderRef   Vendor's confirmation reference; null while PENDING or FAILED
     * @param failureReason    Human-readable error from the vendor API; null when not FAILED
     */
    public record LineItemStatus(
            Long lineItemId,
            Long catalogItemId,
            Long vendorId,
            String vendorName,
            java.math.BigDecimal quantity,
            BigDecimal lineTotal,
            String subOrderStatus,
            String vendorOrderRef,
            String failureReason
    ) {}
}
