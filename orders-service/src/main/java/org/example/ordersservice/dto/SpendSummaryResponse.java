package org.example.ordersservice.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Response for GET /api/v1/analytics/spend-summary.
 *
 * Drives the analytics dashboard header cards:
 *   "Total spend (last 30 days)"  → totalSpend
 *   "Total savings (last 30 days)" → totalSavings
 *   "Orders placed"               → orderCount
 *
 * vendorBreakdown drives a small pie/bar chart below the header.
 */
public record SpendSummaryResponse(
        BigDecimal totalSpend,
        BigDecimal totalSavings,
        long orderCount,
        String periodLabel,             // e.g. "Last 30 days", "Last 90 days"
        List<VendorSpend> vendorBreakdown
) {

    /**
     * Spend attributed to one vendor within the queried period.
     *
     * @param vendorId    FK to vendors.id
     * @param vendorName  Display name
     * @param spend       Sum of line_total for this vendor's confirmed sub-orders
     * @param itemCount   Number of distinct catalog items ordered from this vendor
     */
    public record VendorSpend(
            Long vendorId,
            String vendorName,
            BigDecimal spend,
            long itemCount
    ) {}
}
