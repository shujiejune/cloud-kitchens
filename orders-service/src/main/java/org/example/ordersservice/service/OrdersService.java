package org.example.ordersservice.service;

import org.example.ordersservice.dto.OrderHistoryFilter;
import org.example.ordersservice.dto.OrderResponse;
import org.example.ordersservice.dto.OrderSummaryResponse;
import org.example.ordersservice.dto.PagedResponse;
import org.springframework.data.domain.Pageable;

/**
 * Contract for read-side order history and single-order lookup, plus the
 * proxy for sub-order retry (which ultimately flips state in
 * procurement-service).
 *
 * Every method is tenant-scoped by operatorId.
 */
public interface OrdersService {

    /**
     * Returns a paged list of order summaries matching the filter.
     * When every filter field is null, this is an unfiltered listing
     * backed by the idx_orders_operator_submitted index.
     */
    PagedResponse<OrderSummaryResponse> listOrders(
            Long operatorId, OrderHistoryFilter filter, Pageable pageable);

    /**
     * Full order detail including line items; catalog item names are
     * hydrated via a Feign call to catalog-service.
     *
     * @throws org.example.ordersservice.exception.OrderNotFoundException    order id unknown or belongs to another operator
     * @throws org.example.ordersservice.exception.CatalogLookupException    catalog-service unreachable during name hydration
     */
    OrderResponse getOrder(Long operatorId, Long orderId, String bearerToken);

    /**
     * Same body as getOrder; kept as a distinct route for future divergence
     * (e.g. cache headers tuned for polling clients).
     *
     * @throws org.example.ordersservice.exception.OrderNotFoundException    order id unknown or belongs to another operator
     * @throws org.example.ordersservice.exception.CatalogLookupException    catalog-service unreachable during name hydration
     */
    OrderResponse getOrderStatus(Long operatorId, Long orderId, String bearerToken);

    /**
     * Retries a FAILED sub-order by delegating to procurement-service's
     * internal endpoint. Returns the refreshed LineItemResponse.
     *
     * orders-service never writes to order_line_items itself — the only
     * mutation here happens inside procurement-service.
     *
     * @throws org.example.ordersservice.exception.OrderNotFoundException          order id unknown or belongs to another operator
     * @throws org.example.ordersservice.exception.LineItemNotFoundException       line item id is not part of the order
     * @throws org.example.ordersservice.exception.ProcurementRetryFailedException procurement-service unreachable or returned 5xx
     */
    OrderResponse.LineItemResponse retrySubOrder(
            Long operatorId, Long orderId, Long lineItemId, String bearerToken);
}
