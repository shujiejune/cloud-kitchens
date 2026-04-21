package org.example.procurementservice.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Request body for PUT /api/v1/procurement/plan/{planId}/override.
 *
 * The operator can switch one line item to a different vendor before
 * submitting the plan.  The service re-computes lineTotal using the
 * overridden vendor's price from the existing price snapshot.
 *
 * planId is taken from the URL path parameter, not this body.
 */
public record OverrideVendorRequest(

        @NotNull(message = "Catalog item ID is required")
        Long catalogItemId,

        @NotNull(message = "Vendor ID to override to is required")
        Long vendorId
) {}
