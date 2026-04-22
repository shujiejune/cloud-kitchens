package org.example.procurementservice.dao.mongo;

import org.example.procurementservice.document.PriceSnapshot;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * MongoDB repository for the price_snapshots collection.
 *
 * Spring Data MongoDB generates the implementation at runtime,
 * just like Spring Data JPA does for relational repositories.
 *
 * The compound index { operatorId, catalogItemId, vendorId, queriedAt }
 * (declared on PriceSnapshot via @CompoundIndex) ensures all queries
 * below are covered — no collection scans.
 */
@Repository
public interface PriceSnapshotDAO extends MongoRepository<PriceSnapshot, String> {

    /**
     * Finds the most recent snapshot for a specific (operator, item, vendor) triple.
     *
     * Used by ProcurementService.fetchOrCachePrice() to check whether a
     * fresh snapshot already exists before making a vendor API call.
     * "Fresh" means queriedAt is within the last 5 minutes
     * (staleness threshold enforced by the caller, not this query).
     */
    Optional<PriceSnapshot> findTopByOperatorIdAndCatalogItemIdAndVendorIdOrderByQueriedAtDesc(
            Long operatorId, Long catalogItemId, Long vendorId);

    /**
     * Fetches all snapshots for a given operator and set of catalog items
     * that were queried after the staleness cutoff.
     *
     * Called at the start of plan generation to load any warm cache entries
     * before deciding which items need a fresh vendor API call.
     */
    @Query("{ 'operatorId': ?0, 'catalogItemId': { $in: ?1 }, 'queriedAt': { $gte: ?2 } }")
    List<PriceSnapshot> findFreshSnapshots(
            Long operatorId, List<Long> catalogItemIds, Instant freshAfter);

    /**
     * Deletes all snapshots for an operator and item — used in tests
     * and in the "force refresh" path where the operator explicitly
     * requests GeneratePlanRequest.refreshPrices = true.
     */
    void deleteAllByOperatorIdAndCatalogItemId(Long operatorId, Long catalogItemId);
}
