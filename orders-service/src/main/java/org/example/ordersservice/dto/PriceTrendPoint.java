package org.example.ordersservice.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * One point on the price-trend chart — a historical unit price paid for
 * a given catalog item from a given vendor on a given order date.
 *
 * Returned in ascending-time order so the UI can plot directly without
 * re-sorting.
 *
 * @param submittedAt timestamp of the order the price was observed on
 * @param vendorName  vendor display name (for chart series grouping)
 * @param unitPrice   price paid per unit on that order
 */
public record PriceTrendPoint(
        LocalDateTime submittedAt,
        String vendorName,
        BigDecimal unitPrice
) {}
