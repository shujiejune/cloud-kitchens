package org.example.procurementservice.dao;

import org.example.procurementservice.entity.OrderLineItem;
import org.example.procurementservice.entity.OrderLineItem.SubOrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Repository for the order_line_items table (write side).
 */
@Repository
public interface OrderLineItemDAO extends JpaRepository<OrderLineItem, Long> {

    /** Returns all line items for an order — used during fan-out. */
    List<OrderLineItem> findAllByOrderId(Long orderId);

    /**
     * Tenant-safe single line item lookup.
     * Joins through the parent order to verify operatorId ownership.
     */
    @Query("""
            SELECT li FROM OrderLineItem li
            JOIN li.order o
            WHERE li.id = :lineItemId
              AND o.operatorId = :operatorId
            """)
    Optional<OrderLineItem> findByIdAndOperatorId(
            @Param("lineItemId") Long lineItemId,
            @Param("operatorId") Long operatorId);

    /**
     * Bulk status update — used after the vendor fan-out completes to
     * mark all CONFIRMED line items in a single JPQL UPDATE rather than
     * issuing one UPDATE per row.
     */
    @Modifying
    @Transactional
    @Query("""
            UPDATE OrderLineItem li
            SET li.subOrderStatus = :status,
                li.vendorOrderRef = :vendorOrderRef
            WHERE li.id = :lineItemId
            """)
    int updateStatusAndRef(
            @Param("lineItemId") Long lineItemId,
            @Param("status") SubOrderStatus status,
            @Param("vendorOrderRef") String vendorOrderRef);

    /**
     * Counts FAILED line items for an order — used by ProcurementService
     * to decide whether to set order status = PARTIAL_FAILURE or COMPLETE.
     */
    long countByOrderIdAndSubOrderStatus(Long orderId, SubOrderStatus status);
}
