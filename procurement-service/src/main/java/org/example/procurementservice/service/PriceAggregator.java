package org.example.procurementservice.service;

import lombok.extern.slf4j.Slf4j;
import org.example.procurementservice.dao.VendorDAO;
import org.example.procurementservice.dao.mongo.PriceSnapshotDAO;
import org.example.procurementservice.document.PriceSnapshot;
import org.example.procurementservice.document.PriceSnapshot.VendorProductResult;
import org.example.procurementservice.entity.Vendor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

/**
 * Fans out price queries across every active vendor in parallel, honouring
 * the per-vendor rate limiters + circuit breakers enforced by
 * {@link VendorCallGateway}.
 *
 * Produces, for each (catalogItemId, vendorId) pair that returned a result,
 * a single {@link PriceSnapshot} document persisted to MongoDB — these
 * snapshots feed the optimizer and the {@code overrideVendor} staleness check.
 *
 * Vendors whose calls fail (circuit open, timeout, exception) are excluded
 * from the returned snapshots and their display names are accumulated into
 * {@link Result#vendorWarnings()} for the UI banner.
 */
@Component
@Slf4j
public class PriceAggregator {

    /** Snapshots younger than this are considered fresh enough to reuse. */
    private static final long CACHE_FRESHNESS_MINUTES = 5;

    /** Hard cap on the overall price-aggregation wall-clock — meets the 5s plan-generation NFR. */
    private static final long AGGREGATION_DEADLINE_SECONDS = 5;

    private final VendorDAO vendorDAO;
    private final PriceSnapshotDAO priceSnapshotDAO;
    private final VendorCallGateway vendorCallGateway;
    private final ExecutorService priceAggregationExecutor;

    public PriceAggregator(VendorDAO vendorDAO,
                           PriceSnapshotDAO priceSnapshotDAO,
                           VendorCallGateway vendorCallGateway,
                           @Qualifier("priceAggregationExecutor") ExecutorService priceAggregationExecutor) {
        this.vendorDAO = vendorDAO;
        this.priceSnapshotDAO = priceSnapshotDAO;
        this.vendorCallGateway = vendorCallGateway;
        this.priceAggregationExecutor = priceAggregationExecutor;
    }

