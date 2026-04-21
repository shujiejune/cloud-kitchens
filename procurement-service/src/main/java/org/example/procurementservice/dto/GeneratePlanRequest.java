package org.example.procurementservice.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Request body for POST /api/v1/procurement/plan.
 *
 * The service fetches prices for every catalogItemId in the list,
 * runs the optimization engine, and returns a PurchasePlanResponse
 * without persisting anything to MySQL yet.
 *
 * refreshPrices:
 *   true  → always re-fetch from vendor APIs (ignores cached snapshots).
 *   false → use any snapshot from the last 5 minutes if available,
 *            only fetching from the vendor when no valid cache exists.
 *           Defaults to false to reduce vendor API quota consumption.
 */
public record GeneratePlanRequest(

        @NotEmpty(message = "At least one catalog item ID is required")
        @Size(max = 200, message = "Cannot request a plan for more than 200 items at once")
        List<Long> catalogItemIds,

        boolean refreshPrices
) {
    /** Compact constructor applies defaulting so callers can omit refreshPrices. */
    public GeneratePlanRequest {
        // refreshPrices defaults to false (primitive boolean default)
    }
}
