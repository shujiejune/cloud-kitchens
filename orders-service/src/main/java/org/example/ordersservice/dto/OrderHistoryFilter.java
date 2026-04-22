package org.example.ordersservice.dto;

import java.time.LocalDateTime;

/**
 * Bundled filter parameters for GET /api/v1/orders.
 *
 * All fields are optional — any subset can be supplied by the client,
 * and the DAO treats null fields as "no filter". The controller builds
 * one of these from its individual @RequestParam values so the service
 * signature stays stable if more filters are added later.
 *
 * @param fromDate      inclusive lower bound on Order.submittedAt
 * @param toDate        inclusive upper bound on Order.submittedAt
 * @param vendorId      restrict to orders containing at least one line with this vendor
 * @param catalogItemId restrict to orders containing at least one line with this catalog item
 */
public record OrderHistoryFilter(
        LocalDateTime fromDate,
        LocalDateTime toDate,
        Long vendorId,
        Long catalogItemId
) {}
