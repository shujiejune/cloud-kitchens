package org.example.procurementservice.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.util.List;

/**
 * Request body for POST /api/v1/procurement/plan/{planId}/submit.
 *
 * The client sends back the final, operator-approved line items
 * (including any vendor overrides they applied).  The service
 * validates this matches a coherent plan before persisting the Order
 * and fanning out sub-orders to vendor APIs.
 *
 * Why resend line items instead of just reading them from the server?
 * The plan is ephemeral (stored only in MongoDB snapshots and the
 * client's session state).  Resending the confirmed line items makes
 * the submission idempotent and self-contained — the server doesn't
 * need to reconstruct the plan from Redis or a DB plan record.
 */
public record SubmitOrderRequest(

        @NotEmpty(message = "At least one line item is required")
        @Valid
        List<LineItemRequest> lineItems
) {

    /**
     * One confirmed line item from the purchase plan.
     *
     * @param catalogItemId  FK to catalog_items.id
     * @param vendorId       FK to vendors.id (may differ from the optimized assignment
     *                       if the operator applied an override)
     * @param quantity       Quantity to order (must match catalog item's preferredQty
     *                       or a coordinator-adjusted value)
     * @param unitPrice      Price per unit from the price snapshot (used to compute lineTotal
     *                       server-side; server re-validates against the snapshot)
     */
    public record LineItemRequest(

            @NotNull(message = "Catalog item ID is required")
            Long catalogItemId,

            @NotNull(message = "Vendor ID is required")
            Long vendorId,

            @NotNull(message = "Quantity is required")
            @DecimalMin(value = "0.01", message = "Quantity must be greater than zero")
            BigDecimal quantity,

            @NotNull(message = "Unit price is required")
            @DecimalMin(value = "0.00", inclusive = false, message = "Unit price must be positive")
            BigDecimal unitPrice
    ) {}
}
