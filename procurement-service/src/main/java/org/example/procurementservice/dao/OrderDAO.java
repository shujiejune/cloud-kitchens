package org.example.procurementservice.dao;

import org.example.procurementservice.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository for the orders table (write side — owned by procurement-service).
 *
 * The read side (order history, analytics) lives in orders-service via OrderViewDAO.
 * This DAO is only used during plan submission and sub-order status updates.
 */
@Repository
public interface OrderDAO extends JpaRepository<Order, Long> {

    /**
     * Tenant-safe lookup — returns empty if the order exists but belongs
     * to a different operator.
     * Used when updating sub-order statuses to ensure a rogue client
     * cannot trigger status changes on another operator's order.
     */
    Optional<Order> findByIdAndOperatorId(Long id, Long operatorId);
}
