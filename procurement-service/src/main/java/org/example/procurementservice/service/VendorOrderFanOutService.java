package org.example.procurementservice.service;

import lombok.extern.slf4j.Slf4j;
import org.example.procurementservice.dao.OrderLineItemDAO;
import org.example.procurementservice.dao.mongo.PriceSnapshotDAO;
import org.example.procurementservice.document.PriceSnapshot;
import org.example.procurementservice.document.PriceSnapshot.VendorProductResult;
import org.example.procurementservice.entity.OrderLineItem;
import org.example.procurementservice.entity.OrderLineItem.SubOrderStatus;
import org.example.procurementservice.exception.VendorUnavailableException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

/**
 * Submits sub-orders to vendor APIs after the parent order has been
 * committed in MySQL. Groups line items by vendor, runs one
 * {@link CompletableFuture} per vendor on {@code vendorFanOutExecutor}, and
 * inside each group calls {@link VendorCallGateway#placeOrder} sequentially
 * (so the per-vendor rate limiter is never contended with itself).
 *
 * For every line item, this service persists the outcome immediately via
 * {@link OrderLineItemDAO#updateStatusAndRef} — each update is its own
 * short-lived Hibernate transaction. The parent {@code submitOrder} call
 * has already committed; we never hold DB locks across vendor I/O.
 */
@Component
@Slf4j
public class VendorOrderFanOutService {

    private static final long FANOUT_DEADLINE_SECONDS = 10;

    private final VendorCallGateway vendorCallGateway;
    private final OrderLineItemDAO orderLineItemDAO;
    private final PriceSnapshotDAO priceSnapshotDAO;
    private final ExecutorService vendorFanOutExecutor;

    public VendorOrderFanOutService(
            VendorCallGateway vendorCallGateway,
            OrderLineItemDAO orderLineItemDAO,
            PriceSnapshotDAO priceSnapshotDAO,
            @Qualifier("vendorFanOutExecutor") ExecutorService vendorFanOutExecutor) {
        this.vendorCallGateway = vendorCallGateway;
        this.orderLineItemDAO = orderLineItemDAO;
        this.priceSnapshotDAO = priceSnapshotDAO;
        this.vendorFanOutExecutor = vendorFanOutExecutor;
    }

