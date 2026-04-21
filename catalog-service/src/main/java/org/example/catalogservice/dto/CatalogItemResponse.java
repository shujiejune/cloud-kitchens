package org.example.catalogservice.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Response body for all catalog item endpoints:
 *   GET  /api/v1/catalog/items
 *   GET  /api/v1/catalog/items/{id}
 *   POST /api/v1/catalog/items
 *   PUT  /api/v1/catalog/items/{id}
 *
 * operatorId is included so the React dashboard can assert it matches
 * the current user without an extra round-trip.
 */
public record CatalogItemResponse(
        Long id,
        Long operatorId,
        String name,
        String category,
        String unit,
        BigDecimal preferredQty,
        LocalDateTime createdAt
) {}
