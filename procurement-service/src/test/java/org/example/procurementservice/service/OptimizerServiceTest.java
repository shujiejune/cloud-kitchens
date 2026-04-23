package org.example.procurementservice.service;

import org.example.procurementservice.document.Plan;
import org.example.procurementservice.document.PriceSnapshot;
import org.example.procurementservice.document.PriceSnapshot.VendorProductResult;
import org.example.procurementservice.service.OptimizerService.ItemMeta;
import org.example.procurementservice.service.OptimizerService.OptimizeResult;
import org.example.procurementservice.service.OptimizerService.VendorMeta;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class OptimizerServiceTest {

    private OptimizerService optimizer;

    @BeforeEach
    void setUp() {
        // consolidation threshold = 5.00
        optimizer = new OptimizerService(BigDecimal.valueOf(5.00));
    }

    // ──────────────────── assignOptimal ────────────────────

    @Test
    void assignOptimal_singleItemSingleVendor_picksOnlyAvailableVendor() {
        ItemMeta item = new ItemMeta(1L, "Chicken", "lb", BigDecimal.TEN);
        PriceSnapshot snap = buildSnapshot(1L, 1L, "Amazon", BigDecimal.valueOf(3.50));
        VendorMeta amazon = new VendorMeta(1L, "Amazon");

        OptimizeResult result = optimizer.assignOptimal(
                List.of(item),
                Map.of(1L, amazon),
                Map.of(1L, List.of(snap)));

        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).getVendorId()).isEqualTo(1L);
        assertThat(result.items().get(0).getUnitPrice()).isEqualByComparingTo(BigDecimal.valueOf(3.50));
        assertThat(result.totalCost()).isEqualByComparingTo(BigDecimal.valueOf(35.00));
        assertThat(result.missingItems()).isEmpty();
    }

    @Test
    void assignOptimal_singleItemMultipleVendors_picksCheapest() {
        ItemMeta item = new ItemMeta(1L, "Chicken", "lb", BigDecimal.TEN);
        PriceSnapshot amazon  = buildSnapshot(1L, 1L, "Amazon",  BigDecimal.valueOf(4.00));
        PriceSnapshot walmart = buildSnapshot(2L, 1L, "Walmart", BigDecimal.valueOf(3.20));
        PriceSnapshot kroger  = buildSnapshot(3L, 1L, "Kroger",  BigDecimal.valueOf(3.80));

        OptimizeResult result = optimizer.assignOptimal(
                List.of(item),
                Map.of(1L, new VendorMeta(1L, "Amazon"),
                       2L, new VendorMeta(2L, "Walmart"),
                       3L, new VendorMeta(3L, "Kroger")),
                Map.of(1L, List.of(amazon, walmart, kroger)));

        assertThat(result.items().get(0).getVendorName()).isEqualTo("Walmart");
        assertThat(result.totalCost()).isEqualByComparingTo(BigDecimal.valueOf(32.00));
    }

    @Test
    void assignOptimal_itemWithNoSnapshots_addedToMissingList() {
        ItemMeta item1 = new ItemMeta(1L, "Chicken", "lb", BigDecimal.TEN);
        ItemMeta item2 = new ItemMeta(2L, "Salmon",  "lb", BigDecimal.valueOf(5));
        PriceSnapshot snap = buildSnapshot(1L, 1L, "Amazon", BigDecimal.valueOf(3.50));

        OptimizeResult result = optimizer.assignOptimal(
                List.of(item1, item2),
                Map.of(1L, new VendorMeta(1L, "Amazon")),
                Map.of(1L, List.of(snap)));

        assertThat(result.items()).hasSize(1);
        assertThat(result.missingItems()).containsExactly(2L);
    }

    @Test
    void assignOptimal_multipleItems_computesCorrectSubtotals() {
        ItemMeta chicken = new ItemMeta(1L, "Chicken", "lb", BigDecimal.TEN);
        ItemMeta salmon  = new ItemMeta(2L, "Salmon",  "lb", BigDecimal.valueOf(5));

        PriceSnapshot snapChicken = buildSnapshot(1L, 1L, "Amazon", BigDecimal.valueOf(3.00));
        PriceSnapshot snapSalmon  = buildSnapshot(1L, 2L, "Amazon", BigDecimal.valueOf(8.00));

        OptimizeResult result = optimizer.assignOptimal(
                List.of(chicken, salmon),
                Map.of(1L, new VendorMeta(1L, "Amazon")),
                Map.of(1L, List.of(snapChicken), 2L, List.of(snapSalmon)));

        // chicken: 10 × 3 = 30, salmon: 5 × 8 = 40, total = 70
        assertThat(result.totalCost()).isEqualByComparingTo(BigDecimal.valueOf(70.00));
        assertThat(result.vendorSubtotals()).hasSize(1);
        assertThat(result.vendorSubtotals().get(0).getSubtotal())
                .isEqualByComparingTo(BigDecimal.valueOf(70.00));
    }

    @Test
    void assignOptimal_emptyItems_returnsZeroTotals() {
        OptimizeResult result = optimizer.assignOptimal(List.of(), Map.of(), Map.of());

        assertThat(result.items()).isEmpty();
        assertThat(result.totalCost()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.estimatedSavings()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    // ──────────────────── recomputeTotals ────────────────────

    @Test
    void recomputeTotals_updatesLineTotalsAndVendorSubtotals() {
        Plan.PlanItem pi1 = Plan.PlanItem.builder()
                .catalogItemId(1L).vendorId(1L).vendorName("Amazon")
                .lineTotal(BigDecimal.valueOf(30)).quantity(BigDecimal.TEN)
                .unitPrice(BigDecimal.valueOf(3)).overridden(false).build();

        Plan.PlanItem pi2 = Plan.PlanItem.builder()
                .catalogItemId(2L).vendorId(2L).vendorName("Walmart")
                .lineTotal(BigDecimal.valueOf(40)).quantity(BigDecimal.valueOf(5))
                .unitPrice(BigDecimal.valueOf(8)).overridden(false).build();

        Plan plan = Plan.builder()
                .items(java.util.Arrays.asList(pi1, pi2))
                .totalCost(BigDecimal.ZERO)
                .estimatedSavings(BigDecimal.valueOf(10))
                .build();

        optimizer.recomputeTotals(plan);

        assertThat(plan.getTotalCost()).isEqualByComparingTo(BigDecimal.valueOf(70));
        assertThat(plan.getVendorSubtotals()).hasSize(2);
        // estimatedSavings unchanged
        assertThat(plan.getEstimatedSavings()).isEqualByComparingTo(BigDecimal.valueOf(10));
    }

    // ──────────────────── helpers ────────────────────

    private PriceSnapshot buildSnapshot(Long vendorId, Long catalogItemId,
                                         String vendorName, BigDecimal price) {
        VendorProductResult product = VendorProductResult.builder()
                .vendorSku("SKU-" + vendorId)
                .productName("Product " + catalogItemId)
                .unitPrice(price)
                .unit("lb")
                .inStock(true)
                .url("https://vendor.example.com/p/" + vendorId)
                .build();

        return PriceSnapshot.builder()
                .vendorId(vendorId)
                .catalogItemId(catalogItemId)
                .vendorName(vendorName)
                .operatorId(1L)
                .bestPrice(price)
                .results(List.of(product))
                .queriedAt(Instant.now())
                .build();
    }
}
