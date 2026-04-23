Context

procurement-service has all entities, DTOs, documents, DAOs, Feign clients, and controller endpoints in place, but ProcurementServiceImpl is fully stubbed
(every method throws UnsupportedOperationException). The vendor Feign clients are declared but not wired to circuit breakers or rate limiters —
VendorRateLimiterConfig is referenced in a TODO comment and does not exist. GlobalExceptionHandler is missing.

This plan delivers:
1. The full business logic (plan generation, override, submit, retry, list vendors).
2. Challenge 1 from docs/CloudKitchens_Interview_Prep_Complete.md — per-vendor rate limiting + circuit-breaker wiring + graceful degradation on partial vendor
   failure.
3. Challenge 2 from the same doc — greedy cost optimizer + consolidation post-processing (swap to 2nd-cheapest if it reduces vendor count by 1 and costDelta <
   $5, threshold configurable).

 ---
How Challenge 2 is solved

The optimizer runs in two phases. Inputs: for each catalog item, the set of PriceSnapshots returned by the available vendors (one winning VendorProductResult
per vendor — the lowest unit price).

Phase A — Greedy assignment. For each catalog item, pick the vendor with the lowest lineTotal = unitPrice × preferredQty. Items with no snapshots from any
vendor are dropped (and surfaced in vendorWarnings). This gives the global cost lower bound and is O(items × vendors).

Phase B — Consolidation pass. Goal: fewer invoices without materially higher cost. For every vendor v currently assigned to exactly one item, compute the
delta of moving that item to its next-cheapest vendor already used by another item. If delta < procurement.optimizer.consolidation-threshold (default $5),
perform the swap. Repeat until no profitable swap remains, bounded at items iterations. This is O(items² × vendors) in the worst case — well under 5s for ≤200
items.

The $5 threshold, the "only single-item vendors" rule, and the "only move to an already-used vendor" rule are all written to keep the heuristic transparent
and replaceable with an LP solver later (the doc explicitly anticipates this).

Edge cases handled:
- Vendor completely unavailable (circuit open or all retries failed) → excluded from the snapshot pool; item just competes on remaining vendors.
  vendorWarnings carries the vendor name for the UI banner.
- Item unavailable at every vendor → item is dropped from the plan and its catalogItemId is surfaced (we'll extend vendorWarnings to carry a distinguishable
  message like "No vendor found for item #123").
- All vendors down → VendorUnavailableException → HTTP 503 via GlobalExceptionHandler.

Minimum-order-value is deliberately not modelled (no field on the Vendor entity, and the PRD lists it as a v2 concern).

 ---
How Challenge 1 is solved

Three concerns, one coherent fix:

1. Per-vendor rate limiting — create config/VendorRateLimiterConfig exposing three Semaphore beans:
- amazonRateLimiter — 1 permit, release after 1000 ms.
- walmartRateLimiter — 5 permits, release after 1000 ms.
- krogerRateLimiter — 3 permits, release after 1000 ms (burst-sensitive).

Wrap every vendor Feign call through a thin VendorCallGateway that tryAcquires the correct permit with a timeout, then schedules release on a single-threaded
ScheduledExecutorService. Timeouts are treated as vendor failures (trip the circuit breaker the same as an HTTP failure).
2. Circuit-breaker wiring — add @CircuitBreaker(name = "amazonClient"|"walmartClient"|"krogerClient", fallbackMethod = ...) on the three Feign client methods
   (price search + order submit, four methods per vendor × three vendors). The fallbackMethod returns an empty result for searches (so the vendor is silently
   dropped from the plan) and throws VendorUnavailableException for order submits (so the sub-order is marked FAILED). The breaker names already match
   config-repo/procurement-service.yml.
3. Parallel fan-out, serialized per vendor — PriceAggregator and the order-submission fan-out both group work by vendor, submit one CompletableFuture per
   vendor group to a dedicated priceAggregationExecutor (size 3 = number of vendors), and inside each group calls are sequential (protected by that vendor's
   semaphore). The top-level CompletableFuture.allOf(...) waits on everything with an overall 5-second deadline per the NFR.

 ---
