package org.example.procurementservice.service;

import org.example.procurementservice.dto.GeneratePlanRequest;
import org.example.procurementservice.dto.LineItemStatusResponse;
import org.example.procurementservice.dto.OrderSubmissionResponse;
import org.example.procurementservice.dto.OverrideVendorRequest;
import org.example.procurementservice.dto.PurchasePlanResponse;
import org.example.procurementservice.dto.SubmitOrderRequest;
import org.example.procurementservice.dto.VendorResponse;

import java.util.List;

/**
 * Contract for purchase-plan generation, vendor override, submission,
 * and retry of failed sub-orders.
 *
 * Separating interface from implementation keeps the controller decoupled
 * and makes it trivial to mock the service in unit tests.
 */
public interface ProcurementService {

    /**
     * Generates a purchase plan by:
     *   1. Loading each catalog item (Feign call to catalog-service, using bearerToken).
     *   2. For each item, fetching (or cache-hitting) a price snapshot from every
     *      active vendor's API via the Resilience4j-wrapped Feign clients.
     *   3. Picking the cheapest vendor per item to form the initial plan.
     *   4. Computing totalCost, per-vendor subtotals, and estimatedSavings versus
     *      the single-cheapest-vendor baseline.
     *   5. Persisting the result as a Plan document with a 6h TTL, then returning
     *      it (planId == Mongo _id).
     *
     * Vendors that fail during price fetch are captured in the returned
     * vendorWarnings list rather than aborting the whole plan.
     *
     * @throws org.example.procurementservice.exception.CatalogLookupException    catalog-service unreachable or returned an unusable response
     * @throws org.example.procurementservice.exception.VendorUnavailableException every vendor API failed
     */
    PurchasePlanResponse generatePlan(Long operatorId, GeneratePlanRequest request, String bearerToken);

    /**
     * Loads a previously generated plan by id, scoped to the calling operator.
     *
     * @throws org.example.procurementservice.exception.PlanNotFoundException id unknown, expired, or belongs to another operator
     */
    PurchasePlanResponse getPlan(Long operatorId, String planId);

    /**
     * Reassigns a plan line to a different vendor, recomputes its line total
     * from the freshest cached snapshot, recomputes per-vendor subtotals and
     * the plan's totalCost + estimatedSavings, and persists the updated plan.
     *
     * The override is rejected if no fresh snapshot exists for the target
     * (operatorId, catalogItemId, vendorId) triple — the UI must re-run plan
     * generation with refreshPrices=true before retrying.
     *
     * @throws org.example.procurementservice.exception.PlanNotFoundException          plan id unknown or belongs to another operator
     * @throws org.example.procurementservice.exception.PlanLineItemNotFoundException  catalogItemId is not in this plan
     * @throws org.example.procurementservice.exception.VendorNotFoundException        vendor id unknown or inactive
     * @throws org.example.procurementservice.exception.StalePriceSnapshotException    no fresh snapshot for the target (item, vendor)
     */
    PurchasePlanResponse overrideVendor(Long operatorId, String planId, OverrideVendorRequest request);

    /**
     * Submits the plan:
     *   1. Loads the plan and validates that request.lineItems exactly mirrors it
     *      (catalogItemId, vendorId, quantity, unitPrice) — guards against client
     *      drift between generate/override and submit.
     *   2. Writes Order + OrderLineItems in a single JPA transaction.
     *   3. After commit, fans out one HTTP call per distinct vendor to place the
     *      sub-orders; updates each line's sub-order status + vendorOrderRef or
     *      failureReason based on the vendor response.
     *   4. Deletes the Plan document so it cannot be replayed.
     *
     * Returns a status-per-line response. The controller maps the per-line
     * statuses to a 201 (all CONFIRMED) or 207 (any FAILED) HTTP code.
     *
     * @throws org.example.procurementservice.exception.PlanNotFoundException         plan id unknown or belongs to another operator
     * @throws org.example.procurementservice.exception.PlanSubmissionMismatchException submitted lines diverge from stored plan
     */
    OrderSubmissionResponse submitOrder(Long operatorId, String planId, SubmitOrderRequest request);

    /**
     * Retries a single previously-FAILED sub-order against its vendor.
     * Reuses the existing OrderLineItem row — no new order is created — and
     * transitions subOrderStatus from FAILED to PENDING → CONFIRMED or FAILED.
     *
     * Invoked internally by orders-service over Feign; the external endpoint
     * at POST /api/v1/orders/{orderId}/retry/{lineItemId} lives there.
     *
     * @throws org.example.procurementservice.exception.OrderNotFoundException         order id unknown or belongs to another operator
     * @throws org.example.procurementservice.exception.LineItemNotFoundException      line item id is not part of the order
     * @throws org.example.procurementservice.exception.SubOrderRetryNotAllowedException line's current status is not FAILED
     * @throws org.example.procurementservice.exception.VendorUnavailableException     vendor API still unavailable after retry
     */
    LineItemStatusResponse retrySubOrder(Long operatorId, Long orderId, Long lineItemId);

    /**
     * Returns the list of vendors (active and inactive) known to the system.
     * Used by the dashboard to render the override-target picker and to show
     * vendor availability banners.
     */
    List<VendorResponse> listVendors();
}
