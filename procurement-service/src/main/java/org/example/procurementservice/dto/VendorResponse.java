package org.example.procurementservice.dto;

/**
 * Lightweight vendor summary used in plan and order responses
 * wherever the full Vendor entity is not needed.
 *
 * apiBaseUrl is intentionally excluded — it is an internal
 * implementation detail that must not be exposed to clients.
 */
public record VendorResponse(
        Long id,
        String name,
        boolean isActive
) {}