High-level architecture of the new code

┌─ controller/ProcurementController                        (unchanged)
│
├─ service/ProcurementServiceImpl                          (orchestrator — @Transactional boundaries only)
│     ├─ plan()      → PriceAggregator → OptimizerService → PlanDAO.save
│     ├─ override()  → PlanDAO.findByIdAndOperatorId → OptimizerService.recomputeTotals → PlanDAO.save
│     ├─ submit()    → validate → persist Order+Lines (tx) → VendorOrderFanOutService (post-commit) → update statuses
│     └─ retry()     → load line → VendorCallGateway.placeOrder → update status
│
├─ service/PriceAggregator                                 (NEW)
│     - for each vendor:
│         CompletableFuture.supplyAsync(() -> VendorCallGateway.fetchPrices(...), priceAggregationExecutor)
│     - writes PriceSnapshot docs (Mongo) as results arrive
│     - returns snapshots + vendorWarnings (names of vendors that failed)
│
├─ service/OptimizerService                                (NEW — pure function, stateless)
│     - Phase A greedy assignment
│     - Phase B consolidation sweep
│     - computes totalCost, vendorSubtotals, estimatedSavings (vs. single-cheapest-vendor baseline)
│
├─ service/VendorOrderFanOutService                        (NEW)
│     - group OrderLineItems by vendor
│     - per-vendor CompletableFuture, sequential inside group
│     - on success: updateStatusAndRef(lineId, CONFIRMED, vendorOrderRef)
│     - on failure: updateStatusAndRef(lineId, FAILED, null) + record failureReason in response
│
├─ service/VendorCallGateway                               (NEW — single choke point for all vendor I/O)
│     - fetchPrices(vendor, query, qty) → List<VendorProductResult>
│     - placeOrder(vendor, vendorSku, qty) → vendorOrderRef
│     - internally: semaphore acquire → call @CircuitBreaker-wrapped Feign client → schedule release
│
├─ config/VendorRateLimiterConfig                          (NEW — beans: 3× Semaphore, scheduler)
│
├─ config/ExecutorsConfig                                  (NEW — priceAggregationExecutor, vendorFanOutExecutor)
│
├─ config/SecurityConfig + filter/OperatorHeaderAuthFilter (NEW — copy catalog-service pattern;
│                                                           populates SecurityContext from X-Operator-Id header
│                                                           so @AuthenticationPrincipal Long operatorId works)
│
└─ exception/GlobalExceptionHandler                        (NEW — @RestControllerAdvice; maps the 10 existing
exceptions to status codes per their file docs)

No changes to entities, DTOs, documents, DAOs, controller, or Feign client interfaces (circuit-breaker annotation added on their methods only).

 ---
Key files — create or modify

Create

Path: procurement-service/src/main/java/org/example/procurementservice/service/PriceAggregator.java
Purpose: Parallel per-vendor price fetch, writes PriceSnapshots, returns per-item per-vendor best prices and vendor warnings.
────────────────────────────────────────
Path: procurement-service/src/main/java/org/example/procurementservice/service/OptimizerService.java
Purpose: Greedy + consolidation, computes totals and savings. Pure class, no dependencies on DAOs.
────────────────────────────────────────
Path: procurement-service/src/main/java/org/example/procurementservice/service/VendorOrderFanOutService.java
Purpose: Per-vendor grouped order submission, post-commit.
────────────────────────────────────────
Path: procurement-service/src/main/java/org/example/procurementservice/service/VendorCallGateway.java
Purpose: Uniform vendor-call surface: semaphore → circuit-breaker-wrapped Feign → scheduled release. Maps vendor-specific DTOs to VendorProductResult.
────────────────────────────────────────
Path: procurement-service/src/main/java/org/example/procurementservice/config/VendorRateLimiterConfig.java
Purpose: Three Semaphore beans + ScheduledExecutorService for releases.
────────────────────────────────────────
Path: procurement-service/src/main/java/org/example/procurementservice/config/ExecutorsConfig.java
Purpose: priceAggregationExecutor, vendorFanOutExecutor.
────────────────────────────────────────
Path: procurement-service/src/main/java/org/example/procurementservice/config/SecurityConfig.java
Purpose: Mirror of catalog-service SecurityConfig.
────────────────────────────────────────
Path: procurement-service/src/main/java/org/example/procurementservice/filter/OperatorHeaderAuthFilter.java
Purpose: Reads X-Operator-Id, populates SecurityContext so @AuthenticationPrincipal Long operatorId resolves.
────────────────────────────────────────
Path: procurement-service/src/main/java/org/example/procurementservice/exception/GlobalExceptionHandler.java
Purpose: @RestControllerAdvice; map all 10 exceptions + validation errors. Error body matches auth-service/catalog-service ErrorResponse record (create if not

present in procurement-service).
────────────────────────────────────────
Path: procurement-service/src/main/java/org/example/procurementservice/dto/ErrorResponse.java
Purpose: Same record shape used in other services.

