# CloudKitchens Interview Preparation — Smart Procurement Optimizer
**Full Story Script | Project 1 of 3**

---

> This document is your complete interview story for the CloudKitchens role (Sep 2023 – present). Read each section as a narrative script. Diagrams are in the companion visual file.

---

## TABLE OF CONTENTS

1. High-Level Project Description
2. Product Requirements Doc (PRD)
3. Use Case Overview (see diagram)
4. Architecture Overview (see diagram)
5. Database Schema Design + ERD (see diagram)
6. Sequence Diagram (see diagram)
7. API Endpoints
8. Testing Strategy
9. Challenging Parts
10. Production Support / Debugging Scenarios
11. Deployment / PR Process
12. Monitoring
13. Scaling Strategy
14. Agile Team & SDLC
15. Traffic (TPS)

---

# SECTION 1 — HIGH-LEVEL PROJECT DESCRIPTION

## Company Domain

CloudKitchens is a Los Angeles-based company operating in the **ghost kitchen** (dark kitchen) industry. It provides:

- **Kitchen Infrastructure**: Turnkey, shared commercial kitchen spaces that food operators rent without the overhead of a traditional restaurant. Operators produce delivery-only meals from these hubs.
- **Software Platform**: A suite of SaaS tools covering order management, kitchen display systems, inventory tracking, and supplier procurement — all purpose-built for delivery-first food operators.
- **Marketplace Access**: Integration with major delivery platforms (Uber Eats, DoorDash, Grubhub) so tenants can reach customers across channels from a single kitchen.

CloudKitchens lowers the barrier to entry for food entrepreneurs by eliminating front-of-house costs and providing shared operational infrastructure with built-in software.

## Who Are the Users?

| User Type | Description |
|---|---|
| Kitchen Operators / Tenants | Food & beverage entrepreneurs or restaurant brands running delivery-only operations inside a CloudKitchens facility. |
| Procurement Managers | Staff within larger operator companies responsible for ordering ingredients and supplies across multiple locations. |
| CloudKitchens Internal Staff | Operations team members who monitor purchasing activity and negotiate vendor contracts. |

## Customer Pain Points

| Pain | Detail |
|---|---|
| Fragmented Purchasing | Operators must manually check Amazon, Walmart, Kroger, etc. separately for the same item — wasting hours weekly. |
| Price Volatility | Grocery prices fluctuate frequently; operators overpay because they don't know a cheaper option existed at that moment. |
| No Spend Visibility | Without a unified procurement system, operators have no central record of what they spent or whether they got a good deal. |
| Manual Order Errors | Copy-pasting across vendor websites leads to wrong SKUs, quantities, and missed substitutions. |
| Scaling Difficulty | As operators expand to multiple kitchen locations, coordinating purchasing manually becomes unsustainable. |

## The Big Problem We Solve

> **How can a food operator automatically find the cheapest combination of grocery purchases across multiple vendor platforms — and execute the order — without spending hours manually comparing prices?**

## Core Feature Set: Smart Procurement Optimizer

| Feature | Description |
|---|---|
| Ingredient Catalog | Operators maintain a standard list of items they regularly purchase. |
| Multi-Vendor Price Fetch | The system queries Amazon, Walmart, and Kroger APIs in real time. |
| Cost-Optimization Engine | Computes the globally cheapest vendor assignment per item. |
| Purchase Plan Dashboard | React SPA showing the recommended plan, savings, and vendor breakdown. |
| Order Submission | Operator confirms plan; system fans out orders to vendor APIs. |
| Order History & Analytics | Past orders, spend trends, and total savings generated. |
| Authentication & Multi-Tenancy | JWT-based login; data scoped to each operator's tenant. |

---

# SECTION 2 — PRODUCT REQUIREMENTS DOCUMENT

## Developer Scenario

**Team Composition:**

| Role | Count |
|---|---|
| Full-Stack Engineers | 4 (including me) |
| Backend Engineers | 2 |
| QA Engineer | 1 |
| DevOps Engineer | 1 |
| Product Manager | 1 |
| Tech Lead / Architect | 1 |
| **Total** | **10** |

I was a **Full-Stack Engineer** owning the Procurement Service (backend) and the Procurement Dashboard (React SPA frontend).

**Team Goal:** Deliver a production-ready Smart Procurement Optimizer that reduces ingredient purchasing costs for CloudKitchens operators, within one business quarter.

**Features:**
1. Operator authentication (JWT-based)
2. Ingredient catalog management (CRUD)
3. Vendor price aggregation (Amazon, Walmart, Kroger)
4. Cost optimization engine (cheapest vendor per item)
5. Purchase plan dashboard (React)
6. Order submission (vendor API fan-out)
7. Order history and analytics

**Constraints:**

| Constraint | Detail |
|---|---|
| Deadline | 12-week delivery (3 sprints × 4 weeks) |
| Scale Target | 500 concurrent operators at launch; designed for 5,000 |
| Security | No PII shared between tenants; JWT expiry enforced |
| Vendor API Rate Limits | Amazon: 1 req/sec; Walmart: 5 req/sec; Kroger: burst-sensitive |
| Uptime SLA | 99.9% internal SLA |

## Functional Requirements

**FR-01: Authentication & Authorization**
The system must allow operators to register and log in with email + password. On successful login, the system must issue a signed JWT (HS256, 24h expiry). All protected API endpoints must reject requests with missing or expired tokens. All data access must be scoped to the authenticated operator's tenant ID.

