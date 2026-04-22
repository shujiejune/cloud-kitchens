package org.example.ordersservice.service;

import org.example.ordersservice.dto.PriceTrendPoint;
import org.example.ordersservice.dto.SpendSummaryResponse;

import java.util.List;

/**
 * Contract for the analytics dashboard endpoints.
 *
 * `days` is the rolling window length (today back to now - days). All
 * aggregations exclude FAILED sub-orders so retries of a failed line
 * do not inflate totals.
 */
public interface AnalyticsService {

    /**
     * Top-level summary: total spend, total savings, order count, and
     * a per-vendor breakdown for the pie/bar chart.
     *
     * @throws org.example.ordersservice.exception.InvalidAnalyticsRangeException days not in [1, 365]
     */
    SpendSummaryResponse getSpendSummary(Long operatorId, int days);

    /**
     * Per-vendor spend roll-up, ordered by spend descending. Used when
     * the UI needs only the breakdown (e.g. a dedicated vendor table)
     * without the scalar totals.
     *
     * @throws org.example.ordersservice.exception.InvalidAnalyticsRangeException days not in [1, 365]
     */
    List<SpendSummaryResponse.VendorSpend> getSpendByVendor(Long operatorId, int days);

    /**
     * Historical unit-price series for a single catalog item across all
     * vendors in the window, ordered ascending by order date.
     *
     * @throws org.example.ordersservice.exception.InvalidAnalyticsRangeException days not in [1, 365]
     */
    List<PriceTrendPoint> getPriceTrends(Long operatorId, Long catalogItemId, int days);
}