Modify

Path: procurement-service/src/main/java/org/example/procurementservice/service/ProcurementServiceImpl.java
Change: Replace all UnsupportedOperationException stubs with real bodies that delegate to the new services. Keep existing @Transactional boundaries. Fill in
the hand-coded toResponse / toPlanDocument helpers.
────────────────────────────────────────
Path: procurement-service/src/main/java/org/example/procurementservice/client/VendorApiClients.java
Change: Add @CircuitBreaker(name=…, fallbackMethod=…) on all 6 Feign methods (3 search + 3 order). Add matching default fallback methods in each interface.
────────────────────────────────────────
Path: config-server/src/main/resources/config-repo/procurement-service.yml
Change: Add procurement.optimizer.consolidation-threshold: 5.00 and procurement.vendor-call.acquire-timeout-ms: 2000. Leave the existing resilience4j block
untouched.

Seeding (one-off SQL)

The vendors table needs three rows (Amazon / Walmart / Kroger) for the JPA Vendor lookups to succeed. Proposed: a schema-seed.sql + data.sql picked up by
Spring Boot on startup, or a CommandLineRunner in ProcurementServiceApplication that inserts if absent. Recommend the CommandLineRunner to avoid cross-service
SQL conflicts on the shared DB.

 ---
Implementation order (bottom-up, each step verifiable)

1. Security + exception handling — add SecurityConfig, OperatorHeaderAuthFilter, ErrorResponse, GlobalExceptionHandler. Verify with GET
   /api/v1/procurement/vendors (once step 3 makes that method work). Without this step @AuthenticationPrincipal is null.
2. Vendor seeding — CommandLineRunner inserts Amazon/Walmart/Kroger if absent.
3. listVendors() in ProcurementServiceImpl — smallest real method; sanity-checks DAO + security filter + exception mapping end-to-end.
4. VendorRateLimiterConfig + ExecutorsConfig + VendorCallGateway — rate-limited, circuit-broken vendor surface. Unit-test with WireMock-backed vendor servers.
5. Circuit-breaker annotations on Feign methods + fallbacks.
6. PriceAggregator — parallel fetch, Mongo snapshot persistence, vendor-warning collection.
7. OptimizerService — greedy then consolidation. Unit tests: cheapest-wins, vendor warnings, consolidation trigger / no-trigger / threshold boundary /
   all-vendors-down.
8. generatePlan() — wire aggregator + optimizer + PlanDAO.save. Map Plan → PurchasePlanResponse.
9. getPlan() — straight PlanDAO.findByIdAndOperatorId + toResponse.
10. overrideVendor() — locate line in plan, require a fresh snapshot (<5 min) else StalePriceSnapshotException, recompute subtotals + total via
    OptimizerService.recomputeTotals(plan), persist.
11. submitOrder() — validate (PlanSubmissionMismatchException if any line differs in vendorId/qty/unitPrice), @Transactional write Order + OrderLineItems with
    subOrderStatus=PENDING, commit, then call VendorOrderFanOutService.fanOut(...) outside the transaction; update each line via
    OrderLineItemDAO.updateStatusAndRef. Compute overall OrderStatus (all CONFIRMED → COMPLETE; any FAILED → PARTIAL_FAILURE). Controller picks 201 vs 207.
