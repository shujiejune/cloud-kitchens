package org.example.procurementservice.service;

import lombok.extern.slf4j.Slf4j;
import org.example.catalogservice.dto.CatalogItemResponse;
import org.example.procurementservice.client.CatalogClient;
import org.example.procurementservice.dao.OrderDAO;
import org.example.procurementservice.dao.OrderLineItemDAO;
import org.example.procurementservice.dao.VendorDAO;
import org.example.procurementservice.dao.mongo.PlanDAO;
import org.example.procurementservice.dao.mongo.PriceSnapshotDAO;
import org.example.procurementservice.document.Plan;
import org.example.procurementservice.document.Plan.PlanItem;
import org.example.procurementservice.document.PriceSnapshot;
import org.example.procurementservice.document.PriceSnapshot.VendorProductResult;
import org.example.procurementservice.dto.GeneratePlanRequest;
import org.example.procurementservice.dto.LineItemStatusResponse;
import org.example.procurementservice.dto.OrderSubmissionResponse;
import org.example.procurementservice.dto.OverrideVendorRequest;
import org.example.procurementservice.dto.PurchasePlanResponse;
import org.example.procurementservice.dto.SubmitOrderRequest;
import org.example.procurementservice.dto.SubmitOrderRequest.LineItemRequest;
import org.example.procurementservice.dto.VendorResponse;
import org.example.procurementservice.entity.Order;
import org.example.procurementservice.entity.Order.OrderStatus;
import org.example.procurementservice.entity.OrderLineItem;
import org.example.procurementservice.entity.OrderLineItem.SubOrderStatus;
import org.example.procurementservice.entity.Vendor;
import org.example.procurementservice.exception.CatalogLookupException;
import org.example.procurementservice.exception.LineItemNotFoundException;
import org.example.procurementservice.exception.OrderNotFoundException;
import org.example.procurementservice.exception.PlanLineItemNotFoundException;
import org.example.procurementservice.exception.PlanNotFoundException;
import org.example.procurementservice.exception.PlanSubmissionMismatchException;
import org.example.procurementservice.exception.StalePriceSnapshotException;
import org.example.procurementservice.exception.SubOrderRetryNotAllowedException;
import org.example.procurementservice.exception.VendorNotFoundException;
import org.example.procurementservice.exception.VendorUnavailableException;
import org.example.procurementservice.mapper.PlanMapper;
import org.example.procurementservice.mapper.VendorMapper;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Primary implementation of ProcurementService.
 *
 * Transaction strategy:
 *   submitOrder is NOT annotated @Transactional. It uses TransactionTemplate
 *   to wrap only the JPA write (Order + lines) and the final status update.
 *   Vendor fan-out happens strictly between transactions, so no DB locks
 *   are ever held across external HTTP I/O.
 *
 * Mapping style matches auth-service / catalog-service: hand-coded private
 * toResponse / toEntity helpers — no MapStruct.
 */
@Service
@Slf4j
public class ProcurementServiceImpl implements ProcurementService {

    /** Snapshots older than this are considered stale for the override path. */
    private static final long OVERRIDE_STALENESS_MINUTES = 5;

    /** Plan TTL — matches the @Indexed(expireAfterSeconds=0) on Plan.ttlExpiry. */
    private static final long PLAN_TTL_HOURS = 6;

    private final CatalogClient catalogClient;
    private final VendorDAO vendorDAO;
    private final OrderDAO orderDAO;
    private final OrderLineItemDAO orderLineItemDAO;
    private final PriceSnapshotDAO priceSnapshotDAO;
    private final PlanDAO planDAO;
    private final PriceAggregator priceAggregator;
    private final OptimizerService optimizerService;
    private final VendorOrderFanOutService vendorOrderFanOutService;
    private final TransactionTemplate transactionTemplate;
    private final VendorMapper vendorMapper;
    private final PlanMapper planMapper;

    public ProcurementServiceImpl(CatalogClient catalogClient,
                                  VendorDAO vendorDAO,
                                  OrderDAO orderDAO,
                                  OrderLineItemDAO orderLineItemDAO,
                                  PriceSnapshotDAO priceSnapshotDAO,
                                  PlanDAO planDAO,
                                  PriceAggregator priceAggregator,
                                  OptimizerService optimizerService,
                                  VendorOrderFanOutService vendorOrderFanOutService,
                                  PlatformTransactionManager transactionManager,
                                  VendorMapper vendorMapper,
                                  PlanMapper planMapper) {
        this.catalogClient = catalogClient;
        this.vendorDAO = vendorDAO;
        this.orderDAO = orderDAO;
        this.orderLineItemDAO = orderLineItemDAO;
        this.priceSnapshotDAO = priceSnapshotDAO;
        this.planDAO = planDAO;
        this.priceAggregator = priceAggregator;
        this.optimizerService = optimizerService;
        this.vendorOrderFanOutService = vendorOrderFanOutService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.vendorMapper = vendorMapper;
        this.planMapper = planMapper;
    }

