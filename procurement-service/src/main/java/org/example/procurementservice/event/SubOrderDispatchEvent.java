package org.example.procurementservice.event;

import java.math.BigDecimal;

/**
 * Published to the {@code sub-order-dispatch} Kafka topic — one message per
 * OrderLineItem — when a plan is submitted. The consumer calls the vendor API
 * and updates the line item status in MySQL.
 *
 * Partitioned by vendorName so all line items for the same vendor are processed
 * sequentially, consistent with the per-vendor rate limiter contract.
 */
public record SubOrderDispatchEvent(
        Long orderId,
        Long lineItemId,
        Long operatorId,
        Long vendorId,
        String vendorName,
        Long catalogItemId,
        BigDecimal quantity,
        BigDecimal unitPrice
) {}
