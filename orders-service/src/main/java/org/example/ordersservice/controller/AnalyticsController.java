package org.example.ordersservice.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.example.ordersservice.dto.PriceTrendPoint;
import org.example.ordersservice.dto.SpendSummaryResponse;
import org.example.ordersservice.service.AnalyticsService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST controller for the analytics dashboard.
 *
 * Routed through the gateway via the `orders-analytics` route on
 * /api/v1/analytics/**. All endpoints require a valid JWT; results are
 * tenant-scoped by operatorId extracted from the SecurityContext.
 */
@RestController
@RequestMapping("/api/v1/analytics")
@RequiredArgsConstructor
@Tag(name = "Analytics", description = "Spend and price-trend analytics")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    // ----------------------------------------------------------------
    // GET /api/v1/analytics/spend-summary
    // ----------------------------------------------------------------

    @GetMapping("/spend-summary")
    @Operation(summary = "Total spend, savings, order count, and per-vendor breakdown")
    public SpendSummaryResponse getSpendSummary(
            @RequestParam(defaultValue = "30") int days,
            @AuthenticationPrincipal Long operatorId) {
        return analyticsService.getSpendSummary(operatorId, days);
    }

    // ----------------------------------------------------------------
    // GET /api/v1/analytics/spend-by-vendor
    // ----------------------------------------------------------------

    @GetMapping("/spend-by-vendor")
    @Operation(summary = "Per-vendor spend roll-up (without the scalar totals)")
    public List<SpendSummaryResponse.VendorSpend> getSpendByVendor(
            @RequestParam(defaultValue = "30") int days,
            @AuthenticationPrincipal Long operatorId) {
        return analyticsService.getSpendByVendor(operatorId, days);
    }

    // ----------------------------------------------------------------
    // GET /api/v1/analytics/price-trends/{catalogItemId}
    // ----------------------------------------------------------------

    @GetMapping("/price-trends/{catalogItemId}")
    @Operation(summary = "Historical unit price series for a single catalog item")
    public List<PriceTrendPoint> getPriceTrends(
            @PathVariable Long catalogItemId,
            @RequestParam(defaultValue = "90") int days,
            @AuthenticationPrincipal Long operatorId) {
        return analyticsService.getPriceTrends(operatorId, catalogItemId, days);
    }
}
