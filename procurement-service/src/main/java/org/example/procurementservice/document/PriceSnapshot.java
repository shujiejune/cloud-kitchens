package org.example.procurementservice.document;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * One vendor's real-time price response for one catalog item,
 * stored in MongoDB for use during plan generation.
 *
 * MongoDB collection: price_snapshots
 *
 * TTL:  the ttlExpiry field is indexed with expireAfterSeconds=0,
 *       so MongoDB auto-deletes documents once ttlExpiry is in the past.
 *       Snapshots are set to expire 6 hours after queriedAt.
 *
 * Sharding key: { operatorId: 1, catalogItemId: 1 }
 *   — co-locates all price data for one operator's plan on the same shard,
 *     minimising cross-shard scatter during plan generation.
 *
 * Primary query pattern:
 *   Find the most recent snapshot for each (operatorId, catalogItemId, vendorId)
 *   where queriedAt >= now - 5 minutes (staleness check in ProcurementService).
 *   The compound index below covers this query fully.
 */
@Document(collection = "price_snapshots")
@CompoundIndexes({
        @CompoundIndex(
                name  = "idx_snapshot_lookup",
                def   = "{ 'operatorId': 1, 'catalogItemId': 1, 'vendorId': 1, 'queriedAt': -1 }",
                unique = false
        ),
        @CompoundIndex(
                name  = "idx_snapshot_id",
                def   = "{ 'snapshotId': 1 }",
                unique = true
        )
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PriceSnapshot {

    @Id
    private String id;  // MongoDB ObjectId (auto-generated)

    /**
     * Business-level idempotency key.
     * Format: snap_{yyyyMMdd}_{operatorId}_{catalogItemId}_{vendorId}
     * Used for upsert (replaceOne with upsert=true) to avoid duplicates
     * when the price fetch is retried.
     */
    @Field("snapshotId")
    private String snapshotId;

    @Field("operatorId")
    private Long operatorId;

    @Field("catalogItemId")
    private Long catalogItemId;

    @Field("vendorId")
    private Long vendorId;

    @Field("vendorName")
    private String vendorName;

    /** Timestamp when this Feign call was made. */
    @Field("queriedAt")
    private Instant queriedAt;

    /** The search term sent to the vendor API, e.g. "chicken breast 10lb". */
    @Field("searchQuery")
    private String searchQuery;

    /**
     * Raw results returned by the vendor — an array of product hits.
     * Schema-flexible: each vendor returns different fields, all stored here.
     */
    @Field("results")
    private List<VendorProductResult> results;

    /**
     * The lowest unit price among all results[].unitPrice for this snapshot.
     * Pre-computed so the optimization engine can read bestPrice directly
     * without iterating the full results array.
     */
    @Field("bestPrice")
    private BigDecimal bestPrice;

    /**
     * TTL field.  MongoDB deletes the document when CURRENT_TIME > ttlExpiry.
     * Set to queriedAt + 6 hours in ProcurementService.
     * The TTL index must be created on this field with expireAfterSeconds = 0.
     */
    @Indexed(expireAfterSeconds = 0)
    @Field("ttlExpiry")
    private Instant ttlExpiry;

    // ----------------------------------------------------------------
    // Embedded document — one product hit from the vendor API
    // ----------------------------------------------------------------

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class VendorProductResult {

        /** Vendor's own SKU / ASIN / UPC. */
        private String vendorSku;

        /** Full product title as returned by the vendor. */
        private String productName;

        /** Price per unit (per the unit of measure in the catalog item). */
        private BigDecimal unitPrice;

        /** Unit string as returned by the vendor (may differ from catalog unit). */
        private String unit;

        private boolean inStock;

        /** Direct product URL for display in the dashboard. */
        private String url;
    }
}
