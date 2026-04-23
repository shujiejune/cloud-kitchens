package org.example.ordersservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.ordersservice.dao.OrderLineItemViewDAO;
import org.example.ordersservice.dto.PriceTrendPoint;
import org.example.ordersservice.dto.SpendSummaryResponse;
import org.example.ordersservice.exception.InvalidAnalyticsRangeException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
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
        validateDays(days);
        LocalDateTime from = LocalDateTime.now().minusDays(days);
        OrderLineItemViewDAO.SpendTotalsProjection totals =
                orderLineItemViewDAO.aggregateTotals(operatorId, from);
        List<SpendSummaryResponse.VendorSpend> breakdown = getSpendByVendor(operatorId, days);
        return new SpendSummaryResponse(
                totals.getTotalSpend(),
                totals.getTotalSavings(),
                totals.getOrderCount(),
                periodLabel(days),
                breakdown);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SpendSummaryResponse.VendorSpend> getSpendByVendor(Long operatorId, int days) {
        validateDays(days);
        LocalDateTime from = LocalDateTime.now().minusDays(days);
        return orderLineItemViewDAO.aggregateSpendByVendor(operatorId, from)
                .stream().map(this::toVendorSpend).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<PriceTrendPoint> getPriceTrends(Long operatorId, Long catalogItemId, int days) {
        validateDays(days);
        LocalDateTime from = LocalDateTime.now().minusDays(days);
        return orderLineItemViewDAO.findPriceTrend(operatorId, catalogItemId, from)
                .stream().map(this::toTrendPoint).toList();
    }

    // ----------------------------------------------------------------
    // Private helpers
    // ----------------------------------------------------------------

    /** Renders the `days` window into a human label for the dashboard header. */
    private String periodLabel(int days) {
        return "Last " + days + (days == 1 ? " day" : " days");
    }

    /** Projects a VendorSpendProjection row into the public VendorSpend DTO. */
    private SpendSummaryResponse.VendorSpend toVendorSpend(
            OrderLineItemViewDAO.VendorSpendProjection p) {
        return new SpendSummaryResponse.VendorSpend(
                p.getVendorId(), p.getVendorName(), p.getSpend(), p.getItemCount());
    }

    /** Projects a PriceTrendProjection row into the public PriceTrendPoint DTO. */
    private PriceTrendPoint toTrendPoint(OrderLineItemViewDAO.PriceTrendProjection p) {
        return new PriceTrendPoint(p.getSubmittedAt(), p.getVendorName(), p.getUnitPrice());
    }

    /**
     * Enforces the [1, 365] bound on the `days` query parameter before
     * building the "from" Instant handed to the DAO.
     */
    private void validateDays(int days) {
        if (days < 1 || days > 365) {
            throw new InvalidAnalyticsRangeException(
                    "days must be between 1 and 365, got: " + days);
        }
    }
}