**FR-02: Ingredient Catalog**
Operators must be able to create, read, update, and delete catalog items. Each catalog item stores: name, category, unit of measure, preferred quantity. Bulk import via CSV upload must be supported.

**FR-03: Vendor Price Aggregation**
The system must query at least 3 vendor APIs for current prices. Price data must be refreshed on-demand when an operator requests a new plan. If a vendor API is unavailable, the system must gracefully degrade (exclude that vendor, surface a warning) without failing the entire plan generation.

**FR-04: Cost Optimization Engine**
Given a list of catalog items and current vendor prices, the engine must produce the cheapest vendor assignment per item. The plan must include total cost, per-vendor subtotals, and savings vs. single-vendor baseline. Plan generation must complete within 5 seconds for catalogs up to 200 items.

**FR-05: Purchase Plan Dashboard**
The React SPA must display the recommended plan in a clear tabular format. Operators must be able to override any item's assigned vendor before submitting. The dashboard must show a real-time savings summary.

**FR-06: Order Submission**
On operator confirmation, the system must fan out orders to the respective vendor APIs. Each sub-order's status must be tracked independently (pending / confirmed / failed). If a sub-order fails, the operator must be notified and offered a retry.

**FR-07: Order History**
The system must persist all submitted orders with line-item detail. Operators must be able to filter order history by date range, vendor, and item. Spend summary (total + savings last 30 days) must be exposed.

## Non-Functional Requirements

**Performance:**
- API response time p95: < 200ms for CRUD operations
- Purchase plan generation end-to-end: < 5 seconds
- Dashboard page load (LCP): < 2 seconds
- Database query time (indexed): < 50ms

**Security:**
- All communication over HTTPS (TLS 1.2+)
- JWT stored in localStorage; attached as Authorization: Bearer header on all API calls
- Passwords hashed with BCrypt (cost factor 12)
- Spring Boot Security applied to all non-public endpoints
- Input validation on all API inputs; parameterized queries / HQL to prevent injection
- Tenant data isolation enforced at service layer

**Availability:**
- Uptime SLA: 99.9% (< 8.7 hours downtime/year)
- RDS: Multi-AZ deployment for automatic failover
- Eureka with health-check heartbeats (30s interval)
- Resilience4j circuit breaker on all vendor API Feign clients

**Scalability:**
- Microservices independently deployable and scalable
- Stateless services behind API Gateway; horizontal scaling via AWS ECS Auto Scaling
- MongoDB sharded by operatorId for price snapshot collection
- MySQL read replica for analytics queries

## EQ Questions

**Q: What was your team's responsibility?**
"Our team owned the entire Smart Procurement Optimizer product — from requirements gathering and backend microservices to the React frontend and DevOps pipeline. We did not have a separate platform team; we owned our own infrastructure on AWS. The team followed Agile/Scrum with 2-week sprints and daily standups."

**Q: What was your personal responsibility and work scope?**
"I was a full-stack engineer. On the backend, I owned the Procurement Service — specifically the price aggregation layer, the optimization engine, and the order submission workflow. I built the Repository, Service, and Controller layers in Spring Boot, wrote HQL queries for MySQL order persistence, and integrated Feign clients to call vendor APIs through the gateway. On the frontend, I built the Purchase Plan Dashboard in React — components like the item table, vendor selector, savings summary card, and order confirmation modal — using TailwindCSS, React Router, and Axios for API calls. I also set up the CI/CD pipeline in Jenkins and Dockerized the services."

**Q: Where did you get the requirements from?**
"Requirements came from two main sources. First, the Product Manager synthesized input from internal CloudKitchens operations staff and feedback from early operator tenants about their procurement pain points. The PM wrote user stories in Jira, which we refined in sprint planning. Second, for technical constraints like vendor API rate limits and AWS budget caps, the Tech Lead worked directly with vendor API documentation and our infrastructure team. I participated in sprint planning and backlog grooming to clarify acceptance criteria, and I communicated directly with the PM when I found ambiguities during development."

---

# SECTION 3 — USE CASE DIAGRAM

*(See the Use Case Diagram visual)*

**Actors:**
- **Operator** (primary user): Interacts with all system features — login, catalog management, plan generation, vendor override, order submission, order history, and analytics.
- **Vendor APIs** (external systems — Amazon, Walmart, Kroger): Receive price queries from the Procurement Service (during plan generation) and receive order submissions from the Orders Service.

**Use Cases by actor:**

*Operator:*
- Login / authenticate
- Manage catalog items (CRUD + bulk import)
- Generate purchase plan
- Override vendor assignment
- Submit order
- View order history
- View spend analytics

*Vendor APIs (Amazon / Walmart / Kroger):*
- Respond to price queries (interacts with "Generate purchase plan")
- Receive and confirm sub-orders (interacts with "Submit order")

---

# SECTION 4 — ARCHITECTURE DIAGRAM

*(See the Architecture Diagram visual)*

**Request flow:**

