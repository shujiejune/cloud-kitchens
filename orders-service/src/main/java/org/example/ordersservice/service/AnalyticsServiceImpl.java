package org.example.ordersservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.ordersservice.dao.OrderLineItemViewDAO;
import org.example.ordersservice.dto.PriceTrendPoint;
import org.example.ordersservice.dto.SpendSummaryResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Primary implementation of AnalyticsService.
 *
 * Aggregation work is delegated to projection-returning JPQL queries on
 * OrderLineItemViewDAO so Hibernate generates single-trip SQL — no
 * in-memory summation on fetched entities.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AnalyticsServiceImpl implements AnalyticsService {

    private final OrderLineItemViewDAO orderLineItemViewDAO;

    // ----------------------------------------------------------------
    // Public API
    // ----------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public SpendSummaryResponse getSpendSummary(Long operatorId, int days) {
        throw new UnsupportedOperationException("TODO: implement getSpendSummary");
    }

    @Override
    @Transactional(readOnly = true)
    public List<SpendSummaryResponse.VendorSpend> getSpendByVendor(Long operatorId, int days) {
        throw new UnsupportedOperationException("TODO: implement getSpendByVendor");
    }

    @Override
    @Transactional(readOnly = true)
    public List<PriceTrendPoint> getPriceTrends(Long operatorId, Long catalogItemId, int days) {
        throw new UnsupportedOperationException("TODO: implement getPriceTrends");
    }

    // ----------------------------------------------------------------
    // Private helpers
    // ----------------------------------------------------------------

    /** Renders the `days` window into a human label for the dashboard header. */
    private String periodLabel(int days) {
        throw new UnsupportedOperationException("TODO: implement periodLabel");
    }

    /** Projects a VendorSpendProjection row into the public VendorSpend DTO. */
    private SpendSummaryResponse.VendorSpend toVendorSpend(
            OrderLineItemViewDAO.VendorSpendProjection projection) {
        throw new UnsupportedOperationException("TODO: map VendorSpendProjection -> VendorSpend");
    }

    /** Projects a PriceTrendProjection row into the public PriceTrendPoint DTO. */
    private PriceTrendPoint toTrendPoint(
            OrderLineItemViewDAO.PriceTrendProjection projection) {
        throw new UnsupportedOperationException("TODO: map PriceTrendProjection -> PriceTrendPoint");
    }

    /**
     * Enforces the [1, 365] bound on the `days` query parameter before
     * building the "from" Instant handed to the DAO.
     */
    private void validateDays(int days) {
        throw new UnsupportedOperationException("TODO: implement validateDays");
    }
}
