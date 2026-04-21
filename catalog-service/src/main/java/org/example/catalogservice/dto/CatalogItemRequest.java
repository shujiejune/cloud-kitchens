package org.example.catalogservice.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;

/**
 * Request body for:
 *   POST /api/v1/catalog/items        — create a new catalog item
 *   PUT  /api/v1/catalog/items/{id}   — replace an existing catalog item
 *
 * operatorId is NOT accepted from the client — it is always extracted
 * from the validated JWT by the service layer to prevent a tenant from
 * writing items into another tenant's catalog.
 */
public record CatalogItemRequest(

        @NotBlank(message = "Item name is required")
        @Size(max = 255, message = "Item name must be 255 characters or fewer")
        String name,

        /** Optional grouping, e.g. "Protein", "Dairy", "Packaging". */
        @Size(max = 100, message = "Category must be 100 characters or fewer")
        String category,

        @NotBlank(message = "Unit of measure is required")
        @Size(max = 50, message = "Unit must be 50 characters or fewer")
        String unit,

        @NotNull(message = "Preferred quantity is required")
        @DecimalMin(value = "0.01", message = "Preferred quantity must be greater than zero")
        @Digits(integer = 8, fraction = 2, message = "Preferred quantity format: up to 8 digits, 2 decimal places")
        BigDecimal preferredQty
) {}
