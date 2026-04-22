package org.example.ordersservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

/**
 * Read-only view of the vendors table, owned and written by procurement-service.
 *
 * Only the columns orders-service needs for JPQL joins (analytics group-by
 * vendor name, history filters by vendor) are mapped. apiBaseUrl and other
 * internal columns are intentionally omitted.
 *
 * Hibernate is set to ddl-auto=none in orders-service so no DDL is attempted.
 */
@Entity
@Table(name = "vendors")
@Getter
@org.hibernate.annotations.Immutable
public class VendorView {

    @Id
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(name = "is_active", nullable = false)
    private boolean isActive;
}
