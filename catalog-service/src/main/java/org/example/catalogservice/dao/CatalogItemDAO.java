package org.example.catalogservice.dao;

import org.example.catalogservice.entity.CatalogItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for the catalog_items table.
 *
 * Every query is scoped by operatorId to enforce tenant isolation.
 * The service layer always passes the operatorId extracted from the
 * JWT — never a value supplied by the client in the request body.
 */
@Repository
public interface CatalogItemDAO extends JpaRepository<CatalogItem, Long> {

    /**
     * Returns all catalog items belonging to the operator.
     * Used by GET /api/v1/catalog/items (full list, no pagination needed
     * at current scale; add Pageable overload when list grows large).
     */
    List<CatalogItem> findAllByOperatorId(Long operatorId);

    /**
     * Tenant-safe single-item lookup.
     * Returns empty if the item exists but belongs to a different operator —
     * the service treats this identically to "not found" to avoid leaking
     * the existence of another tenant's data.
     */
    Optional<CatalogItem> findByIdAndOperatorId(Long id, Long operatorId);

    /**
     * Tenant-safe existence check — used by the delete endpoint before
     * calling deleteById to produce a proper 404 instead of a silent no-op.
     */
    boolean existsByIdAndOperatorId(Long id, Long operatorId);

    /**
     * Fetches only the items whose IDs are in the supplied set,
     * always scoped to the operator.
     * Called by Procurement Service (via Feign) when it needs catalog
     * metadata (name, unit, preferredQty) for the items in a plan request.
     */
    List<CatalogItem> findByIdInAndOperatorId(List<Long> ids, Long operatorId);

    /**
     * Counts items per operator — used for lightweight analytics.
     */
    long countByOperatorId(Long operatorId);
}