1. **React SPA** sends HTTPS request with JWT in the Authorization header.
2. **API Gateway** (Spring Cloud Gateway) intercepts the request, validates the JWT via the Spring Boot Security filter, and routes to the appropriate microservice via Eureka service discovery.
3. **Eureka Server** maintains a registry of all running service instances. The Gateway resolves the correct host:port dynamically, enabling load balancing and failover.
4. The targeted **microservice** (Auth, Catalog, Procurement, or Orders) processes the request. Services communicate with each other via Feign clients (also routed through Eureka).
5. **Vendor APIs** (Amazon, Walmart, Kroger) are called by the Procurement Service via Feign clients wrapped in Resilience4j circuit breakers.
6. Data is persisted in **MySQL RDS** (structured relational data) and **MongoDB** (vendor price snapshots).
7. The **CI/CD pipeline** (Jenkins + Docker + AWS ECS) manages building, testing, and deploying each service independently.

**Security layers:**
- TLS 1.2+ on all external traffic
- JWT filter at the Gateway (short-circuits unauthorized requests before they reach services)
- BCrypt password hashing in Auth Service
- Tenant-scoped queries in every service (every DB query includes `operatorId` predicate)
- Spring Boot Security annotations on controller methods

**Tech stack:**
- Java 17 (all Spring Boot services)
- Spring Cloud (Gateway, Eureka, Feign Client)
- React (JavaScript ES6, JSX, Redux, React Router Dom)
- TailwindCSS 3, Axios
- MySQL (AWS RDS), MongoDB
- Hibernate HQL
- Docker, Jenkins, AWS ECS
- JUnit 5, Mockito
- Apache Maven
- Google Gson (Java object serialization)
- Git, GitHub, Postman

---

# SECTION 5 — DATABASE SCHEMA & DESIGN

*(See the ERD visual)*

## Overview: Polyglot Persistence

| Database | Engine | Purpose |
|---|---|---|
| MySQL (AWS RDS) | Relational / SQL | Operators, catalog items, orders, order line items — structured transactional data requiring ACID guarantees |
| MongoDB | Document / NoSQL | Vendor price snapshots — schema-flexible, high-write, time-series documents that vary per vendor |

## MySQL Tables

**operators**: id (PK), email (UNIQUE), password_hash, company_name, status, created_at, updated_at. Index on email, status.

**vendors**: id (PK), name, api_base_url, is_active.

**catalog_items**: id (PK), operator_id (FK), name, category, unit, preferred_qty, created_at. Index on operator_id, composite (operator_id, category).

**orders**: id (PK), operator_id (FK), status, total_cost, estimated_savings, submitted_at, created_at. Index on operator_id, (operator_id, submitted_at), status. Partition by submitted_at year for large datasets.

**order_line_items**: id (PK), order_id (FK), catalog_item_id (FK), vendor_id (FK), quantity, unit_price, line_total, vendor_order_ref, sub_order_status. Index on order_id, vendor_id, catalog_item_id.

## MongoDB Collection: price_snapshots

Each document represents a vendor's response for one catalog item at one point in time. Key fields: operatorId, catalogItemId, vendorId, queriedAt, results (array of product hits), bestPrice, ttlExpiry. TTL index auto-deletes documents after 6 hours. Primary query index: { operatorId, catalogItemId, vendorId, queriedAt }.

## SQL Design Considerations

**ACID Guarantees**: MySQL InnoDB provides full ACID compliance. Order submission is wrapped in a single transaction — either all line items are persisted or none are.

**Transactions and Isolation Levels**: Order submission uses the default REPEATABLE READ. Analytics reads on the read replica use READ COMMITTED. Each vendor sub-order status update runs as its own short transaction.

**Index Design**: Covering index on (operator_id, submitted_at, status) for order history queries. All FK columns are indexed. No index on password_hash or api_base_url — never queried by value.

**Sharding and Replication**: AWS RDS Multi-AZ provides synchronous replication to a standby replica. A separate read replica handles analytics queries.

**Write & Read Separation**: Write path → primary RDS. Read path (analytics, order history list) → read replica via Spring DataSource routing.

## NoSQL Design Considerations

**Eventual Consistency**: Write concern w: majority; read preference primaryPreferred. Price snapshots accept slightly stale data (UI always shows queriedAt timestamp).

**Sharding Keys**: Collection sharded by { operatorId, catalogItemId }. Co-locates all price data for one operator's plan generation on the same shard.

**Query Access Patterns**: Primary pattern is "latest price snapshot per vendor per item for this operator's basket." Compound index covers this fully.

**CAP Theorem Trade-offs**: Prioritizes CP (Consistency + Partition Tolerance) for writes (majority write concern). Accepts AP (Availability over strict Consistency) for reads — slightly stale price data is far better than an unavailable planning tool.

---

# SECTION 6 — SEQUENCE DIAGRAM

*(See the Sequence Diagram visual)*

## Happy Path Summary

**Phase 1 — Authentication:**
Operator posts credentials → Gateway forwards to Auth Service → BCrypt validation → JWT issued → stored in client localStorage.

**Phase 2 — Generate Purchase Plan:**
Operator requests plan with JWT → Gateway validates JWT → Procurement Service loads catalog from MySQL → fans out parallel Feign calls to Amazon, Walmart, Kroger (rate-controlled, circuit breakers) → saves price snapshots to MongoDB → runs optimization algorithm → returns plan with vendor assignments and savings estimate.

**Phase 3 — Submit Order:**
Operator confirms plan → Gateway → Procurement Service begins a MySQL transaction (INSERT orders + line items) → fans out sub-orders to each vendor API → each sub-order status tracked independently (CONFIRMED or FAILED) → UPDATE sub_order_status per line item → response returns orderId + statuses.

## Payload Structure

Plan request: `{ catalogItemIds: [1,2,3], refreshPrices: true }`