    /**
     * Fans out every line item to its vendor. Returns per-line results in the
     * same order as the input. Each {@link Result} captures the outcome; the
     * DB rows have already been updated to match.
     */
    public List<Result> fanOut(Long operatorId, List<OrderLineItem> lineItems) {
        Map<String, List<OrderLineItem>> byVendor = new LinkedHashMap<>();
        for (OrderLineItem li : lineItems) {
            byVendor.computeIfAbsent(li.getVendor().getName(), k -> new ArrayList<>()).add(li);
        }

        // Resolve vendorSku per line up-front (one Mongo read each) — avoids
        // holding DB cursors open while doing vendor I/O.
        Map<Long, String> skuByLineId = resolveSkus(operatorId, lineItems);

        Map<Long, Result> resultByLineId = new ConcurrentHashMap<>();
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (Map.Entry<String, List<OrderLineItem>> e : byVendor.entrySet()) {
            String vendorName = e.getKey();
            List<OrderLineItem> group = e.getValue();
            futures.add(CompletableFuture.runAsync(
                    () -> submitGroup(vendorName, group, skuByLineId, resultByLineId),
                    vendorFanOutExecutor));
        }

        CompletableFuture<Void> all = CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new));
        try {
            all.get(FANOUT_DEADLINE_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException te) {
            log.warn("Vendor fan-out exceeded {}s deadline; lines with no result will be marked FAILED",
                    FANOUT_DEADLINE_SECONDS);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException ee) {
            log.warn("Fan-out task failed: {}", ee.getMessage());
        }

        // Finalize anything that didn't receive a result into FAILED.
        List<Result> out = new ArrayList<>(lineItems.size());
        for (OrderLineItem li : lineItems) {
            Result r = resultByLineId.get(li.getId());
            if (r == null) {
                orderLineItemDAO.updateStatusAndRef(li.getId(), SubOrderStatus.FAILED, null);
                r = new Result(li.getId(), SubOrderStatus.FAILED, null,
                        "Vendor fan-out timed out");
            }
            out.add(r);
        }
        return out;
    }

    /**
     * Retries a single previously-FAILED line item — used by
     * {@code retrySubOrder}. Throws {@link VendorUnavailableException} if the
     * vendor is still unreachable; callers translate that into a 503 body.
     */
    public Result retrySingle(Long operatorId, OrderLineItem line) {
        String sku = resolveSkus(operatorId, List.of(line)).get(line.getId());
        if (sku == null) {
            orderLineItemDAO.updateStatusAndRef(line.getId(), SubOrderStatus.FAILED, null);
            throw new VendorUnavailableException(
                    "No price snapshot available to resolve vendor SKU for line " + line.getId());
        }
        try {
            String ref = vendorCallGateway.placeOrder(line.getVendor().getName(), sku, line.getQuantity());
            orderLineItemDAO.updateStatusAndRef(line.getId(), SubOrderStatus.CONFIRMED, ref);
            return new Result(line.getId(), SubOrderStatus.CONFIRMED, ref, null);
        } catch (VendorUnavailableException e) {
            orderLineItemDAO.updateStatusAndRef(line.getId(), SubOrderStatus.FAILED, null);
            throw e;
        }
    }

    // ----------------------------------------------------------------
    // Internals
    // ----------------------------------------------------------------

    private void submitGroup(String vendorName,
                             List<OrderLineItem> group,
                             Map<Long, String> skuByLineId,
                             Map<Long, Result> resultByLineId) {
        for (OrderLineItem li : group) {
            String sku = skuByLineId.get(li.getId());
            if (sku == null) {
                orderLineItemDAO.updateStatusAndRef(li.getId(), SubOrderStatus.FAILED, null);
                resultByLineId.put(li.getId(), new Result(li.getId(), SubOrderStatus.FAILED, null,
                        "No price snapshot available to resolve vendor SKU"));
                continue;
            }
            try {
                String ref = vendorCallGateway.placeOrder(vendorName, sku, li.getQuantity());
                orderLineItemDAO.updateStatusAndRef(li.getId(), SubOrderStatus.CONFIRMED, ref);
                resultByLineId.put(li.getId(), new Result(li.getId(), SubOrderStatus.CONFIRMED, ref, null));
            } catch (Exception e) {
                log.warn("Sub-order FAILED for line {} @ {}: {}", li.getId(), vendorName, e.getMessage());
                orderLineItemDAO.updateStatusAndRef(li.getId(), SubOrderStatus.FAILED, null);
                resultByLineId.put(li.getId(), new Result(li.getId(), SubOrderStatus.FAILED, null,
                        e.getMessage()));
            }
        }
    }

    /**
     * Picks the vendorSku for each line by matching the stored unitPrice
     * against the most recent snapshot's results list for
     * (operatorId, catalogItemId, vendorId). If no exact match is found we
     * fall back to the snapshot's cheapest result (same vendor still).
     */
    private Map<Long, String> resolveSkus(Long operatorId, List<OrderLineItem> lineItems) {
        Map<Long, String> out = new HashMap<>();
        for (OrderLineItem li : lineItems) {
            priceSnapshotDAO.findTopByOperatorIdAndCatalogItemIdAndVendorIdOrderByQueriedAtDesc(
                            operatorId, li.getCatalogItemId(), li.getVendor().getId())
                    .flatMap(snap -> pickSku(snap, li.getUnitPrice()))
                    .ifPresent(sku -> out.put(li.getId(), sku));
        }
        return out;
    }

    private static java.util.Optional<String> pickSku(PriceSnapshot snap, java.math.BigDecimal unitPrice) {
        if (snap.getResults() == null || snap.getResults().isEmpty()) return java.util.Optional.empty();
        return snap.getResults().stream()
                .filter(r -> r.getVendorSku() != null)
                .filter(r -> Objects.equals(r.getUnitPrice(), unitPrice))
                .map(VendorProductResult::getVendorSku)
                .findFirst()
                .or(() -> snap.getResults().stream()
                        .filter(r -> r.getVendorSku() != null)
                        .map(VendorProductResult::getVendorSku)
                        .findFirst());
    }

    // ----------------------------------------------------------------
    // Result record
    // ----------------------------------------------------------------

    public record Result(Long lineItemId, SubOrderStatus status, String vendorOrderRef, String failureReason) {}
}
