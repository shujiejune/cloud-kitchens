package org.example.ordersservice.dto;

import java.math.BigDecimal;

/**
 * Response for procurement-service's internal retry endpoint, consumed
 * by orders-service via ProcurementRetryClient Feign.
 *
 * Shape intentionally mirrors procurement-service's LineItemStatusResponse
 * (same field names in the same order) so Feign / Jackson can deserialize
 * the JSON straight into this record without any cross-module dependency.
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
public record LineItemStatusResponse(
        Long lineItemId,
        Long catalogItemId,
        Long vendorId,
        String vendorName,
        BigDecimal quantity,
        BigDecimal lineTotal,
        String subOrderStatus,
        String vendorOrderRef,
        String failureReason
) {}
