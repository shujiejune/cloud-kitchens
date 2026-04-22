package org.example.procurementservice.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.procurementservice.dto.GeneratePlanRequest;
import org.example.procurementservice.dto.LineItemStatusResponse;
import org.example.procurementservice.dto.OrderSubmissionResponse;
import org.example.procurementservice.dto.OverrideVendorRequest;
import org.example.procurementservice.dto.PurchasePlanResponse;
import org.example.procurementservice.dto.SubmitOrderRequest;
import org.example.procurementservice.dto.VendorResponse;
import org.example.procurementservice.service.ProcurementService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST controller for procurement: plan generation, override, submission,
 * sub-order retry, and vendor listing.
 *
 * All endpoints require a valid JWT. operatorId is extracted from the
 * SecurityContext (@AuthenticationPrincipal) and passed to the service —
 * never read from the request body or query parameters.
 *
 * The /orders/{orderId}/retry/{lineItemId} endpoint is called internally
 * by orders-service over Feign (via Eureka, not the gateway). The caller
 * forwards the operator's bearer token so this service's JWT filter can
 * populate the SecurityContext the same way it does for gateway traffic.
 */
@RestController
@RequestMapping("/api/v1/procurement")
@RequiredArgsConstructor
@Tag(name = "Procurement", description = "Plan generation, submission, vendor fan-out")
public class ProcurementController {

    private final ProcurementService procurementService;

    // ----------------------------------------------------------------
    // POST /api/v1/procurement/plan
    // ----------------------------------------------------------------

    @PostMapping("/plan")
    @Operation(summary = "Generate a purchase plan across active vendors")
    public PurchasePlanResponse generatePlan(
            @Valid @RequestBody GeneratePlanRequest request,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String bearerToken,
            @AuthenticationPrincipal Long operatorId) {
        return procurementService.generatePlan(operatorId, request, bearerToken);
    }

    // ----------------------------------------------------------------
    // GET /api/v1/procurement/plan/{planId}
    // ----------------------------------------------------------------

    @GetMapping("/plan/{planId}")
    @Operation(summary = "Fetch a previously generated plan")
    public PurchasePlanResponse getPlan(
            @PathVariable String planId,
            @AuthenticationPrincipal Long operatorId) {
        return procurementService.getPlan(operatorId, planId);
    }

    // ----------------------------------------------------------------
    // PUT /api/v1/procurement/plan/{planId}/override
    // ----------------------------------------------------------------

    @PutMapping("/plan/{planId}/override")
    @Operation(summary = "Reassign a plan line to a different vendor and recompute subtotals")
    public PurchasePlanResponse overrideVendor(
            @PathVariable String planId,
            @Valid @RequestBody OverrideVendorRequest request,
            @AuthenticationPrincipal Long operatorId) {
        return procurementService.overrideVendor(operatorId, planId, request);
    }

    // ----------------------------------------------------------------
    // POST /api/v1/procurement/plan/{planId}/submit
    // ----------------------------------------------------------------

    /**
     * Submits the plan, writes Order + OrderLineItems, fans out vendor calls.
     * Returns 201 Created when every sub-order is CONFIRMED, or 207 Multi-Status
     * when at least one sub-order FAILED. The body is the same shape in both
     * cases so the client renders a uniform status table.
     */
    @PostMapping("/plan/{planId}/submit")
    @Operation(summary = "Submit the plan and fan out vendor orders")
    public ResponseEntity<OrderSubmissionResponse> submitOrder(
            @PathVariable String planId,
            @Valid @RequestBody SubmitOrderRequest request,
            @AuthenticationPrincipal Long operatorId) {

        OrderSubmissionResponse result = procurementService.submitOrder(operatorId, planId, request);

        boolean anyFailed = result.lineItems().stream()
                .anyMatch(li -> "FAILED".equals(li.subOrderStatus()));
        HttpStatus status = anyFailed ? HttpStatus.MULTI_STATUS : HttpStatus.CREATED;

        return ResponseEntity.status(status).body(result);
    }

    // ----------------------------------------------------------------
    // POST /api/v1/procurement/orders/{orderId}/retry/{lineItemId}  (internal)
    // ----------------------------------------------------------------

    /**
     * Internal endpoint invoked by orders-service's ProcurementRetryClient Feign.
     * Retries a single FAILED sub-order against its vendor and returns the
     * updated line item status.
     */
    @PostMapping("/orders/{orderId}/retry/{lineItemId}")
    @Operation(summary = "Retry a failed sub-order (internal use)")
    public LineItemStatusResponse retrySubOrder(
            @PathVariable Long orderId,
            @PathVariable Long lineItemId,
            @AuthenticationPrincipal Long operatorId) {
        return procurementService.retrySubOrder(operatorId, orderId, lineItemId);
    }

    // ----------------------------------------------------------------
    // GET /api/v1/procurement/vendors
    // ----------------------------------------------------------------

    @GetMapping("/vendors")
    @Operation(summary = "List all vendors known to the system")
    public List<VendorResponse> listVendors() {
        return procurementService.listVendors();
    }
}