    // ================================================================
    // generatePlan
    // ================================================================

    @Override
    public PurchasePlanResponse generatePlan(Long operatorId, GeneratePlanRequest request, String bearerToken) {

        List<CatalogItemResponse> catalogItems;
        try {
            catalogItems = catalogClient.getItemsByIds(request.catalogItemIds(), bearerToken);
        } catch (Exception e) {
            throw new CatalogLookupException("Failed to load catalog items from catalog-service", e);
        }
        if (catalogItems == null || catalogItems.isEmpty()) {
            throw new CatalogLookupException(
                    "Catalog returned no items for requested ids " + request.catalogItemIds(), null);
        }

        // Search term used against vendor APIs = item name (could append category later).
        Map<Long, String> itemsByNameKey = new LinkedHashMap<>();
        for (CatalogItemResponse ci : catalogItems) {
            itemsByNameKey.put(ci.id(), ci.name());
        }

        PriceAggregator.Result aggregated = priceAggregator.aggregate(
                operatorId, itemsByNameKey, request.refreshPrices());

        Map<Long, Vendor> vendorsById = vendorDAO.findAll().stream()
                .collect(Collectors.toMap(Vendor::getId, v -> v));
        Map<Long, OptimizerService.VendorMeta> vendorMetaById = vendorsById.values().stream()
                .collect(Collectors.toMap(
                        Vendor::getId,
                        v -> new OptimizerService.VendorMeta(v.getId(), v.getName())));

        List<OptimizerService.ItemMeta> itemMetas = catalogItems.stream()
                .map(ci -> new OptimizerService.ItemMeta(
                        ci.id(), ci.name(), ci.unit(), ci.preferredQty()))
                .toList();

        OptimizerService.OptimizeResult optimized = optimizerService.assignOptimal(
                itemMetas, vendorMetaById, aggregated.snapshotsByItem());

        if (optimized.items().isEmpty()) {
            throw new VendorUnavailableException(
                    "No vendor responded with usable prices for any requested item");
        }

        List<String> warnings = new ArrayList<>(aggregated.vendorWarnings());
        for (Long missingId : optimized.missingItems()) {
            warnings.add("No vendor found for item #" + missingId);
        }

        Instant now = Instant.now();
        Plan plan = Plan.builder()
                .operatorId(operatorId)
                .items(optimized.items())
                .totalCost(optimized.totalCost())
                .estimatedSavings(optimized.estimatedSavings())
                .vendorSubtotals(optimized.vendorSubtotals())
                .vendorWarnings(warnings)
                .generatedAt(now)
                .ttlExpiry(now.plus(PLAN_TTL_HOURS, ChronoUnit.HOURS))
                .build();
        planDAO.save(plan);

        return planMapper.toResponse(plan);
    }

    // ================================================================
    // getPlan
    // ================================================================

    @Override
    public PurchasePlanResponse getPlan(Long operatorId, String planId) {
        Plan plan = planDAO.findByIdAndOperatorId(planId, operatorId)
                .orElseThrow(() -> new PlanNotFoundException("Plan not found: " + planId));
        return planMapper.toResponse(plan);
    }

    // ================================================================
    // overrideVendor
    // ================================================================

    @Override
    public PurchasePlanResponse overrideVendor(Long operatorId, String planId, OverrideVendorRequest request) {
        Plan plan = planDAO.findByIdAndOperatorId(planId, operatorId)
                .orElseThrow(() -> new PlanNotFoundException("Plan not found: " + planId));

        PlanItem target = plan.getItems().stream()
                .filter(i -> Objects.equals(i.getCatalogItemId(), request.catalogItemId()))
                .findFirst()
                .orElseThrow(() -> new PlanLineItemNotFoundException(
                        "Plan does not contain catalog item " + request.catalogItemId()));

        Vendor vendor = vendorDAO.findById(request.vendorId())
                .filter(Vendor::isActive)
                .orElseThrow(() -> new VendorNotFoundException(
                        "Vendor not found or inactive: " + request.vendorId()));

        Instant freshAfter = Instant.now().minus(OVERRIDE_STALENESS_MINUTES, ChronoUnit.MINUTES);
        PriceSnapshot snap = priceSnapshotDAO
                .findTopByOperatorIdAndCatalogItemIdAndVendorIdOrderByQueriedAtDesc(
                        operatorId, request.catalogItemId(), request.vendorId())
                .filter(s -> s.getQueriedAt().isAfter(freshAfter))
                .orElseThrow(() -> new StalePriceSnapshotException(
                        "No fresh price snapshot for item " + request.catalogItemId()
                                + " at vendor " + vendor.getName()
                                + " — re-generate the plan with refreshPrices=true"));

        VendorProductResult best = snap.getResults().stream()
                .filter(r -> Objects.equals(r.getUnitPrice(), snap.getBestPrice()))
                .findFirst()
                .orElse(snap.getResults().isEmpty() ? null : snap.getResults().get(0));

        BigDecimal unitPrice = snap.getBestPrice();
        BigDecimal lineTotal = unitPrice.multiply(target.getQuantity()).setScale(2, RoundingMode.HALF_UP);

        target.setVendorId(vendor.getId());
        target.setVendorName(vendor.getName());
        target.setVendorSku(best != null ? best.getVendorSku() : null);
        target.setProductName(best != null ? best.getProductName() : null);
        target.setUnitPrice(unitPrice);
        target.setLineTotal(lineTotal);
        target.setVendorProductUrl(best != null ? best.getUrl() : null);
        target.setOverridden(true);

        optimizerService.recomputeTotals(plan);
        planDAO.save(plan);

        return planMapper.toResponse(plan);
    }

