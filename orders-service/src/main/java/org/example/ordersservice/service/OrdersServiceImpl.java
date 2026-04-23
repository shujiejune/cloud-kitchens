package org.example.ordersservice.service;

import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.catalogservice.dto.CatalogItemResponse;
import org.example.ordersservice.client.CatalogClient;
import org.example.ordersservice.client.ProcurementRetryClient;
import org.example.ordersservice.dao.OrderLineItemViewDAO;
import org.example.ordersservice.dao.OrderViewDAO;
import org.example.ordersservice.dao.VendorViewDAO;
import org.example.ordersservice.dto.LineItemStatusResponse;
import org.example.ordersservice.dto.OrderHistoryFilter;
import org.example.ordersservice.dto.OrderResponse;
import org.example.ordersservice.dto.OrderSummaryResponse;
import org.example.ordersservice.dto.PagedResponse;
import org.example.ordersservice.entity.OrderLineItemView;
import org.example.ordersservice.entity.OrderView;
import org.example.ordersservice.exception.CatalogLookupException;
import org.example.ordersservice.exception.LineItemNotFoundException;
import org.example.ordersservice.exception.OrderNotFoundException;
import org.example.ordersservice.exception.ProcurementRetryFailedException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Primary implementation of OrdersService.
 *
 * Every method is @Transactional(readOnly = true): orders-service never
 * writes to the shared orders / order_line_items tables. The single
 * mutation path (sub-order retry) delegates to procurement-service over
 * Feign — no local JPA write.
 *
 * Mapping style matches auth-service / catalog-service: hand-coded
 * private toResponse helpers — no MapStruct.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OrdersServiceImpl implements OrdersService {

    private final OrderViewDAO orderViewDAO;
    private final OrderLineItemViewDAO orderLineItemViewDAO;
    private final VendorViewDAO vendorViewDAO;
    private final CatalogClient catalogClient;
    private final ProcurementRetryClient procurementRetryClient;

    // ----------------------------------------------------------------
    // Public API
    // ----------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<OrderSummaryResponse> listOrders(
            Long operatorId, OrderHistoryFilter filter, Pageable pageable) {
        boolean hasFilters = filter.fromDate() != null || filter.toDate() != null
                || filter.vendorId() != null || filter.catalogItemId() != null;

        Page<OrderView> page = hasFilters
                ? orderViewDAO.search(operatorId, filter.fromDate(), filter.toDate(),
                        filter.vendorId(), filter.catalogItemId(), pageable)
                : orderViewDAO.findAllByOperatorId(operatorId, pageable);

        return PagedResponse.of(page.map(this::toSummary));
    }

    @Override
    @Transactional(readOnly = true)
    public OrderResponse getOrder(Long operatorId, Long orderId, String bearerToken) {
        OrderView orderView = orderViewDAO.findByIdAndOperatorId(orderId, operatorId)
                .orElseThrow(() -> new OrderNotFoundException("Order not found: " + orderId));

        List<OrderLineItemView> lineItems = orderLineItemViewDAO.findAllByOrderId(orderId);

        List<Long> catalogIds = lineItems.stream()
                .map(OrderLineItemView::getCatalogItemId).distinct().toList();
        List<Long> vendorIds = lineItems.stream()
                .map(OrderLineItemView::getVendorId).distinct().toList();

        Map<Long, String> catalogNames = hydrateCatalogNames(catalogIds, bearerToken);
        Map<Long, String> vendorNames  = hydrateVendorNames(vendorIds);

        return toResponse(orderView, lineItems, catalogNames, vendorNames);
    }

    @Override
    @Transactional(readOnly = true)
    public OrderResponse getOrderStatus(Long operatorId, Long orderId, String bearerToken) {
        return getOrder(operatorId, orderId, bearerToken);
    }

    @Override
    @Transactional(readOnly = true)
    public OrderResponse.LineItemResponse retrySubOrder(
            Long operatorId, Long orderId, Long lineItemId, String bearerToken) {
        orderViewDAO.findByIdAndOperatorId(orderId, operatorId)
                .orElseThrow(() -> new OrderNotFoundException("Order not found: " + orderId));

        OrderLineItemView lineItem = orderLineItemViewDAO.findAllByOrderId(orderId).stream()
                .filter(li -> li.getId().equals(lineItemId))
                .findFirst()
                .orElseThrow(() -> new LineItemNotFoundException(
                        "Line item " + lineItemId + " not found on order " + orderId));

        LineItemStatusResponse retryResult = retryViaFeign(orderId, lineItemId, bearerToken);

        String catalogName = hydrateCatalogNames(List.of(lineItem.getCatalogItemId()), bearerToken)
                .getOrDefault(lineItem.getCatalogItemId(), null);
        String vendorName = hydrateVendorNames(List.of(lineItem.getVendorId()))
                .getOrDefault(lineItem.getVendorId(), null);

        return new OrderResponse.LineItemResponse(
                lineItem.getId(),
                lineItem.getCatalogItemId(),
                catalogName,
                lineItem.getVendorId(),
                vendorName,
                lineItem.getQuantity(),
                lineItem.getUnitPrice(),
                retryResult.lineTotal(),
                retryResult.subOrderStatus(),
                retryResult.vendorOrderRef());
    }

    // ----------------------------------------------------------------
    // Private mapping helpers (hand-coded — see auth/catalog convention)
    // ----------------------------------------------------------------

    /** Projects an OrderView into the lightweight history-list summary. */
    private OrderSummaryResponse toSummary(OrderView orderView) {
        return new OrderSummaryResponse(
                orderView.getId(),
                orderView.getStatus(),
                orderView.getTotalCost(),
                orderView.getEstimatedSavings(),
                orderView.getSubmittedAt(),
                orderView.getLineItems().size());
    }

    private OrderResponse toResponse(OrderView orderView, List<OrderLineItemView> lineItems,
            Map<Long, String> catalogNamesById, Map<Long, String> vendorNamesById) {
        List<OrderResponse.LineItemResponse> itemResponses = lineItems.stream()
                .map(li -> toLineResponse(li,
                        catalogNamesById.getOrDefault(li.getCatalogItemId(), null),
                        vendorNamesById.getOrDefault(li.getVendorId(), null)))
                .toList();

        return new OrderResponse(
                orderView.getId(),
                orderView.getOperatorId(),
                orderView.getStatus(),
                orderView.getTotalCost(),
                orderView.getEstimatedSavings(),
                orderView.getSubmittedAt(),
                orderView.getCreatedAt(),
                itemResponses);
    }

    /** Projects a single OrderLineItemView into the nested LineItemResponse. */
    private OrderResponse.LineItemResponse toLineResponse(
            OrderLineItemView lineItem, String catalogItemName, String vendorName) {
        return new OrderResponse.LineItemResponse(
                lineItem.getId(),
                lineItem.getCatalogItemId(),
                catalogItemName,
                lineItem.getVendorId(),
                vendorName,
                lineItem.getQuantity(),
                lineItem.getUnitPrice(),
                lineItem.getLineTotal(),
                lineItem.getSubOrderStatus(),
                lineItem.getVendorOrderRef());
    }

    /**
     * Batch-loads catalog item names for a set of ids via CatalogClient Feign
     * and returns an id -> name map. Used before rendering OrderResponse so
     * each line item can be hydrated without a per-line round trip.
     */
    private Map<Long, String> hydrateCatalogNames(List<Long> catalogItemIds, String bearerToken) {
        if (catalogItemIds.isEmpty()) return Map.of();
        try {
            List<CatalogItemResponse> items = catalogClient.getItemsByIds(catalogItemIds, bearerToken);
            return items.stream()
                    .collect(Collectors.toMap(CatalogItemResponse::id, CatalogItemResponse::name));
        } catch (FeignException e) {
            throw new CatalogLookupException("Failed to load catalog names: " + e.getMessage(), e);
        }
    }

    private Map<Long, String> hydrateVendorNames(List<Long> vendorIds) {
        if (vendorIds.isEmpty()) return Map.of();
        return vendorViewDAO.findAllById(vendorIds).stream()
                .collect(Collectors.toMap(v -> v.getId(), v -> v.getName()));
    }

    private LineItemStatusResponse retryViaFeign(Long orderId, Long lineItemId, String bearerToken) {
        try {
            return procurementRetryClient.retrySubOrder(orderId, lineItemId, bearerToken);
        } catch (FeignException e) {
            throw new ProcurementRetryFailedException(
                    "Retry failed for line item " + lineItemId + ": " + e.getMessage(), e);
        }
    }
}
