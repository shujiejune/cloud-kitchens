package org.example.ordersservice.dao;

import org.example.ordersservice.entity.OrderLineItemView;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Read-side repository for order_line_items (shared with procurement-service).
 *
 * In addition to simple line-item finders, this DAO exposes the aggregation
 * queries that power the analytics endpoints. Projections are used so each
 * query returns exactly the shape the service layer needs — no post-filtering
 * or extra joins.
 */
@Repository
public interface OrderLineItemViewDAO extends JpaRepository<OrderLineItemView, Long> {

    /** Loads every line item for a given order (used when hydrating OrderResponse). */
    List<OrderLineItemView> findAllByOrderId(Long orderId);

    // ----------------------------------------------------------------
    // Projections
    // ----------------------------------------------------------------

    /** Row shape for the per-vendor spend roll-up. */
    interface VendorSpendProjection {
        Long getVendorId();
        String getVendorName();
        BigDecimal getSpend();
        long getItemCount();
    }

    /** Row shape for the overall spend summary card. */
    interface SpendTotalsProjection {
        BigDecimal getTotalSpend();
        BigDecimal getTotalSavings();
        long getOrderCount();
    }

    /** Row shape for the price-trend chart: one point per historical order. */
    interface PriceTrendProjection {
        LocalDateTime getSubmittedAt();
        String getVendorName();
        BigDecimal getUnitPrice();
    }

    // ----------------------------------------------------------------
    // Aggregations
    // ----------------------------------------------------------------

    /**
     * Total spend and item count per vendor over the rolling window
     * (from = now - days). Only CONFIRMED sub-orders count so retries
     * of a FAILED line do not inflate totals.
     *
     * The join to VendorView lets us return the vendor display name in
     * the same row without a second query.
     */
    @Query("""
            SELECT li.vendorId           AS vendorId,
                   v.name                AS vendorName,
                   SUM(li.lineTotal)     AS spend,
                   COUNT(DISTINCT li.catalogItemId) AS itemCount
              FROM OrderLineItemView li
              JOIN li.order o
              JOIN VendorView v ON v.id = li.vendorId
             WHERE o.operatorId      = :operatorId
               AND o.submittedAt    >= :from
               AND li.subOrderStatus = 'CONFIRMED'
             GROUP BY li.vendorId, v.name
             ORDER BY spend DESC
            """)
    List<VendorSpendProjection> aggregateSpendByVendor(
            @Param("operatorId") Long operatorId,
            @Param("from") LocalDateTime from);

    /**
     * Scalar totals for the "spend summary" card: total spend, total
     * estimated savings, and number of orders in the window. COALESCE
     * keeps the return non-null when the window contains no rows.
     */
    @Query("""
            SELECT COALESCE(SUM(o.totalCost), 0)        AS totalSpend,
                   COALESCE(SUM(o.estimatedSavings), 0) AS totalSavings,
                   COUNT(o)                             AS orderCount
              FROM OrderView o
             WHERE o.operatorId  = :operatorId
               AND o.submittedAt >= :from
            """)
    SpendTotalsProjection aggregateTotals(
            @Param("operatorId") Long operatorId,
            @Param("from") LocalDateTime from);

    /**
     * Price points for a single catalog item across all vendors and all
     * historical orders in the window, ordered ascending so the UI can
     * render a line chart directly.
     */
    @Query("""
            SELECT o.submittedAt AS submittedAt,
                   v.name        AS vendorName,
                   li.unitPrice  AS unitPrice
              FROM OrderLineItemView li
              JOIN li.order o
              JOIN VendorView v ON v.id = li.vendorId
             WHERE o.operatorId     = :operatorId
               AND li.catalogItemId = :catalogItemId
               AND o.submittedAt   >= :from
             ORDER BY o.submittedAt ASC
            """)
    List<PriceTrendProjection> findPriceTrend(
            @Param("operatorId") Long operatorId,
            @Param("catalogItemId") Long catalogItemId,
            @Param("from") LocalDateTime from);
}
