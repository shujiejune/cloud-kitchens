package org.example.ordersservice.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Response for:
 *   GET /api/v1/orders/{id}          — full order detail
 *   GET /api/v1/orders/{id}/status   — same shape; client uses it for live polling
 *
 * Returned by orders-service, which reads from the same MySQL tables
 * that procurement-service writes.
 */
public record OrderResponse(
        Long id,
        Long operatorId,
        String status,
        BigDecimal totalCost,
        BigDecimal estimatedSavings,
        LocalDateTime submittedAt,
        LocalDateTime createdAt,
        List<LineItemResponse> lineItems
) {

    /**
     * One persisted order line item.
     *
     * vendorName is denormalized here (joined from vendors table in the
     * JPQL query) so the client doesn't need a second round-trip to look
     * up vendor names.
     */
    public record LineItemResponse(
            Long id,
            Long catalogItemId,
            String catalogItemName,     // denormalized from Feign call to catalog-service
            Long vendorId,
            String vendorName,          // denormalized from vendors table JOIN
            BigDecimal quantity,
            BigDecimal unitPrice,
            BigDecimal lineTotal,
            String subOrderStatus,
            String vendorOrderRef
    ) {}
}