12. retrySubOrder() — guard subOrderStatus == FAILED (else SubOrderRetryNotAllowedException), call VendorCallGateway.placeOrder, update status, return
    LineItemStatusResponse.
13. GlobalExceptionHandler — map each exception to its status code from the catalog table (already documented per file).

 ---
Existing code to reuse (don't reinvent)

- catalog-service/src/main/java/org/example/catalogservice/config/SecurityConfig.java + its header filter — copy structure, change package. This is the proven
  operator-header → SecurityContext bridge.
- catalog-service/src/main/java/org/example/catalogservice/exception/GlobalExceptionHandler.java and its ErrorResponse record — copy structure, add handlers
  for procurement's 10 exceptions.
- OrderLineItemDAO.updateStatusAndRef(...) — already @Modifying @Query; use it directly from the fan-out service (its own short transaction per update).
- PriceSnapshotDAO.findFreshSnapshots(operatorId, itemIds, freshAfter) — exactly what overrideVendor needs for the staleness check; use Instant.now().minus(5,
  MINUTES).
- PlanDAO.findByIdAndOperatorId / deleteByIdAndOperatorId — tenant-safe already.
- orders-service/src/main/java/org/example/ordersservice/client/ProcurementRetryClient.java — already calls POST
  /api/v1/procurement/orders/{orderId}/retry/{lineItemId}; my controller path must match exactly (it does — verified).
- JJWT, Lombok @RequiredArgsConstructor, MapStruct available in parent POM; no new dependencies required.

 ---
Verification

Unit tests (JUnit 5 + Mockito)
- OptimizerServiceTest — cheapest-per-item, consolidation triggered, consolidation blocked by threshold, all-vendors-down, single-vendor availability, savings
  calc.
- PriceAggregatorTest — graceful degradation when one vendor throws, warnings collected, snapshots written.
- VendorCallGatewayTest — semaphore actually blocks; timeout → counted as failure; circuit-breaker fallback path.

Integration tests (@SpringBootTest + Testcontainers MySQL + Mongo + WireMock)
- POST /plan full happy path → 200 with populated PurchasePlanResponse.
- POST /plan with one WireMock vendor returning 500 → 200, plan generated from remaining two vendors, vendorWarnings populated.
- PUT /plan/{id}/override with stale snapshot → 409 StalePriceSnapshotException.
- POST /plan/{id}/submit happy path → 201, all subOrderStatus=CONFIRMED, Order.status=COMPLETE.
- POST /plan/{id}/submit with one vendor failing → 207, mixed line statuses, Order.status=PARTIAL_FAILURE.
- POST /orders/{id}/retry/{lineId} on a FAILED line, vendor now healthy → 200, line transitions to CONFIRMED.
- Tenant isolation: operator A's planId returns 404 when operator B tries to read it.

Manual smoke (happy path, services up)
# start: config-server → eureka → auth → catalog → procurement → gateway
curl -X POST :8080/api/v1/auth/login -d '{"email":...,"password":...}'    # capture JWT
curl -X POST :8080/api/v1/catalog/items -H "Authorization: Bearer $T" ... # seed a few items
curl -X POST :8080/api/v1/procurement/plan -H "Authorization: Bearer $T" \
-d '{"catalogItemIds":[1,2,3],"refreshPrices":true}'                 # plan
curl :8080/api/v1/procurement/plan/$PLAN -H "Authorization: Bearer $T"    # read-back
curl -X POST :8080/api/v1/procurement/plan/$PLAN/submit \
-H "Authorization: Bearer $T" -d '{"lineItems":[...]}'               # submit
curl :8080/api/v1/orders -H "Authorization: Bearer $T"                    # orders-service reads shared tables

With placeholder vendor URLs, /plan will return a plan with all three vendors listed in vendorWarnings and zero items (since no vendor responds). That is the
correct graceful-degradation behavior — the test that proves Challenge 1 is wired. For a green end-to-end, point vendor.*.api-base-url at WireMock
(http://localhost:8089/...) or a local stub.

 ---
Out of scope (explicit)

- Replacing greedy with an LP solver (documented hand-off point for v2).
- Minimum-order-value per vendor (no field on entity; v2).
- Async retry queue / SQS-based fan-out (mentioned in doc Section 13 as a later scaling step).
- Frontend dashboard changes.
- Redis-backed JWT blocklist in procurement-service (gateway already handles it).

 ---
Summary of what landed

Challenge 2 — Optimizer (greedy + consolidation)
- service/OptimizerService.java — Phase A picks cheapest vendor per item; Phase B consolidates single-item vendors into already-used vendors when the per-item
  delta is strictly below procurement.optimizer.consolidation-threshold (default $5, configurable). Also computes the single-cheapest-vendor baseline →        
  estimatedSavings. Pure class, no DB/DI beyond the threshold — trivially unit-testable and swappable for an LP solver later.

Challenge 1 — Vendor I/O (rate-limited, circuit-broken, graceful)
- config/VendorRateLimiterConfig.java — Semaphores (1 / 5 / 3 permits for Amazon / Walmart / Kroger) + ScheduledExecutorService that releases a permit 1 s    
  after each call.
- config/ExecutorsConfig.java — priceAggregationExecutor and vendorFanOutExecutor (one worker per vendor).
- service/VendorCallGateway.java — the single choke point. Semaphore acquire (with configurable timeout) → CircuitBreaker.executeSupplier(...) using the      
  existing amazonClient/walmartClient/krogerClient instances → scheduled release. Returns Optional.empty() for unavailable-search (graceful); throws            
  VendorUnavailableException for order placement (sub-order → FAILED).
- service/PriceAggregator.java — parallel per-vendor fetch; persists PriceSnapshots (TTL 6h); collects vendorWarnings; warm-cache reuse when                  
  refreshPrices=false; 5 s aggregation deadline per the NFR.
- service/VendorOrderFanOutService.java — groups submitted lines by vendor; parallel across vendors, serial within; per-line updateStatusAndRef in its own    
  short tx; a retrySingle path for the retry endpoint.

Business logic
- service/ProcurementServiceImpl.java — all six methods implemented. submitOrder is not @Transactional; a TransactionTemplate wraps only the Order+Lines write
  and the final status bump, so no DB locks are held across vendor HTTP I/O. retrySubOrder uses the same pattern. validateSubmissionMatchesPlan diffs the      
  submitted lines against the stored Plan (tenant-safe).

Surrounding plumbing
- filter/OperatorHeaderAuthFilter.java + config/SecurityConfig.java — lifts gateway's X-Operator-Id into the SecurityContext so @AuthenticationPrincipal Long
  operatorId actually resolves (previously broken in this module).
- exception/GlobalExceptionHandler.java + dto/ErrorResponse.java — maps the 10 procurement exceptions to proper status codes (404/409/502/503/400/500) with
  the uniform ErrorResponse body used by auth- and catalog-service.
- config/VendorSeeder.java — idempotent CommandLineRunner inserting Amazon/Walmart/Kroger on startup.
- dao/OrderLineItemDAO.java — added @Transactional to the @Modifying updateStatusAndRef so fan-out can call it from outside a tx.
- client/VendorApiClients.java — promoted the three innermost nested records to public so cross-package mapping compiles.
- config-repo/procurement-service.yml — added procurement.optimizer.consolidation-threshold: 5.00 and procurement.vendor-call.acquire-timeout-ms: 2000.

What's next (not done, worth flagging):
- No unit or integration tests written. The plan's test matrix (OptimizerServiceTest, PriceAggregatorTest, WireMock-backed controller tests) is still TODO.
- With the placeholder vendor URLs, POST /plan will correctly surface all three vendors in vendorWarnings and throw 503 (VendorUnavailableException) because  
  no items get snapshots — that's the graceful-degradation behavior, but to see a green end-to-end you'd point vendor.*.api-base-url at a local WireMock.
- ./mvnw test will run the scaffolded *ApplicationTests classes only; no new failures introduced.