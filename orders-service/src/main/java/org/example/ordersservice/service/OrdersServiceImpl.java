package org.example.ordersservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.ordersservice.client.CatalogClient;
import org.example.ordersservice.client.ProcurementRetryClient;
import org.example.ordersservice.dao.OrderLineItemViewDAO;
import org.example.ordersservice.dao.OrderViewDAO;
import org.example.ordersservice.dto.OrderHistoryFilter;
import org.example.ordersservice.dto.OrderResponse;
import org.example.ordersservice.dto.OrderSummaryResponse;
import org.example.ordersservice.dto.PagedResponse;
import org.example.ordersservice.entity.OrderLineItemView;
import org.example.ordersservice.entity.OrderView;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

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
    private final CatalogClient catalogClient;
    private final ProcurementRetryClient procurementRetryClient;

    // ----------------------------------------------------------------
    // Public API
    // ----------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<OrderSummaryResponse> listOrders(
            Long operatorId, OrderHistoryFilter filter, Pageable pageable) {
        throw new UnsupportedOperationException("TODO: implement listOrders");
    }

    @Override
    @Transactional(readOnly = true)
    public OrderResponse getOrder(Long operatorId, Long orderId, String bearerToken) {
        throw new UnsupportedOperationException("TODO: implement getOrder");
    }

    @Override
    @Transactional(readOnly = true)
    public OrderResponse getOrderStatus(Long operatorId, Long orderId, String bearerToken) {
        throw new UnsupportedOperationException("TODO: implement getOrderStatus");
    }

    @Override
    @Transactional(readOnly = true)
    public OrderResponse.LineItemResponse retrySubOrder(
            Long operatorId, Long orderId, Long lineItemId, String bearerToken) {
        throw new UnsupportedOperationException("TODO: implement retrySubOrder");
    }

    // ----------------------------------------------------------------
    // Private mapping helpers (hand-coded — see auth/catalog convention)
    // ----------------------------------------------------------------

    /** Projects an OrderView into the lightweight history-list summary. */
    private OrderSummaryResponse toSummary(OrderView orderView) {
        throw new UnsupportedOperationException("TODO: map OrderView -> OrderSummaryResponse");
    }

    /**
     * Projects an OrderView + its line items into the full response,
     * using the pre-hydrated catalog name map to avoid N+1 Feign calls.
     */
    private OrderResponse toResponse(OrderView orderView, Map<Long, String> catalogNamesById) {
        throw new UnsupportedOperationException("TODO: map OrderView -> OrderResponse");
    }

    /** Projects a single OrderLineItemView into the nested LineItemResponse. */
    private OrderResponse.LineItemResponse toLineResponse(
            OrderLineItemView lineItem, String catalogItemName) {
        throw new UnsupportedOperationException("TODO: map OrderLineItemView -> LineItemResponse");
    }

    /**
     * Batch-loads catalog item names for a set of ids via CatalogClient Feign
     * and returns an id -> name map. Used before rendering OrderResponse so
     * each line item can be hydrated without a per-line round trip.
     */
    private Map<Long, String> hydrateCatalogNames(List<Long> catalogItemIds, String bearerToken) {
        throw new UnsupportedOperationException("TODO: implement hydrateCatalogNames");
    }
}
