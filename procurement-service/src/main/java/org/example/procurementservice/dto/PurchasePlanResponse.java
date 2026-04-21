package org.example.procurementservice.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Response for POST /api/v1/procurement/plan and GET /api/v1/procurement/plan/{planId}.
 *
 * A plan is not persisted in MySQL — it lives only in the client's state
 * and is reconstructable from the price_snapshots in MongoDB.
 * planId is a composite key string (not a DB PK) that the client passes
 * back in the submit endpoint URL.
 *
 * vendorWarnings lists any vendor APIs that were unavailable during
 * price fetch so the operator knows the plan may not be globally optimal.
 *
 * generatedAt lets the UI show "prices as of X minutes ago" and warn
 * the operator if the plan is stale before they submit.
 */
public record PurchasePlanResponse(

        /** Opaque identifier used in PUT /plan/{planId}/override and POST /plan/{planId}/submit. */
        String planId,

        /** Per-item vendor assignment + price detail. */
        List<PlanLineItem> items,

        /** Sum of all lineTotal values across items. */
        BigDecimal totalCost,

        /**
         * totalCost subtracted from the single-cheapest-vendor baseline.
         * Displayed as "You save $X vs. buying everything from one vendor."
         */
        BigDecimal estimatedSavings,

        /** Per-vendor subtotals for the "orders to be placed" summary. */
        List<VendorSubtotal> vendorSubtotals,

        /**
         * Vendor names that could not be reached during price aggregation.
         * Empty list means all vendors responded successfully.
         */
        List<String> vendorWarnings,

        /** Timestamp when price snapshots were last fetched. */
        Instant generatedAt
) {

    // ----------------------------------------------------------------
    // Nested response types
    // ----------------------------------------------------------------

    /**
     * One line in the purchase plan — one catalog item assigned to one vendor.
     *
     * @param catalogItemId   FK to catalog_items.id
     * @param itemName        Denormalized name (avoids a separate catalog lookup on the client)
     * @param unit            Unit of measure (e.g. "lb")
     * @param quantity        How much to order (from catalog item's preferredQty)
     * @param vendorId        Assigned vendor's DB id
     * @param vendorName      Display name (e.g. "Amazon")
     * @param vendorSku       The specific SKU the vendor returned for this item
     * @param productName     Full product title from the vendor's catalog
     * @param unitPrice       Price per unit at query time
     * @param lineTotal       quantity × unitPrice
     * @param vendorProductUrl Direct link to the product page (for operator reference)
     * @param overridden      True if the operator manually changed the vendor assignment
     */
    public record PlanLineItem(
            Long catalogItemId,
            String itemName,
            String unit,
            java.math.BigDecimal quantity,
            Long vendorId,
            String vendorName,
            String vendorSku,
            String productName,
            BigDecimal unitPrice,
            BigDecimal lineTotal,
            String vendorProductUrl,
            boolean overridden
    ) {}

    /**
     * Rollup of total cost per vendor — drives the "summary by vendor" section
     * of the dashboard and the fan-out ordering logic.
     *
     * @param vendorId     FK to vendors.id
     * @param vendorName   Display name
     * @param itemCount    Number of catalog items assigned to this vendor
     * @param subtotal     Sum of lineTotals for this vendor's items
     */
    public record VendorSubtotal(
            Long vendorId,
            String vendorName,
            int itemCount,
            BigDecimal subtotal
    ) {}
}
