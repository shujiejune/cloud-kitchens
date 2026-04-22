package org.example.ordersservice.client;

import org.example.ordersservice.dto.LineItemStatusResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;

/**
 * Feign client for procurement-service's internal sub-order retry endpoint.
 *
 * The externally-exposed retry endpoint lives here at
 *   POST /api/v1/orders/{id}/retry/{lineItemId}
 * and delegates to procurement-service via this client, because vendor
 * fan-out (including the DB write that flips sub_order_status) is
 * procurement-service's responsibility — orders-service is read-only
 * on the shared orders / order_line_items tables.
 *
 * Bearer token forwarding is handled by the same RequestInterceptor used
 * for CatalogClient.
 */
@FeignClient(name = "procurement-service")
public interface ProcurementRetryClient {

    /**
     * Retries the given failed sub-order against its vendor. Returns the
     * new LineItemStatus so orders-service can echo it back to the client.
     */
    @PostMapping("/api/v1/procurement/orders/{orderId}/retry/{lineItemId}")
    LineItemStatusResponse retrySubOrder(
            @PathVariable("orderId") Long orderId,
            @PathVariable("lineItemId") Long lineItemId,
            @RequestHeader("Authorization") String bearerToken);
}
