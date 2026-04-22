package org.example.procurementservice.dto;

import java.math.BigDecimal;

/**
 * Response for POST /api/v1/procurement/orders/{orderId}/retry/{lineItemId},
 * called internally by orders-service over Feign to retry a single FAILED
 * sub-order against its vendor.
 *
 * Shape mirrors OrderSubmissionResponse.LineItemStatus so the frontend
 * can render a retry result the same way it renders initial submission results.
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
