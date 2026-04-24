package org.example.procurementservice.listener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.procurementservice.dao.OrderDAO;
import org.example.procurementservice.dao.OrderLineItemDAO;
import org.example.procurementservice.dao.mongo.PriceSnapshotDAO;
import org.example.procurementservice.document.PriceSnapshot;
import org.example.procurementservice.entity.Order;
import org.example.procurementservice.entity.Order.OrderStatus;
import org.example.procurementservice.entity.OrderLineItem;
import org.example.procurementservice.entity.OrderLineItem.SubOrderStatus;
import org.example.procurementservice.event.SubOrderDispatchEvent;
import org.example.procurementservice.exception.VendorUnavailableException;
import org.example.procurementservice.service.VendorCallGateway;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Objects;
import java.util.Optional;

/**
 * Consumes {@link SubOrderDispatchEvent} messages (one per OrderLineItem), calls the
 * vendor API, and writes the outcome back to MySQL. Once all line items for an order
 * settle (no PENDING remaining), upgrades the Order status to COMPLETE or PARTIAL_FAILURE.
 *
 * Partitioned by vendorName — all events for a vendor arrive on the same partition,
 * preserving the serial-within-vendor contract from the original synchronous fan-out.
 */
@Component
@Slf4j
public class SubOrderConsumer {

    private final VendorCallGateway vendorCallGateway;
    private final PriceSnapshotDAO priceSnapshotDAO;
    private final OrderLineItemDAO orderLineItemDAO;
    private final OrderDAO orderDAO;
    private final TransactionTemplate transactionTemplate;

    public SubOrderConsumer(VendorCallGateway vendorCallGateway,
                            PriceSnapshotDAO priceSnapshotDAO,
                            OrderLineItemDAO orderLineItemDAO,
                            OrderDAO orderDAO,
                            PlatformTransactionManager transactionManager) {
        this.vendorCallGateway = vendorCallGateway;
        this.priceSnapshotDAO = priceSnapshotDAO;
        this.orderLineItemDAO = orderLineItemDAO;
        this.orderDAO = orderDAO;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @RetryableTopic(
            attempts = "3",
            backoff = @Backoff(delay = 1000, multiplier = 5)
    )
    @KafkaListener(topics = "${kafka.topics.sub-order-dispatch.name:sub-order-dispatch}",
                   groupId = "procurement-service")
    public void consume(SubOrderDispatchEvent event) {
        // Idempotency: skip if this line was already settled by a previous delivery.
        Optional<OrderLineItem> maybeItem = orderLineItemDAO.findById(event.lineItemId());
        if (maybeItem.isEmpty() || maybeItem.get().getSubOrderStatus() != SubOrderStatus.PENDING) {
            log.debug("Skipping SubOrderDispatchEvent for already-settled lineItemId={}", event.lineItemId());
            return;
        }

        String sku = resolveVendorSku(event);
        if (sku == null) {
            orderLineItemDAO.updateStatusAndRef(event.lineItemId(), SubOrderStatus.FAILED, null);
            finalizeOrderIfComplete(event.orderId());
            throw new VendorUnavailableException(
                    "No price snapshot available to resolve vendor SKU for line " + event.lineItemId());
        }

        try {
            String ref = vendorCallGateway.placeOrder(event.vendorName(), sku, event.quantity());
            orderLineItemDAO.updateStatusAndRef(event.lineItemId(), SubOrderStatus.CONFIRMED, ref);
            log.info("Sub-order CONFIRMED lineItemId={} orderId={} ref={}", event.lineItemId(), event.orderId(), ref);
        } catch (VendorUnavailableException e) {
            orderLineItemDAO.updateStatusAndRef(event.lineItemId(), SubOrderStatus.FAILED, null);
            log.warn("Sub-order FAILED lineItemId={} vendor={}: {}", event.lineItemId(), event.vendorName(), e.getMessage());
            finalizeOrderIfComplete(event.orderId());
            throw e;
        }

        finalizeOrderIfComplete(event.orderId());
    }

    @DltHandler
    public void handleDlt(SubOrderDispatchEvent event) {
        log.error("Sub-order DLT: lineItemId={}, orderId={} — marking line FAILED",
                event.lineItemId(), event.orderId());
        orderLineItemDAO.findById(event.lineItemId()).ifPresent(li -> {
            if (li.getSubOrderStatus() == SubOrderStatus.PENDING) {
                orderLineItemDAO.updateStatusAndRef(event.lineItemId(), SubOrderStatus.FAILED, null);
            }
        });
        finalizeOrderIfComplete(event.orderId());
    }

    // ----------------------------------------------------------------
    // Helpers
    // ----------------------------------------------------------------

    private String resolveVendorSku(SubOrderDispatchEvent event) {
        return priceSnapshotDAO
                .findTopByOperatorIdAndCatalogItemIdAndVendorIdOrderByQueriedAtDesc(
                        event.operatorId(), event.catalogItemId(), event.vendorId())
                .flatMap(snap -> pickSku(snap, event.unitPrice()))
                .orElse(null);
    }

    private static Optional<String> pickSku(PriceSnapshot snap, java.math.BigDecimal unitPrice) {
        if (snap.getResults() == null || snap.getResults().isEmpty()) return Optional.empty();
        return snap.getResults().stream()
                .filter(r -> r.getVendorSku() != null)
                .filter(r -> Objects.equals(r.getUnitPrice(), unitPrice))
                .map(PriceSnapshot.VendorProductResult::getVendorSku)
                .findFirst()
                .or(() -> snap.getResults().stream()
                        .filter(r -> r.getVendorSku() != null)
                        .map(PriceSnapshot.VendorProductResult::getVendorSku)
                        .findFirst());
    }

    /**
     * If no PENDING line items remain for the order, sets Order.status to
     * COMPLETE (all confirmed) or PARTIAL_FAILURE (at least one failed).
     * Runs inside a transaction so concurrent consumer threads see a consistent count.
     */
    private void finalizeOrderIfComplete(Long orderId) {
        transactionTemplate.executeWithoutResult(s -> {
            long pending = orderLineItemDAO.countByOrderIdAndSubOrderStatus(orderId, SubOrderStatus.PENDING);
            if (pending > 0) return;

            long failed = orderLineItemDAO.countByOrderIdAndSubOrderStatus(orderId, SubOrderStatus.FAILED);
            OrderStatus finalStatus = failed > 0 ? OrderStatus.PARTIAL_FAILURE : OrderStatus.COMPLETE;

            orderDAO.findById(orderId).ifPresent(order -> {
                if (order.getStatus() == OrderStatus.SUBMITTED) {
                    order.setStatus(finalStatus);
                    orderDAO.save(order);
                    log.info("Order {} finalized as {}", orderId, finalStatus);
                }
            });
        });
    }
}