Plan response: `{ planId, items: [{ catalogItemId, vendorName, unitPrice, qty, lineTotal }], totalCost, estimatedSavings }`

Order response: `{ orderId, lineItems: [{ id, vendorOrderRef, subOrderStatus }] }`

## Compensations and Retries

If a vendor sub-order fails (HTTP 500 or timeout), the system marks that line item as FAILED in MySQL. The operator is notified on the dashboard. A retry endpoint (`POST /orders/{id}/retry/{lineItemId}`) re-attempts just that line item without re-running the full plan. If the overall MySQL transaction fails (network error during INSERT), a ROLLBACK ensures no partial order is created.

## Observability

Each request receives a Spring Cloud Sleuth `traceId` that propagates through all Feign client calls. Logs are searchable by traceId to reconstruct the complete call chain across all microservices.

---

# SECTION 7 — API ENDPOINTS

All endpoints prefixed `/api/v1`. JWT required on all except `/auth/**`.

**Auth Service (/auth)**

| Method | Path | Description |
|---|---|---|
| POST | /auth/register | Register new operator |
| POST | /auth/login | Login, returns JWT |
| POST | /auth/logout | Clear token (client-side) |

**Catalog Service (/catalog)**

| Method | Path | Description |
|---|---|---|
| GET | /catalog/items | List all operator catalog items |
| POST | /catalog/items | Create new catalog item |
| GET | /catalog/items/{id} | Get single catalog item |
| PUT | /catalog/items/{id} | Update catalog item |
| DELETE | /catalog/items/{id} | Delete catalog item |
| POST | /catalog/items/bulk | Bulk import from CSV |

**Procurement Service (/procurement)**

| Method | Path | Description |
|---|---|---|
| POST | /procurement/plan | Generate purchase plan |
| GET | /procurement/plan/{planId} | Get a previously generated plan |
| PUT | /procurement/plan/{planId}/override | Override vendor for a line item |
| POST | /procurement/plan/{planId}/submit | Submit order (fan-out to vendors) |

**Orders Service (/orders)**

| Method | Path | Description |
|---|---|---|
| GET | /orders | List order history (paginated, filterable) |
| GET | /orders/{id} | Get order detail with all line items |
| GET | /orders/{id}/status | Get live sub-order statuses |
| POST | /orders/{id}/retry/{lineItemId} | Retry a failed sub-order |

**Analytics Service (/analytics)**

| Method | Path | Description |
|---|---|---|
| GET | /analytics/spend-summary | Total spend + savings (30/90 days) |
| GET | /analytics/spend-by-vendor | Spend breakdown per vendor |
| GET | /analytics/price-trends/{itemId} | Price history for a catalog item |

---

# SECTION 8 — TESTING STRATEGY

## Unit Testing (JUnit 5 + Mockito)
Scope: Service layer logic — optimization engine, price comparison calculations, JWT token validation, order total computation. All Repository dependencies mocked with Mockito. Tests cover edge cases: empty vendor results, single-vendor fallback, zero-item catalog, expired JWT. Coverage target: 80%+ line coverage on Service and utility classes.