    // ================================================================
    // submitOrder  (NOT @Transactional — see class javadoc)
    // ================================================================

    @Override
    public OrderSubmissionResponse submitOrder(Long operatorId, String planId, SubmitOrderRequest request) {
        Plan plan = planDAO.findByIdAndOperatorId(planId, operatorId)
                .orElseThrow(() -> new PlanNotFoundException("Plan not found: " + planId));
        validateSubmissionMatchesPlan(plan, request);

        // Step 1 — persist Order + line items in one short transaction.
        BigDecimal planSavings = plan.getEstimatedSavings();
        Order persisted = transactionTemplate.execute(status ->
                persistOrder(operatorId, request, planSavings));
        Long orderId = persisted.getId();

        // Load back with lines (ensures ids on each OrderLineItem).
        List<OrderLineItem> lines = orderLineItemDAO.findAllByOrderId(orderId);

        // Step 2 — fan out to vendors (outside any DB transaction).
        List<VendorOrderFanOutService.Result> results =
                vendorOrderFanOutService.fanOut(operatorId, lines);

        Map<Long, VendorOrderFanOutService.Result> resultByLineId = results.stream()
                .collect(Collectors.toMap(VendorOrderFanOutService.Result::lineItemId, r -> r));

        // Step 3 — update order status based on sub-order outcomes.
        boolean anyFailed = results.stream().anyMatch(r -> r.status() == SubOrderStatus.FAILED);
        OrderStatus finalOrderStatus = anyFailed ? OrderStatus.PARTIAL_FAILURE : OrderStatus.COMPLETE;
        transactionTemplate.executeWithoutResult(s -> {
            Order o = orderDAO.findById(orderId).orElseThrow();
            o.setStatus(finalOrderStatus);
            orderDAO.save(o);
        });

        // Step 4 — delete plan so it can't be replayed.
        planDAO.deleteByIdAndOperatorId(planId, operatorId);

        // Step 5 — assemble response.
        List<OrderSubmissionResponse.LineItemStatus> lineStatuses = new ArrayList<>();
        for (OrderLineItem li : lines) {
            VendorOrderFanOutService.Result r = resultByLineId.get(li.getId());
            lineStatuses.add(new OrderSubmissionResponse.LineItemStatus(
                    li.getId(),
                    li.getCatalogItemId(),
                    li.getVendor().getId(),
                    li.getVendor().getName(),
                    li.getQuantity(),
                    li.getLineTotal(),
                    r.status().name(),
                    r.vendorOrderRef(),
                    r.failureReason()));
        }

        return new OrderSubmissionResponse(
                orderId,
                finalOrderStatus.name(),
                persisted.getTotalCost(),
                persisted.getEstimatedSavings(),
                persisted.getSubmittedAt(),
                lineStatuses);
    }

    /** Transactional inner of submitOrder — writes Order + its OrderLineItems. */
    private Order persistOrder(Long operatorId, SubmitOrderRequest request, BigDecimal estimatedSavings) {
        Map<Long, Vendor> vendorsById = new HashMap<>();
        BigDecimal total = BigDecimal.ZERO;
        Order order = Order.builder()
                .operatorId(operatorId)
                .status(OrderStatus.SUBMITTED)
                .submittedAt(LocalDateTime.now())
                .estimatedSavings(estimatedSavings)
                .build();

        List<OrderLineItem> lines = new ArrayList<>();
        for (LineItemRequest li : request.lineItems()) {
            Vendor vendor = vendorsById.computeIfAbsent(li.vendorId(),
                    id -> vendorDAO.findById(id)
                            .orElseThrow(() -> new VendorNotFoundException("Vendor not found: " + id)));
            BigDecimal lineTotal = li.unitPrice().multiply(li.quantity()).setScale(2, RoundingMode.HALF_UP);
            total = total.add(lineTotal);

            OrderLineItem row = OrderLineItem.builder()
                    .order(order)
                    .catalogItemId(li.catalogItemId())
                    .vendor(vendor)
                    .quantity(li.quantity())
                    .unitPrice(li.unitPrice())
                    .lineTotal(lineTotal)
                    .subOrderStatus(SubOrderStatus.PENDING)
                    .build();
            lines.add(row);
        }
        order.setLineItems(lines);
        order.setTotalCost(total.setScale(2, RoundingMode.HALF_UP));
        return orderDAO.save(order);
    }

