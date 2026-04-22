package org.example.procurementservice.dao;

import org.example.procurementservice.entity.Vendor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for the vendors table.
 *
 * Vendors are system-level data (not tenant-scoped) — there are only
 * three rows (Amazon, Walmart, Kroger) seeded at deploy time.
 * Operators cannot create or modify vendor records.
 */
@Repository
public interface VendorDAO extends JpaRepository<Vendor, Long> {

    /**
     * Returns all active vendors.
     * Used by ProcurementService to decide which vendors to query
     * during plan generation — inactive vendors are skipped entirely.
     */
    List<Vendor> findAllByIsActiveTrue();

    /** Lookup by display name — used in circuit breaker naming. */
    Optional<Vendor> findByName(String name);
}
