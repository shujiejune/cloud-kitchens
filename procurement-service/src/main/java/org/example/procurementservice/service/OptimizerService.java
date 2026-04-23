package org.example.procurementservice.service;

import lombok.extern.slf4j.Slf4j;
import org.example.procurementservice.document.Plan;
import org.example.procurementservice.document.Plan.PlanItem;
import org.example.procurementservice.document.Plan.VendorSubtotalSnapshot;
import org.example.procurementservice.document.PriceSnapshot;
import org.example.procurementservice.document.PriceSnapshot.VendorProductResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Cost-optimization engine. Two phases:
 *
 * <ol>
 *   <li><b>Greedy</b> — for every catalog item, assign the vendor whose
 *       snapshot has the lowest {@code bestPrice}.</li>
 *   <li><b>Consolidation</b> — reduce the number of distinct vendors when it
 *       costs almost nothing. For each item whose vendor is currently only
 *       serving that one item, try moving it to the next-cheapest vendor
 *       that already appears elsewhere in the plan. If the per-item cost
 *       delta is strictly below {@code consolidationThreshold}, perform the
 *       swap. Repeat until no profitable swap remains.</li>
 * </ol>
 *
 * The engine is a pure function — no DB calls, no Mongo. {@code assignOptimal}
 * takes only the snapshots, item metadata, and vendor metadata, so it's
 * trivially unit-testable and swappable for an LP solver later.
 */
@Service
@Slf4j
public class OptimizerService {

    private final BigDecimal consolidationThreshold;

    public OptimizerService(
            @Value("${procurement.optimizer.consolidation-threshold:5.00}")
            BigDecimal consolidationThreshold) {
        this.consolidationThreshold = consolidationThreshold;
    }

    // ----------------------------------------------------------------
    // Inputs (records so tests don't need any DB types)
    // ----------------------------------------------------------------

    public record ItemMeta(Long catalogItemId, String itemName, String unit, BigDecimal quantity) {}
    public record VendorMeta(Long vendorId, String vendorName) {}

    // ----------------------------------------------------------------
    // Core entry
    // ----------------------------------------------------------------

