package org.example.ordersservice.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.example.ordersservice.dto.OrderHistoryFilter;
import org.example.ordersservice.dto.OrderResponse;
import org.example.ordersservice.dto.OrderSummaryResponse;
import org.example.ordersservice.dto.PagedResponse;
import org.example.ordersservice.service.OrdersService;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

/**
 * REST controller for submitted purchase order history and retry.
 *
 * All endpoints require a valid JWT. operatorId is extracted from the
 * SecurityContext via @AuthenticationPrincipal — never read from the
 * request body or query parameters.
 *
 * Sub-order retry is exposed here (instead of on procurement-service)
 * because the operator-facing history UI is where retry lives in the
 * UX. The controller delegates to OrdersService which in turn calls
 * procurement-service over Feign to perform the actual state transition.
 */
@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
@Tag(name = "Orders", description = "Submitted purchase order history and retry")
public class OrdersController {

    private final OrdersService ordersService;

    // ----------------------------------------------------------------
    // GET /api/v1/orders
    // ----------------------------------------------------------------

    @GetMapping("")
    @Operation(summary = "List submitted orders with optional filters")
    public PagedResponse<OrderSummaryResponse> listOrders(
            @RequestParam(required = false) LocalDateTime fromDate,
            @RequestParam(required = false) LocalDateTime toDate,
            @RequestParam(required = false) Long vendorId,
            @RequestParam(required = false) Long catalogItemId,
            @ParameterObject Pageable pageable,
            @AuthenticationPrincipal Long operatorId) {

        OrderHistoryFilter filter = new OrderHistoryFilter(fromDate, toDate, vendorId, catalogItemId);
        return ordersService.listOrders(operatorId, filter, pageable);
    }

    // ----------------------------------------------------------------
    // GET /api/v1/orders/{id}
    // ----------------------------------------------------------------

    @GetMapping("/{id}")
    @Operation(summary = "Get a single order by ID")
    public OrderResponse getOrder(
            @PathVariable Long id,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String bearerToken,
            @AuthenticationPrincipal Long operatorId) {
        return ordersService.getOrder(operatorId, id, bearerToken);
    }

    // ----------------------------------------------------------------
    // GET /api/v1/orders/{id}/status
    // ----------------------------------------------------------------

    @GetMapping("/{id}/status")
    @Operation(summary = "Get the latest status for an order (used for polling)")
    public OrderResponse getOrderStatus(
            @PathVariable Long id,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String bearerToken,
            @AuthenticationPrincipal Long operatorId) {
        return ordersService.getOrderStatus(operatorId, id, bearerToken);
    }

    // ----------------------------------------------------------------
    // POST /api/v1/orders/{id}/retry/{lineItemId}
    // ----------------------------------------------------------------

    @PostMapping("/{id}/retry/{lineItemId}")
    @Operation(summary = "Retry a failed sub-order against its vendor")
    public OrderResponse.LineItemResponse retrySubOrder(
            @PathVariable("id") Long orderId,
            @PathVariable Long lineItemId,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String bearerToken,
            @AuthenticationPrincipal Long operatorId) {
        return ordersService.retrySubOrder(operatorId, orderId, lineItemId, bearerToken);
    }
}