    // ================================================================
    // retrySubOrder
    // ================================================================

    @Override
    public LineItemStatusResponse retrySubOrder(Long operatorId, Long orderId, Long lineItemId) {
        // Step 1 — load + validate in a read-only tx (so the LAZY vendor relation is initialised).
        OrderLineItem line = transactionTemplate.execute(s -> {
            Order order = orderDAO.findByIdAndOperatorId(orderId, operatorId)
                    .orElseThrow(() -> new OrderNotFoundException("Order not found: " + orderId));

            OrderLineItem li = orderLineItemDAO.findByIdAndOperatorId(lineItemId, operatorId)
                    .orElseThrow(() -> new LineItemNotFoundException("Line item not found: " + lineItemId));
            if (!Objects.equals(li.getOrder().getId(), order.getId())) {
                throw new LineItemNotFoundException(
                        "Line item " + lineItemId + " is not part of order " + orderId);
            }
            if (li.getSubOrderStatus() != SubOrderStatus.FAILED) {
                throw new SubOrderRetryNotAllowedException(
                        "Line item " + lineItemId + " is " + li.getSubOrderStatus() + ", not FAILED");
            }
            // Force-init vendor (LAZY) so we can read vendor name/id outside the tx.
            li.getVendor().getName();
            return li;
        });

        // Step 2 — vendor call, outside any DB transaction.
        VendorOrderFanOutService.Result r = vendorOrderFanOutService.retrySingle(operatorId, line);

        // Step 3 — if this retry made the order fully CONFIRMED, upgrade its status.
        transactionTemplate.executeWithoutResult(s -> {
            long remainingFailed = orderLineItemDAO.countByOrderIdAndSubOrderStatus(orderId, SubOrderStatus.FAILED);
            if (remainingFailed == 0) {
                orderDAO.findById(orderId).ifPresent(o -> {
                    o.setStatus(OrderStatus.COMPLETE);
                    orderDAO.save(o);
                });
            }
        });

        return new LineItemStatusResponse(
                line.getId(),
                line.getCatalogItemId(),
                line.getVendor().getId(),
                line.getVendor().getName(),
                line.getQuantity(),
                line.getLineTotal(),
                r.status().name(),
                r.vendorOrderRef(),
                r.failureReason());
    }

    // ================================================================
    // listVendors
    // ================================================================

    @Override
    @Transactional(readOnly = true)
    public List<VendorResponse> listVendors() {
        return vendorDAO.findAll(Sort.by("name")).stream()
                .map(vendorMapper::toResponse)
                .toList();
    }

    // ================================================================
    // Submit-time validation
    // ================================================================

    private void validateSubmissionMatchesPlan(Plan plan, SubmitOrderRequest request) {
        if (plan.getItems().size() != request.lineItems().size()) {
            throw new PlanSubmissionMismatchException(
                    "Submitted line count (" + request.lineItems().size()
                            + ") does not match plan (" + plan.getItems().size() + ")");
        }
        Map<Long, PlanItem> planByItem = plan.getItems().stream()
                .collect(Collectors.toMap(PlanItem::getCatalogItemId, pi -> pi));
        for (LineItemRequest req : request.lineItems()) {
            PlanItem pi = planByItem.get(req.catalogItemId());
            if (pi == null) {
                throw new PlanSubmissionMismatchException(
                        "Submitted catalog item " + req.catalogItemId() + " is not in the plan");
            }
            if (!Objects.equals(pi.getVendorId(), req.vendorId())) {
                throw new PlanSubmissionMismatchException(
                        "Vendor mismatch for item " + req.catalogItemId()
                                + " (plan=" + pi.getVendorId() + ", submitted=" + req.vendorId() + ")");
            }
            if (pi.getQuantity().compareTo(req.quantity()) != 0) {
                throw new PlanSubmissionMismatchException(
                        "Quantity mismatch for item " + req.catalogItemId());
            }
            if (pi.getUnitPrice().compareTo(req.unitPrice()) != 0) {
                throw new PlanSubmissionMismatchException(
                        "Unit price mismatch for item " + req.catalogItemId());
            }
        }
    }

}