## Integration Testing (Spring Boot Test + H2)
Scope: Controller → Service → Repository stack. Uses @SpringBootTest with an embedded H2 database. Tests the full HTTP request-response cycle via MockMvc. Verifies unauthorized requests return 401 and that tenant isolation works (operator A cannot see operator B's data).

## API Testing (Postman + Newman)
A Postman collection covers all endpoints with sample payloads. Newman (Postman CLI) runs the collection in the Jenkins CI pipeline on every PR merge to main. Tests cover: login flow, full plan-generate-submit cycle, order history retrieval.

## Contract Testing (Wiremock)
Feign client interfaces are tested with Wiremock stubs simulating vendor API responses, including error cases (500, timeout, rate limit 429).

## Frontend Testing (React Testing Library)
Unit tests for reusable UI components (Autocomplete, Button, Spinner, ItemTable). Snapshot tests to catch unintended rendering regressions. Mocked Axios calls for dashboard data-fetching hooks.

---

# SECTION 9 — CHALLENGING PARTS

## Challenge 1: Vendor API Rate Limiting & Partial Failure Handling

**Problem**: The three vendor APIs have different rate limits (Amazon: 1 req/sec, Walmart: 5 req/sec, Kroger: burst-sensitive). For a catalog of 100+ items, naively calling each vendor sequentially is too slow (exceeding 100 seconds). Calling them all in parallel risks HTTP 429 rate limit errors.

**Solution**: I implemented a per-vendor request queue with configurable throttle rates using a Semaphore-based token bucket per vendor Feign client. Calls to each vendor were batched and rate-controlled independently. For partial failures, I used Resilience4j's circuit breaker on each Feign client — if a vendor returned 3 consecutive failures, the circuit opened for 30 seconds, that vendor was excluded from the plan, and the UI displayed a warning banner. This preserved plan generation for the available vendors.

## Challenge 2: Optimization Engine Correctness vs. Performance

**Problem**: The cost-optimization problem sounds simple (always pick cheapest per item) but has subtleties: some vendors have minimum order values, some items may be unavailable at a given vendor, and some operators want to minimize the number of vendors (fewer invoices) even at slightly higher cost.

**Solution**: I modeled the plan generation as a constrained assignment problem. For the MVP, we implemented a greedy algorithm with a post-processing consolidation step: if switching one item from its cheapest vendor to the second-cheapest reduces total vendor count by 1 and the cost delta is less than $5, we consolidate. This was validated against test fixture catalogs and gave results operators found intuitive. We documented the algorithm so future iterations could swap in a proper LP solver if needed.

---

# SECTION 10 — PRODUCTION SUPPORT / DEBUGGING SCENARIOS

## Scenario 1: Plan Generation Silently Returns Stale Prices

**Symptom**: An operator reports that the generated plan showed a price from yesterday for a Kroger item, even after requesting a fresh plan.

**Root Cause**: MongoDB's TTL index background task runs every 60 seconds, so expired snapshots could persist briefly. More critically, the Kroger API was returning HTTP 200 with an empty body, which our deserialization treated as "no results" rather than an error. The system then fell back to the cached snapshot (past TTL but not yet physically deleted).

**Short-term Fix** (~4–6 hours, deployable same day):

Two targeted patches that stop the bleeding without restructuring anything:

1. **Explicit staleness guard in `ProcurementService`** — before using any snapshot returned from MongoDB, compare its `queriedAt` against `Instant.now()`. If the gap exceeds a configurable threshold (default 5 minutes, controlled via `procurement.snapshot.max-age-seconds` in `config-repo/procurement-service.yml`), discard it and force a live Feign call regardless of the TTL field. This closes the 60-second reaper-lag window: even if MongoDB has not yet physically deleted the expired document, the application-level guard prevents it from being used.

   ```java
   // ProcurementService.java — inside getOrFetchSnapshot()
   if (cached != null
       && Duration.between(cached.getQueriedAt(), Instant.now()).getSeconds()
          > maxAgeSeconds) {
       cached = null;   // treat as cache miss; fall through to Feign call
   }
   ```

2. **Strict empty-body validation in the Kroger Feign decoder** — add a custom `ErrorDecoder` (or `ResponseInterceptor`) for `KrogerClient` that inspects the response before deserialization. If the body is absent or the `results` array is empty, throw a typed `VendorEmptyResponseException`. Resilience4j's circuit breaker is already configured to count this exception family as a failure, so three consecutive empty responses will open the circuit and surface the issue on the next plan request rather than silently serving stale data.

   ```java
   // KrogerResponseDecoder.java
   KrogerPriceResponse body = objectMapper.readValue(stream, KrogerPriceResponse.class);
   if (body == null || body.getResults() == null || body.getResults().isEmpty()) {
       throw new VendorEmptyResponseException("Kroger returned empty results for " + itemId);
   }
   ```

Why this works short-term: it addresses both failure modes (TTL reaper lag and silent empty-body fallback) with surgical, low-risk changes — no schema migration, no new infrastructure. The existing Resilience4j and MongoDB stack is unchanged.

**Long-term Fix** (~1–2 sprints):

The short-term patches are band-aids on a deeper design gap: the system has no clear contract for what "a valid vendor response" means, and it relies on MongoDB's TTL reaper as its only cache-eviction mechanism. The durable fix closes both gaps:

1. **Vendor response contract tests (Sprint 1, ~3 days)** — add Wiremock stubs for all three vendor clients (Amazon, Walmart, Kroger) that cover the full error surface: HTTP 200 with empty body, HTTP 200 with malformed JSON, HTTP 429, HTTP 500, and connection timeout. Run these in CI on every PR. This forces every future change to the Feign decoder to explicitly handle the empty-body case rather than discovering it in production.

2. **Uniform empty-response policy across all vendors (Sprint 1, ~1 day)** — extend the `VendorEmptyResponseException` guard to `AmazonClient` and `WalmartClient` as well. Add a shared base decoder that the vendor-specific decoders extend, so the policy is defined once and inherited — not copy-pasted.

3. **Application-level cache invalidation separate from TTL (Sprint 2, ~4 days)** — stop using MongoDB TTL as the primary freshness signal. Add an explicit `isValid` flag and a `staleBefore` timestamp to each `PriceSnapshot` document. When a `refreshPrices: true` plan request arrives, mark all existing snapshots for this operator as stale (bulk update) before fetching. This makes freshness a deliberate write, not a race against a background reaper, and allows targeted invalidation (e.g., invalidate only Kroger snapshots when the Kroger circuit opens).

4. **Observability metric for cache-hit anomalies (Sprint 2, ~1 day)** — emit a Micrometer counter (`procurement.snapshot.cache_hit` vs `procurement.snapshot.cache_miss`) on every lookup. Wire a CloudWatch alarm that fires if the hit ratio exceeds 95% for more than 10 minutes — a sign that re-fetch is being silently skipped. This would have detected this incident before the operator noticed.

Why this prevents the bug long-term: contract tests catch silent-empty-body regressions before they ship; the uniform policy means no vendor can silently degrade; explicit invalidation removes reliance on the 60-second reaper window entirely; and the alarm makes any future drift in cache behavior visible to the on-call engineer within minutes rather than days.

## Scenario 2: Order History List Slows Down as Order Volume Grows

**Symptom**: During load testing with a realistic dataset, `GET /api/v1/orders` (the order history list) was taking 800ms+ for a page of 20 orders — far above the 200ms p95 target. The endpoint is read-only and has no complex aggregation, so the latency made no sense.

**Discovery**: Enabled SQL logging in `config-repo/orders-service.yml` (`spring.jpa.show-sql: true`, `logging.level.org.hibernate.SQL: DEBUG`). Immediately visible in the log: a page of 20 orders produced 21 SQL statements — one `SELECT` for the orders page, then one `SELECT … FROM order_line_items WHERE order_id = ?` per row. Latency scaled linearly with page size.

**Root Cause**: `OrderMapper.toSummary()` contains a MapStruct expression `java(orderView.getLineItems().size())` to populate the `lineItemCount` field. `OrderView.lineItems` is declared `@OneToMany(fetch = FetchType.LAZY)`, so each `.size()` call triggers a separate round-trip to MySQL. The `findAllByOperatorId` repository method returns plain `OrderView` entities with no JOIN FETCH, leaving every collection uninitialized. With a page size of N, the result is N+1 database queries.

**Fix**: Replaced the entity-load approach with a JPQL constructor projection that computes the count in a single query, so `lineItems` is never loaded at all:

```java
// OrderViewDAO — replaces findAllByOperatorId for the list endpoint
@Query("""
    SELECT new org.example.ordersservice.dto.OrderSummaryResponse(
        o.id, o.status, o.totalCost, o.estimatedSavings, o.submittedAt, COUNT(li))
    FROM OrderView o LEFT JOIN o.lineItems li
    WHERE o.operatorId = :operatorId
    GROUP BY o.id, o.status, o.totalCost, o.estimatedSavings, o.submittedAt
    """)
Page<OrderSummaryResponse> findSummariesByOperatorId(
        @Param("operatorId") Long operatorId, Pageable pageable);
```

`OrdersServiceImpl.listOrders()` now calls this method directly instead of loading `OrderView` entities and mapping them. The single SQL query returns the count as an aggregate — no secondary queries, no MapStruct expression touching a lazy collection.

**Verifying the fix**: Integration test using Testcontainers (real MySQL). Insert 20 `OrderView` rows with 3 line items each via `procurement-service`'s write-side entities, then call the list endpoint and capture Hibernate statistics:

```java
SessionFactory sf = entityManagerFactory.unwrap(SessionFactory.class);
sf.getStatistics().setStatisticsEnabled(true);
sf.getStatistics().clear();

mockMvc.perform(get("/api/v1/orders").header("X-Operator-Id", operatorId))
       .andExpect(status().isOk())
       .andExpect(jsonPath("$.content[0].lineItemCount").value(3));

assertThat(sf.getStatistics().getQueryExecutionCount()).isEqualTo(2); // 1 data + 1 count for Page
```

Before the fix this assertion fails at 22. After the fix it passes at 2.

---

# SECTION 11 — DEPLOYMENT / PR PROCESS

## Git Workflow
Branching: `main` (production) → `develop` (integration) → feature branches (e.g. `feature/JIRA-123-plan-generation`). PR requirements: at least 1 peer review approval + all CI checks green before merge to develop. Release: `develop` → `main` weekly via a release PR reviewed by the Tech Lead.

## CI/CD Pipeline (Jenkins)
1. Git push triggers Jenkins
2. Build: `mvn clean package` (Java), `npm run build` (React)
3. Unit tests: `mvn test`, `npm test`
4. Integration tests: H2-backed Spring Boot tests
5. Postman/Newman: API contract tests
6. Docker build: `docker build -t cloudkitchens/service-name:${GIT_SHA}`
7. Docker push to ECR registry
8. Deploy to Staging: ECS task definition update
9. Smoke tests: health check endpoints
10. Manual gate: PM/QA approval for production deploy
11. Deploy to Production: ECS rolling update (zero downtime)

## Zero-Downtime Deployment
ECS rolling updates replace one task at a time, keeping minimum 50% healthy. ALB health checks ensure traffic only routes to healthy tasks. Rollback: re-deploy previous ECR image tag.

---

# SECTION 12 — MONITORING

## Metrics (AWS CloudWatch + Spring Boot Actuator)

| Metric | Alert Threshold |
|---|---|
| API p95 response time | > 500ms → PagerDuty alert |
| Error rate (5xx) | > 1% over 5 min → PagerDuty |
| Vendor API circuit breaker OPEN | Immediate Slack alert |
| RDS CPU utilization | > 80% for 5 min → warning |
| MongoDB op latency (p99) | > 200ms → warning |
| ECS task health | Any task unhealthy → alert |
| JVM heap usage | > 85% → alert |

## Logs (AWS CloudWatch Logs)
Structured JSON logging via Logback. Every request logged with: requestId, operatorId, endpoint, latencyMs, statusCode. Log levels: ERROR for unhandled exceptions, WARN for circuit breaker events, INFO for order submissions, DEBUG disabled in production.

## Distributed Tracing
Spring Cloud Sleuth adds traceId and spanId to every request, propagated through all Feign client calls between microservices. Logs are searchable by traceId to reconstruct the full call chain.

## Health Checks
Each Spring Boot service exposes `/actuator/health`. Eureka uses this endpoint for service registration health. ALB target group health check hits it every 30 seconds.

---

# SECTION 13 — SCALING STRATEGY

**step 1 — vertical scaling**: right-size ecs task cpu/memory based on observed utilization. upgrade rds instance class if db cpu is the bottleneck.

**step 2 — horizontal scaling (services)**: stateless spring boot services scale horizontally. ecs auto scaling: add tasks when average cpu > 70% for 2 consecutive minutes; remove when < 30%. eureka auto-discovers new instances.

**step 3 — database read scaling**: add rds read replicas for the analytics service. route all read-heavy queries to read replicas via spring's abstractroutingdatasource.

**step 4 — caching layer**: add redis (elasticache) in front of price snapshot lookup. cache operator catalog items in redis (ttl 5 min) to reduce mysql reads on every plan generation.

**step 5 — vendor api fan-out parallelism**: move vendor api calls from synchronous request-scoped threads to an async task queue (spring @async with a bounded thread pool, or aws sqs for full decoupling).

**step 6 — event-driven order submission**: replace synchronous order fan-out with sqs-based event-driven flow — one ordersubmitted event per line item; per-vendor consumers process at their own rate, retry on failure, update status via callback.

**step 7 — multi-region (future)**: deploy to a second aws region (us-east-1 mirror) with rds global database for < 1 second replication lag. route traffic via route 53 latency-based routing.

Step 1 — Horizontal scaling of stateless services

What to scale first: procurement-service → orders-service → catalog-service → auth-service

Each service is stateless (no in-process state; JWT auth; operatorId-scoped queries), so adding instances costs almost nothing architecturally.

Each replica self-registers in Eureka under the same spring.application.name. The gateway's lb://PROCUREMENT-SERVICE already round-robins across them.

Switch the gateway discovery from Eureka to Kubernetes DNS (lb://procurement-service still works via Spring Cloud Kubernetes or a Kubernetes Service).

Step 2 — Scale the API Gateway itself

The gateway is reactive (WebFlux) and handles high concurrency per instance. To scale it:

1. Put a load balancer (NGINX, AWS ALB, or a Kubernetes LoadBalancer Service) in front of multiple gateway replicas.
2. The rate-limiter is Redis-backed — all gateway replicas share the same counters automatically. No change needed.
3. Tune the current rate limits in config-repo/api-gateway.yml as you measure actual traffic:                                                                 
   - procurement-service: currently 5 req/s burst 10 — this is the tightest constraint; raise only after confirming vendor APIs can handle it.
                                                                                                                                                       
Step 3 — Scale the databases

MySQL (the main bottleneck at high load):

┌──────────────────────────┬──────────────────────────────────────────────────────────────────────────┬───────────────────────────────────────────────────┐
│        Technique         │                                   How                                    │                       When                        │   
├──────────────────────────┼──────────────────────────────────────────────────────────────────────────┼───────────────────────────────────────────────────┤
│ Read replicas            │ Route all SELECTs from orders-service and catalog-service to a replica   │ First step — zero schema changes                  │
├──────────────────────────┼──────────────────────────────────────────────────────────────────────────┼───────────────────────────────────────────────────┤
│ Connection pooling       │ HikariCP is already the default; tune maximumPoolSize in each service's  │ Before scaling instances                          │   
│                          │ config-repo YAML                                                         │                                                   │   
├──────────────────────────┼──────────────────────────────────────────────────────────────────────────┼───────────────────────────────────────────────────┤   
│ Separate schemas per     │ Move each service to its own DB                                          │ If one service's write load causes lock           │   
│ service                  │                                                                          │ contention for others                             │
└──────────────────────────┴──────────────────────────────────────────────────────────────────────────┴───────────────────────────────────────────────────┘

To add a read replica for orders-service, add a second datasource in config-repo/orders-service.yml:                                                          
spring:
datasource:                                                                                                                                                 
url: jdbc:mysql://mysql-primary:3307/cloudkitchens_db   # writes (not used by orders-service)
datasource-read:                                                                               
url: jdbc:mysql://mysql-replica:3307/cloudkitchens_db   # reads                                                                                           
Then annotate read-only service methods with @Transactional(readOnly = true) — Spring will route them to the replica.

MongoDB (used by procurement-service for plans + price snapshots):
- Add a replica set (1 primary + 2 secondaries) — MongoDB Atlas handles this automatically.
- PriceSnapshot already has a 6h TTL index; no change needed for sharding at this scale.
                                                                                                                                                          
Step 4 — Introduce a message queue for vendor fan-out

Currently VendorOrderFanOutService calls vendor APIs synchronously inside a request. At scale, one slow vendor blocks the thread and the user's HTTP
connection.

Replace with async fan-out:
1. Add RabbitMQ or Kafka to the stack.
2. submitOrder() writes the Order row and publishes one VendorOrderRequested event per line item, then returns immediately (HTTP 202).
3. A new VendorWorker service (or a thread pool consumer inside procurement-service) consumes the events and calls vendor APIs.
4. orders-service already polls OrderLineItem.status — no change needed on the read side.

This decouples your response latency from vendor API latency and lets you scale vendor workers independently.
                                                     
Step 5 — Caching

Add application-level caching for the two hot read paths:

┌─────────────────────────────────┬──────────────────────────┬───────┐                                                                                        
│              Path               │        Cache key         │  TTL  │
├─────────────────────────────────┼──────────────────────────┼───────┤                                                                                        
│ GET /api/v1/catalog/ingredients │ operatorId               │ 5 min │
├─────────────────────────────────┼──────────────────────────┼───────┤
│ GET /api/v1/orders (list)       │ operatorId + filter hash │ 30 s  │                                                                                        
└─────────────────────────────────┴──────────────────────────┴───────┘

Your config-repo/api-gateway.yml already has Redis wired in. Add Spring Cache (@Cacheable) backed by the same Redis instance in each service:
config-repo/catalog-service.yml (already has Redis; just add cache config)

Plans (MongoDB) already have a 6h TTL — that is already a cache.
                                                                                                                                                        
Step 6 — Observability before you scale further

Before adding more replicas, make sure you can see what breaks:

1. Add Zipkin to docker-compose.yml (currently commented out) — it is already instrumented in all services at 50% sampling.
2. Add Prometheus + Grafana — actuator/prometheus is already exposed. Scrape it.
3. Watch these metrics under load:                                                                                                                            
   - resilience4j_circuitbreaker_state — vendor circuit breakers opening
   - spring_cloud_gateway_requests_seconds — gateway latency per route                                                                                         
   - HikariCP hikaricp_connections_pending — DB pool saturation
                                                                                                                                                          
Step 7 — Move to Kubernetes for production

The services are already containerized. The migration path:

1. Write one Deployment + Service YAML per module (7 total).
2. Replace Eureka with Kubernetes Service DNS — or keep Eureka and add a ClusterIP Service pointing to the Eureka pod.
3. Add HorizontalPodAutoscaler for procurement-service and orders-service based on CPU or custom metrics (requests/sec from Prometheus).
4. Use Secrets for DB_PASSWORD and JWT_SECRET instead of environment variables.
5. Use a managed MySQL (RDS, Cloud SQL) and MongoDB Atlas instead of in-cluster databases.
 
Priority order

┌──────────┬─────────────────────────────────────────┬──────────────────────────────────────────────────────┐
│ Priority │                 Action                  │                        Impact                        │                                                 
├──────────┼─────────────────────────────────────────┼──────────────────────────────────────────────────────┤
│ 1        │ Tune HikariCP pool sizes                │ Prevents connection exhaustion immediately           │
├──────────┼─────────────────────────────────────────┼──────────────────────────────────────────────────────┤
│ 2        │ Scale procurement-service to 3 replicas │ Removes single point of failure on the critical path │                                                 
├──────────┼─────────────────────────────────────────┼──────────────────────────────────────────────────────┤                                                 
│ 3        │ Add Zipkin + Prometheus                 │ Visibility before you go further                     │                                                 
├──────────┼─────────────────────────────────────────┼──────────────────────────────────────────────────────┤                                                 
│ 4        │ MySQL read replica for orders-service   │ Isolates heavy analytics reads from writes           │
├──────────┼─────────────────────────────────────────┼──────────────────────────────────────────────────────┤                                                 
│ 5        │ Async vendor fan-out via queue          │ Decouples latency, unlocks independent scaling       │
├──────────┼─────────────────────────────────────────┼──────────────────────────────────────────────────────┤                                                 
│ 6        │ Kubernetes + HPA                        │ Elastic autoscaling in production                    │
└──────────┴─────────────────────────────────────────┴──────────────────────────────────────────────────────┘

---

# SECTION 14 — AGILE TEAM STRUCTURE & SDLC

## Team Structure

| Role | Count | Responsibility |
|---|---|---|
| Product Manager | 1 | Requirements, prioritization, stakeholder communication |
| Tech Lead / Architect | 1 | System design, code reviews, technical decisions |
| Full-Stack Engineers | 4 | Feature development (FE + BE), owns services end-to-end |
| Backend Engineers | 2 | Optimization engine, data pipeline, Feign integrations |
| QA Engineer | 1 | Test cases, API testing (Postman/Newman), regression |
| DevOps Engineer | 1 | Jenkins CI/CD, AWS infrastructure, Docker, monitoring |

## Sprint Structure (2-Week Sprints)

| Activity | Timing | Duration |
|---|---|---|
| Sprint Planning | Monday, Sprint Day 1 | 2 hours |
| Daily Standup | Every weekday, 9:30am | 15 minutes |
| Mid-Sprint Sync | Wednesday, Sprint Day 6 | 30 minutes |
| Sprint Review / Demo | Friday, Sprint Day 10 | 1 hour |
| Sprint Retrospective | Friday, Sprint Day 10 | 45 minutes |
| Backlog Grooming | Wednesday, Sprint Day 8 | 1 hour |

## Standup Format
- What I completed yesterday
- What I'm working on today
- Any blockers (dependency on another service, unclear requirement, vendor API access)

## Definition of Done
- Code reviewed and approved (1+ approval)
- Unit tests written and passing
- Integration tests passing in CI
- PM has verified acceptance criteria in staging
- No open Sev1/Sev2 bugs on the feature

---

# SECTION 15 — TRAFFIC (TRANSACTIONS PER SECOND)

## Observed Traffic at Launch (500 active operators)

| Endpoint Category | Estimated TPS | Notes |
|---|---|---|
| Auth (login/token refresh) | ~5 TPS | Morning peak as operators start their day |
| Plan generation | ~2 TPS | Each triggers multiple vendor API calls |
| Order submission | ~1 TPS | Typically done after plan review |
| Order history / analytics reads | ~10 TPS | Dashboard polling, report views |
| Catalog CRUD | ~3 TPS | Less frequent, mainly during onboarding |
| **Total peak** | **~20 TPS** | With 500 active operators |

## Scale Target
- Designed for **200 TPS** peak with current architecture (horizontal service scaling + read replicas).
- Beyond 200 TPS: introduce SQS-based async vendor fan-out and Redis caching to reach ~1,000 TPS without schema changes.
- The vendor API rate limits (Amazon: 1 req/sec per credential set) are the actual bottleneck at scale — solved by maintaining a pool of API credentials and distributing load across them.

---

*End of CloudKitchens Project Story — Project 1 of 3*