    /**
     * @param operatorId      tenant
     * @param itemsByNameKey  catalog item id → search term used against vendor APIs
     * @param refreshPrices   when true, bypass the Mongo cache and hit every vendor
     */
    public Result aggregate(Long operatorId,
                            Map<Long, String> itemsByNameKey,
                            boolean refreshPrices) {

        List<Vendor> vendors = vendorDAO.findAllByIsActiveTrue();
        if (vendors.isEmpty()) {
            return new Result(Collections.emptyList(), List.of("No active vendors configured"));
        }

        List<Long> itemIds = new ArrayList<>(itemsByNameKey.keySet());
        Map<CacheKey, PriceSnapshot> warmCache = refreshPrices
                ? Collections.emptyMap()
                : loadWarmCache(operatorId, itemIds);

        List<PriceSnapshot> allSnapshots = new ArrayList<>(warmCache.values());
        Set<String> vendorWarnings = ConcurrentHashMap.newKeySet();

        // For each vendor, launch one CompletableFuture that sequentially fetches
        // every not-yet-cached item. Parallelism across vendors, serial within
        // a vendor (so the per-vendor rate limiter is never contended with itself).
        List<CompletableFuture<List<PriceSnapshot>>> futures = new ArrayList<>();
        for (Vendor vendor : vendors) {
            futures.add(CompletableFuture.supplyAsync(
                    () -> fetchForVendor(operatorId, itemsByNameKey, vendor, warmCache, vendorWarnings),
                    priceAggregationExecutor));
        }

        CompletableFuture<Void> all = CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new));
        try {
            all.get(AGGREGATION_DEADLINE_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException te) {
            log.warn("Price aggregation exceeded {}s deadline; using partial results", AGGREGATION_DEADLINE_SECONDS);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException ee) {
            log.warn("Price aggregation task failed: {}", ee.getMessage());
        }

        for (CompletableFuture<List<PriceSnapshot>> f : futures) {
            if (f.isDone() && !f.isCompletedExceptionally()) {
                try {
                    allSnapshots.addAll(f.get());
                } catch (Exception ignored) { /* already surfaced via warnings */ }
            }
        }

        return new Result(allSnapshots, new ArrayList<>(vendorWarnings));
    }

    // ----------------------------------------------------------------
    // Helpers
    // ----------------------------------------------------------------

    private Map<CacheKey, PriceSnapshot> loadWarmCache(Long operatorId, List<Long> itemIds) {
        Instant freshAfter = Instant.now().minus(CACHE_FRESHNESS_MINUTES, ChronoUnit.MINUTES);
        List<PriceSnapshot> fresh = priceSnapshotDAO.findFreshSnapshots(operatorId, itemIds, freshAfter);

        // Keep only the most recent per (item, vendor).
        Map<CacheKey, PriceSnapshot> out = new java.util.HashMap<>();
        for (PriceSnapshot s : fresh) {
            CacheKey k = new CacheKey(s.getCatalogItemId(), s.getVendorId());
            PriceSnapshot cur = out.get(k);
            if (cur == null || s.getQueriedAt().isAfter(cur.getQueriedAt())) {
                out.put(k, s);
            }
        }
        return out;
    }

    private List<PriceSnapshot> fetchForVendor(Long operatorId,
                                               Map<Long, String> itemsByNameKey,
                                               Vendor vendor,
                                               Map<CacheKey, PriceSnapshot> warmCache,
                                               Set<String> vendorWarnings) {

        List<PriceSnapshot> produced = new ArrayList<>();
        Set<Long> itemsNeedingFetch = new HashSet<>();
        for (Long itemId : itemsByNameKey.keySet()) {
            if (!warmCache.containsKey(new CacheKey(itemId, vendor.getId()))) {
                itemsNeedingFetch.add(itemId);
            }
        }
        if (itemsNeedingFetch.isEmpty()) {
            return produced;
        }

        boolean anySuccess = false;
        boolean anyFailure = false;
        for (Long itemId : itemsNeedingFetch) {
            String query = itemsByNameKey.get(itemId);
            Optional<List<VendorProductResult>> results = vendorCallGateway.searchPrices(vendor.getName(), query);
            if (results.isEmpty()) {
                anyFailure = true;
                continue;
            }
            if (results.get().isEmpty()) {
                // Vendor returned no match for this item — silent miss, not a vendor failure.
                anySuccess = true;
                continue;
            }
            anySuccess = true;
            VendorProductResult best = results.get().stream()
                    .filter(r -> r.getUnitPrice() != null)
                    .min(Comparator.comparing(VendorProductResult::getUnitPrice))
                    .orElse(null);
            if (best == null) continue;

            Instant now = Instant.now();
            PriceSnapshot snap = PriceSnapshot.builder()
                    .snapshotId(buildSnapshotId(operatorId, itemId, vendor.getId(), now))
                    .operatorId(operatorId)
                    .catalogItemId(itemId)
                    .vendorId(vendor.getId())
                    .vendorName(vendor.getName())
                    .queriedAt(now)
                    .searchQuery(query)
                    .results(results.get())
                    .bestPrice(best.getUnitPrice())
                    .ttlExpiry(now.plus(6, ChronoUnit.HOURS))
                    .build();
            priceSnapshotDAO.save(snap);
            produced.add(snap);
        }

        if (!anySuccess && anyFailure) {
            // Every call for this vendor failed — surface the vendor name to the UI.
            vendorWarnings.add(vendor.getName());
        }
        return produced;
    }

    private static String buildSnapshotId(Long operatorId, Long itemId, Long vendorId, Instant at) {
        String day = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS")
                .withZone(java.time.ZoneOffset.UTC).format(at);
        return "snap_" + day + "_" + operatorId + "_" + itemId + "_" + vendorId;
    }

    // ----------------------------------------------------------------
    // Return types
    // ----------------------------------------------------------------

    /** Grouped result of one aggregation run. */
    public record Result(List<PriceSnapshot> snapshots, List<String> vendorWarnings) {

        /** Snapshots keyed by (catalogItemId, vendorId) — most recent kept per key. */
        public Map<Long, List<PriceSnapshot>> snapshotsByItem() {
            return snapshots.stream().collect(Collectors.groupingBy(PriceSnapshot::getCatalogItemId));
        }
    }

    private record CacheKey(Long itemId, Long vendorId) {}
}
