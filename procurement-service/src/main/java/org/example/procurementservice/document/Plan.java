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
 * A generated purchase plan persisted for the lifetime of the operator's browser
 * session (and a bit beyond — 6h TTL).
 *
 * MongoDB collection: plans
 *
 * TTL:  ttlExpiry is indexed with expireAfterSeconds=0; MongoDB deletes the doc
 *       once ttlExpiry is in the past.  Set to generatedAt + 6 hours.
 *
 * Lifecycle:
 *   POST /procurement/plan           → insert (new _id is returned as planId)
 *   GET  /procurement/plan/{planId}  → find
 *   PUT  /procurement/plan/{id}/override → update items, recompute subtotals, save
 *   POST /procurement/plan/{id}/submit   → read, validate against SubmitOrderRequest,
 *                                           then delete (plan can't be replayed)
 *
 * Embedded shape mirrors PurchasePlanResponse.PlanLineItem / VendorSubtotal so
 * `toResponse(Plan)` is a straight field copy.
 */
@Document(collection = "plans")
@CompoundIndexes({
        @CompoundIndex(
                name  = "idx_plan_operator_generated",
                def   = "{ 'operatorId': 1, 'generatedAt': -1 }",
                unique = false
        )
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Plan {

    /** Mongo ObjectId (auto-generated) — exposed to clients as planId. */
    @Id
    private String id;

    @Indexed
    @Field("operatorId")
    private Long operatorId;

    @Field("items")
    private List<PlanItem> items;

    @Field("totalCost")
    private BigDecimal totalCost;

    @Field("estimatedSavings")
    private BigDecimal estimatedSavings;

    @Field("vendorSubtotals")
    private List<VendorSubtotalSnapshot> vendorSubtotals;

    /** Names of vendors that failed during price fetch; surfaced in the UI. */
    @Field("vendorWarnings")
    private List<String> vendorWarnings;

    @Field("generatedAt")
    private Instant generatedAt;

    /** TTL field — generatedAt + 6h.  MongoDB deletes on expiry. */
    @Indexed(expireAfterSeconds = 0)
    @Field("ttlExpiry")
    private Instant ttlExpiry;

    // ----------------------------------------------------------------
    // Embedded: one line of the plan
    // ----------------------------------------------------------------

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class PlanItem {
        private Long catalogItemId;
        private String itemName;
        private String unit;
        private BigDecimal quantity;
        private Long vendorId;
        private String vendorName;
        private String vendorSku;
        private String productName;
        private BigDecimal unitPrice;
        private BigDecimal lineTotal;
        private String vendorProductUrl;

        /** True if the operator manually reassigned this line to a non-cheapest vendor. */
        private boolean overridden;
    }

    // ----------------------------------------------------------------
    // Embedded: per-vendor subtotal snapshot
    // ----------------------------------------------------------------

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class VendorSubtotalSnapshot {
        private Long vendorId;
        private String vendorName;
        private int itemCount;
        private BigDecimal subtotal;
    }
}
