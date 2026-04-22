package org.example.ordersservice.dao;

import org.example.ordersservice.entity.OrderView;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Read-side repository for the orders table (shared with procurement-service).
 *
 * All finders are tenant-scoped by operatorId. The idx_orders_operator_submitted
 * MySQL index (owned by procurement-service) covers the listing and filtered
 * search queries below.
 */
@Repository
public interface OrderViewDAO extends JpaRepository<OrderView, Long> {

    /**
     * Unfiltered, paginated listing for the order history screen.
     * Used when no filters are supplied on GET /api/v1/orders.
     */
    Page<OrderView> findAllByOperatorId(Long operatorId, Pageable pageable);

    /**
     * Combined optional-filter search — any subset of filters may be null,
     * in which case that clause is a no-op. Driven by GET /api/v1/orders
     * with any mix of fromDate / toDate / vendorId / catalogItemId query params.
     *
     * DISTINCT is required because the LEFT JOIN to line items would otherwise
     * produce one row per matching line.
     */
    @Query("""
            SELECT DISTINCT o FROM OrderView o
              LEFT JOIN o.lineItems li
             WHERE o.operatorId = :operatorId
               AND (:from      IS NULL OR o.submittedAt   >= :from)
               AND (:to        IS NULL OR o.submittedAt   <= :to)
               AND (:vendorId  IS NULL OR li.vendorId      = :vendorId)
               AND (:catItemId IS NULL OR li.catalogItemId = :catItemId)
            """)
    Page<OrderView> search(
            @Param("operatorId") Long operatorId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            @Param("vendorId") Long vendorId,
            @Param("catItemId") Long catalogItemId,
            Pageable pageable);

    /**
     * Tenant-safe single-order lookup. Returns empty when id is unknown
     * OR belongs to a different operator.
     */
    Optional<OrderView> findByIdAndOperatorId(Long id, Long operatorId);
}