    /**
     * Produces a fully-populated {@link Plan.PlanItem} list + totals given the
     * per-item snapshots from every vendor and the catalog/vendor metadata.
     *
     * Items with zero snapshots are dropped and surfaced in the returned
     * {@code missingItems} list so the caller can add a warning.
     */
    public OptimizeResult assignOptimal(List<ItemMeta> items,
                                        Map<Long, VendorMeta> vendorsById,
                                        Map<Long, List<PriceSnapshot>> snapshotsByItem) {

        // Phase A — greedy
        List<PlanItem> chosen = new ArrayList<>();
        List<Long> missingItems = new ArrayList<>();
        for (ItemMeta item : items) {
            List<PriceSnapshot> snaps = snapshotsByItem.getOrDefault(item.catalogItemId(), List.of());
            PriceSnapshot cheapest = snaps.stream()
                    .filter(s -> s.getBestPrice() != null)
                    .min(Comparator.comparing(PriceSnapshot::getBestPrice))
                    .orElse(null);
            if (cheapest == null) {
                missingItems.add(item.catalogItemId());
                continue;
            }
            chosen.add(buildPlanItem(item, cheapest, vendorsById, /*overridden=*/false));
        }

        // Phase B — consolidation sweep
        consolidate(chosen, snapshotsByItem, vendorsById);

        // Totals + baseline savings
        BigDecimal totalCost = chosen.stream()
                .map(PlanItem::getLineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal baseline = computeSingleVendorBaseline(items, snapshotsByItem);
        BigDecimal savings = baseline.subtract(totalCost).max(BigDecimal.ZERO)
                .setScale(2, RoundingMode.HALF_UP);

        List<VendorSubtotalSnapshot> subtotals = computeSubtotals(chosen);

        return new OptimizeResult(chosen, totalCost.setScale(2, RoundingMode.HALF_UP), savings, subtotals, missingItems);
    }

    /**
     * Recomputes totals + subtotals after a manual override. Does NOT re-run
     * greedy or consolidation — the operator's override is respected as-is.
     */
    public void recomputeTotals(Plan plan) {
        List<PlanItem> items = plan.getItems();
        BigDecimal total = items.stream()
                .map(PlanItem::getLineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        plan.setTotalCost(total.setScale(2, RoundingMode.HALF_UP));
        plan.setVendorSubtotals(computeSubtotals(items));
        // estimatedSavings is kept from plan generation — an override doesn't
        // change the baseline reference.
    }

    // ----------------------------------------------------------------
    // Phase A helper
    // ----------------------------------------------------------------

    private PlanItem buildPlanItem(ItemMeta item,
                                   PriceSnapshot snap,
                                   Map<Long, VendorMeta> vendorsById,
                                   boolean overridden) {
        VendorProductResult best = snap.getResults().stream()
                .filter(r -> Objects.equals(r.getUnitPrice(), snap.getBestPrice()))
                .findFirst()
                .orElse(snap.getResults().isEmpty() ? null : snap.getResults().get(0));

        BigDecimal unitPrice = snap.getBestPrice();
        BigDecimal lineTotal = unitPrice.multiply(item.quantity()).setScale(2, RoundingMode.HALF_UP);
        VendorMeta vm = vendorsById.get(snap.getVendorId());

        return PlanItem.builder()
                .catalogItemId(item.catalogItemId())
                .itemName(item.itemName())
                .unit(item.unit())
                .quantity(item.quantity())
                .vendorId(snap.getVendorId())
                .vendorName(vm != null ? vm.vendorName() : snap.getVendorName())
                .vendorSku(best != null ? best.getVendorSku() : null)
                .productName(best != null ? best.getProductName() : null)
                .unitPrice(unitPrice)
                .lineTotal(lineTotal)
                .vendorProductUrl(best != null ? best.getUrl() : null)
                .overridden(overridden)
                .build();
    }

    // ----------------------------------------------------------------
    // Phase B — consolidation
    // ----------------------------------------------------------------

    private void consolidate(List<PlanItem> chosen,
                             Map<Long, List<PriceSnapshot>> snapshotsByItem,
                             Map<Long, VendorMeta> vendorsById) {

        int safety = chosen.size();
        while (safety-- > 0) {
            Map<Long, Long> countByVendor = chosen.stream()
                    .collect(Collectors.groupingBy(PlanItem::getVendorId, Collectors.counting()));

            PlanItem bestCandidate = null;
            PriceSnapshot bestTarget = null;
            BigDecimal bestDelta = null;

            for (PlanItem pi : chosen) {
                if (pi.isOverridden()) continue;
                if (countByVendor.getOrDefault(pi.getVendorId(), 0L) != 1L) continue;

                PriceSnapshot alt = snapshotsByItem.getOrDefault(pi.getCatalogItemId(), List.of())
                        .stream()
                        .filter(s -> !Objects.equals(s.getVendorId(), pi.getVendorId()))
                        .filter(s -> s.getBestPrice() != null)
                        // Only move to a vendor that is already used elsewhere in the plan
                        // (otherwise we'd be replacing one "single-item" vendor with another).
                        .filter(s -> countByVendor.getOrDefault(s.getVendorId(), 0L) >= 1L)
                        .min(Comparator.comparing(PriceSnapshot::getBestPrice))
                        .orElse(null);

                if (alt == null) continue;

                BigDecimal newLineTotal = alt.getBestPrice().multiply(pi.getQuantity());
                BigDecimal delta = newLineTotal.subtract(pi.getLineTotal());
                if (delta.compareTo(consolidationThreshold) >= 0) continue;

                if (bestDelta == null || delta.compareTo(bestDelta) < 0) {
                    bestDelta = delta;
                    bestCandidate = pi;
                    bestTarget = alt;
                }
            }

            if (bestCandidate == null) break;

            ItemMeta im = new ItemMeta(
                    bestCandidate.getCatalogItemId(),
                    bestCandidate.getItemName(),
                    bestCandidate.getUnit(),
                    bestCandidate.getQuantity());
            PlanItem replacement = buildPlanItem(im, bestTarget, vendorsById, /*overridden=*/false);

            int idx = chosen.indexOf(bestCandidate);
            chosen.set(idx, replacement);
            log.debug("Consolidated item {} from vendor {} to {} (delta {})",
                    im.catalogItemId(), bestCandidate.getVendorName(), replacement.getVendorName(), bestDelta);
        }
    }

    // ----------------------------------------------------------------
    // Totals helpers
    // ----------------------------------------------------------------

    private List<VendorSubtotalSnapshot> computeSubtotals(List<PlanItem> items) {
        Map<Long, VendorSubtotalSnapshot> byVendor = new HashMap<>();
        for (PlanItem pi : items) {
            VendorSubtotalSnapshot agg = byVendor.computeIfAbsent(pi.getVendorId(),
                    v -> VendorSubtotalSnapshot.builder()
                            .vendorId(v)
                            .vendorName(pi.getVendorName())
                            .itemCount(0)
                            .subtotal(BigDecimal.ZERO)
                            .build());
            agg.setItemCount(agg.getItemCount() + 1);
            agg.setSubtotal(agg.getSubtotal().add(pi.getLineTotal()));
        }
        return byVendor.values().stream()
                .peek(s -> s.setSubtotal(s.getSubtotal().setScale(2, RoundingMode.HALF_UP)))
                .sorted(Comparator.comparing(VendorSubtotalSnapshot::getVendorName))
                .toList();
    }

    /**
     * Baseline = cost of buying every item from the single vendor whose
     * total cost would be lowest. Used for the "estimated savings" headline.
     * Vendors that don't stock every item are excluded from the baseline set
     * (if no vendor stocks every item, baseline falls back to the greedy cost
     * so savings is reported as zero, not negative).
     */
    private BigDecimal computeSingleVendorBaseline(List<ItemMeta> items,
                                                   Map<Long, List<PriceSnapshot>> snapshotsByItem) {
        if (items.isEmpty()) return BigDecimal.ZERO;

        Map<Long, BigDecimal> totalByVendor = new HashMap<>();
        Map<Long, Integer>    itemCountByVendor = new HashMap<>();
        for (ItemMeta im : items) {
            List<PriceSnapshot> snaps = snapshotsByItem.getOrDefault(im.catalogItemId(), List.of());
            for (PriceSnapshot s : snaps) {
                if (s.getBestPrice() == null) continue;
                totalByVendor.merge(s.getVendorId(),
                        s.getBestPrice().multiply(im.quantity()),
                        BigDecimal::add);
                itemCountByVendor.merge(s.getVendorId(), 1, Integer::sum);
            }
        }
        return totalByVendor.entrySet().stream()
                .filter(e -> itemCountByVendor.getOrDefault(e.getKey(), 0) == items.size())
                .map(Map.Entry::getValue)
                .min(Comparator.naturalOrder())
                .orElse(BigDecimal.ZERO);
    }

    // ----------------------------------------------------------------
    // Result record
    // ----------------------------------------------------------------

    public record OptimizeResult(
            List<PlanItem> items,
            BigDecimal totalCost,
            BigDecimal estimatedSavings,
            List<VendorSubtotalSnapshot> vendorSubtotals,
            List<Long> missingItems) {}
}
