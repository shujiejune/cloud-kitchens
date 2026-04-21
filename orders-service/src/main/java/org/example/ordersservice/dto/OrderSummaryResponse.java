package org.example.ordersservice.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Lightweight order summary used in paginated list responses.
 *
 * Returned by GET /api/v1/orders (the order history list).
 * Does not include line items — those are fetched on demand via
 * GET /api/v1/orders/{id} to keep list queries fast.
 *
 * lineItemCount lets the UI display "3 items" without loading the full detail.
 */
public record OrderSummaryResponse(
        Long id,
        String status,
        BigDecimal totalCost,
        BigDecimal estimatedSavings,
        LocalDateTime submittedAt,
        int lineItemCount
) {}
