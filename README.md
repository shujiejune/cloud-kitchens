# CloudKitchens — Smart Procurement Optimizer

A microservices backend that aggregates grocery prices from multiple vendors, produces a cost-optimized purchase plan for ghost-kitchen operators, fans out orders, and exposes history and analytics.

---

## Table of Contents

- [Features](#features)
- [Architecture](#architecture)
- [Tech Stack](#tech-stack)
- [Modules and Ports](#modules-and-ports)
- [API Reference](#api-reference)
- [Data Flow](#data-flow)
- [Implementation Highlights](#implementation-highlights)
- [How to Run](#how-to-run)
- [How to Test](#how-to-test)

---

## Features

| Area | Capability |
|------|-----------|
| **Auth** | Operator register / login / logout with BCrypt passwords and HS256 JWT (24 h TTL); token revocation via Redis blocklist |
| **Catalog** | CRUD for ingredients; bulk CSV import with per-row error reporting |
| **Plan Generation** | Parallel price fetch from Amazon, Walmart, Kroger; greedy cheapest-per-item optimizer with vendor-consolidation pass; results cached as ephemeral MongoDB documents (6 h auto-TTL) |
| **Plan Override** | Reassign any line item to a different vendor and recompute totals before submission |
| **Order Submission** | Validates plan match, persists order + line items transactionally, fans out sub-orders to vendor APIs, returns 201 (all OK) or 207 Multi-Status (partial failure) |
| **Sub-Order Retry** | Re-send a single failed line item to its vendor without re-running the whole plan |
| **Order History** | Paginated list with date / vendor / item filters; full detail view with hydrated item names |
| **Analytics** | Spend summary, per-vendor spend breakdown, and price-trend series for any catalog item |
| **Security** | JWT validated at the gateway; `operatorId` forwarded via `X-Operator-Id` header; all DB queries tenant-scoped |
| **Resilience** | Resilience4j circuit breakers on every vendor Feign client; semaphore rate-limiter for parallel vendor calls; graceful plan generation despite vendor failures |

---

## Architecture

```
[ REACT SPA / CLIENT ]
                               |
                               | (HTTP Request + JWT)
                               v
======================================================================
[ DATA PLANE ] - Where User Traffic Flows

                      +-----------------+
                      |   API Gateway   |
                      |   (Port 8080)   |
                      +--------+--------+
                               | (Routes traffic based on Eureka lookup)
               +---------------+---------------+
               v               v               v
        +------------+  +------------+  +-------------+
        |Auth Service|  |Catalog Svc |  |Procurement  | ---> [ Amazon / Walmart APIs ]
        | (Port 8081)|  | (Port 8082)|  | (Port 8083) |
        +------------+  +------------+  +------+------+
               |               |               |
               |               |               v (Internal Feign Call)
               |               |        +-------------+
               |               |        |Orders Svc   |
               |               |        | (Port 8084) |
               |               |        +------+------+
======================================================================
               |               |               |
               v               v               v
          [ MySQL ]       [ MySQL ]   [ MongoDB & MySQL ]

======================================================================
[ CONTROL PLANE ] - Where Microservices Get Their Instructions

  +-----------------------+              +-----------------------+
  |     Config Server     |              |     Eureka Server     |
  |     (Port 8888)       |              |     (Port 8761)       |
  +-----------------------+              +-----------------------+
      ^                                      ^
      | Phase 1: Bootstrap                   | Phase 2: Registration & Discovery
      |                                      |
      | Every service connects here          | Every service registers its IP here 
      | FIRST to download its database       | AFTER it boots up. The Gateway 
      | passwords, port numbers, and         | checks Eureka constantly to know 
      | Eureka URL.                          | where to route user traffic.
======================================================================
```

**Key architectural decisions:**

- **Externalized config** — per-service YAML files live in `config-server/src/main/resources/config-repo/`; service `application.yml` files only declare the app name and the config-server import.
- **Polyglot persistence** — MySQL for all relational data (operators, catalog, orders); MongoDB for ephemeral plan documents with a native TTL index.
- **CQRS-lite** — `procurement-service` owns and writes the `orders` / `order_line_items` tables; `orders-service` reads the same tables via separate `@Immutable` JPA entity classes to allow independent deployment.
- **Tenant isolation** — every persisted row carries `operatorId`; `operatorId` is always extracted from the JWT, never from the request body.

---

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Language | Java 17 |
| Framework | Spring Boot 3.2.5, Spring Cloud 2023.0.1 |
| Gateway | Spring Cloud Gateway (reactive / WebFlux) |
| Service discovery | Netflix Eureka |
| Config | Spring Cloud Config Server (native profile) |
| Auth | JJWT 0.12.5 (HS256), BCrypt, Redis blocklist |
| HTTP clients | OpenFeign (service-to-service), `WebClient` (vendor APIs) |
| Relational DB | MySQL (shared `cloudkitchens_db`) |
| Document DB | MongoDB (`cloudkitchens_procurement`, TTL index) |
| Cache / blocklist | Redis |
| Resilience | Resilience4j circuit breakers + semaphore rate-limiter |
| Mapping | MapStruct 1.5.5 |
| Boilerplate reduction | Lombok |
| Validation | Jakarta Bean Validation |
| API docs | springdoc-openapi 2.5.0 (Swagger UI) |
| Testing | JUnit 5, Testcontainers 1.19.8, Mockito, MockMvc |
| Build | Maven (wrapper `./mvnw`), multi-module POM |

---

## Modules and Ports

| Module | Port | Role |
|--------|------|------|
| `config-server` | 8888 | Centralized configuration |
| `eureka-server` | 8761 | Service registry and discovery |
| `api-gateway` | 8080 | JWT validation, routing, CORS |
| `auth-service` | 8081 | Operator identity, BCrypt + JWT |
| `catalog-service` | 8082 | Ingredient catalog, CSV bulk import |
| `procurement-service` | 8083 | Price aggregation, optimization, order fan-out |
| `orders-service` | 8084 | Order history, analytics, retry |

---

## API Reference

All routes are served through the gateway at `http://localhost:8080`.

### Auth — `/api/v1/auth`

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `POST` | `/register` | — | Register a new operator |
| `POST` | `/login` | — | Login; returns JWT |
| `POST` | `/logout` | Bearer | Revoke current JWT |
| `GET` | `/me` | Bearer | Get operator profile |

### Catalog — `/api/v1/catalog`

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `GET` | `/items` | Bearer | List all items (tenant-scoped) |
| `POST` | `/items` | Bearer | Create a catalog item |
| `GET` | `/items/{id}` | Bearer | Get single item |
| `PUT` | `/items/{id}` | Bearer | Update item |
| `DELETE` | `/items/{id}` | Bearer | Delete item |
| `POST` | `/items/bulk` | Bearer | Bulk import via CSV (`multipart/form-data`) |
| `GET` | `/items/batch` | Internal | Batch fetch by IDs (Feign, not public) |

### Procurement — `/api/v1/procurement`

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `POST` | `/plan` | Bearer | Generate a purchase plan |
| `GET` | `/plan/{planId}` | Bearer | Fetch a saved plan |
| `PUT` | `/plan/{planId}/override` | Bearer | Override line item vendor |
| `POST` | `/plan/{planId}/submit` | Bearer | Submit plan → fan-out orders |
| `POST` | `/orders/{orderId}/retry/{lineItemId}` | Internal | Retry a failed sub-order |
| `GET` | `/vendors` | Bearer | List registered vendors |

### Orders — `/api/v1/orders`

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `GET` | `/` | Bearer | List orders (paginated; filters: `fromDate`, `toDate`, `vendorId`, `catalogItemId`) |
| `GET` | `/{id}` | Bearer | Order detail with line items |
| `GET` | `/{id}/status` | Bearer | Lightweight status poll |
| `POST` | `/{id}/retry/{lineItemId}` | Bearer | Retry a failed sub-order |

### Analytics — `/api/v1/analytics`

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `GET` | `/spend-summary?days=30` | Bearer | Total spend, savings, per-vendor breakdown |
| `GET` | `/spend-by-vendor?days=30` | Bearer | Vendor-level spend roll-up |
| `GET` | `/price-trends/{catalogItemId}?days=90` | Bearer | Historical unit-price series |

---

## Data Flow

### Purchase Plan Generation

```
POST /api/v1/procurement/plan { catalogItemIds, refreshPrices }
  → Gateway: validate JWT, inject X-Operator-Id
  → ProcurementService.generatePlan():
      1. Feign → catalog-service: batch-fetch item names / units / preferred quantities
      2. Parallel vendor calls (semaphore-gated, circuit-breaker-protected):
           Amazon, Walmart, Kroger → price snapshots (cached in MongoDB 6 h)
      3. Optimizer:
           a. For each item: pick cheapest vendor (greedy)
           b. Consolidation pass: merge vendors if delta < configured threshold
      4. Persist Plan document to MongoDB (auto-expires in 6 h via TTL index)
  ← PurchasePlanResponse { planId, items, totalCost, estimatedSavings, vendorWarnings }
```

### Order Submission

```
POST /api/v1/procurement/plan/{planId}/submit { lineItems }
  → ProcurementService.submitOrder():
      1. Load & validate Plan from MongoDB (ownership + item/price match)
      2. BEGIN TRANSACTION
           Create Order (status=SUBMITTED)
           Create OrderLineItems (subOrderStatus=PENDING for each line)
         COMMIT
      3. Fan-out: one HTTP call per distinct vendor
           On 2xx → subOrderStatus=CONFIRMED, store vendorOrderRef
           On error → subOrderStatus=FAILED
      4. Delete Plan from MongoDB
  ← 201 (all confirmed) or 207 Multi-Status (partial failure)
```

### Sub-Order Retry

```
POST /api/v1/orders/{orderId}/retry/{lineItemId}
  → orders-service (validates ownership)
  → Feign → procurement-service /retry endpoint
      1. Load order + line item, verify subOrderStatus == FAILED
      2. Re-call vendor API
      3. Update subOrderStatus → CONFIRMED or FAILED
  ← Updated LineItemResponse
```

---

## Implementation Highlights

### Vendor Consolidation Optimizer

The optimizer first assigns each catalog item to its cheapest vendor. A second pass consolidates: if moving a group of items to another vendor increases cost by less than a configurable threshold (default 5 %), the lines are merged to reduce the number of purchase orders. This minimises shipping overhead while preserving most of the price savings.

### Circuit Breakers with Graceful Plan Degradation

Each vendor Feign client is wrapped by a Resilience4j circuit breaker (`slidingWindowSize=5`, `failureRateThreshold=50 %`, `waitDurationInOpenState=30 s`). If a vendor's circuit opens during plan generation, that vendor is excluded and its name is added to `vendorWarnings[]` in the response — the plan still generates with the remaining vendors rather than failing entirely.

### JWT Blocklist via Redis TTL

On logout, the token's `jti` claim is written to Redis as `jwt:blocklist:{jti}` with a TTL equal to the token's remaining lifetime. The gateway checks this key before forwarding requests. When the JWT naturally expires, Redis automatically evicts the key — no scheduled cleanup job is needed.

### MongoDB TTL Index for Ephemeral Plans

`Plan` documents carry a `ttlExpiry` field annotated with `@Indexed(expireAfterSeconds = 0)`. MongoDB's background TTL monitor deletes documents automatically when `ttlExpiry` passes, keeping the plans collection from growing unbounded without any application-level cron.

### CQRS-Lite: Same Tables, Independent Services

`procurement-service` writes the `orders` and `order_line_items` tables. `orders-service` reads those same tables through separately defined `@Entity @Immutable` classes (`OrderView`, `OrderLineItemView`) with `ddl-auto: none`. No shared library is required — the services can be deployed and evolved independently as long as the column contract is maintained.

### MapStruct Compile-Time Mapping

Entity ↔ DTO conversions are handled by MapStruct `@Mapper` interfaces compiled by the annotation processor configured in the root POM. This eliminates hand-rolled copy constructors while keeping the mapping logic inspectable (generated source in `target/generated-sources`).

### Bulk CSV Import with Partial Success

The catalog bulk-import endpoint parses each CSV row independently. Rows that fail validation are collected in a `rowErrors[]` list, while valid rows are batch-inserted. The response includes `totalRows`, `successCount`, `failureCount`, and per-row error details, so callers can fix only the failed rows on resubmit.

---

## How to Run

### Prerequisites

| Dependency | Version | Default address |
|------------|---------|-----------------|
| Java | 17+ | — |
| MySQL | 8+ | `localhost:3307` |
| MongoDB | 6+ | `localhost:27017` |
| Redis | 7+ | `localhost:6379` |

Create the MySQL database:

```sql
CREATE DATABASE cloudkitchens_db;
```

Credentials are in `config-server/src/main/resources/config-repo/*.yml`. Edit those files if your local credentials differ — do **not** edit service-level `application.yml` files.

### Start order (each in a separate terminal)

```bash
# 1. Config server — must start first
./mvnw -pl config-server spring-boot:run

# 2. Service registry
./mvnw -pl eureka-server spring-boot:run

# 3. Remaining services (any order)
./mvnw -pl api-gateway         spring-boot:run
./mvnw -pl auth-service        spring-boot:run
./mvnw -pl catalog-service     spring-boot:run
./mvnw -pl procurement-service spring-boot:run
./mvnw -pl orders-service      spring-boot:run
```

### Build without running

```bash
# Full build (skip tests)
./mvnw -DskipTests clean package

# Single module (and its dependencies)
./mvnw -pl procurement-service -am clean package
```

### Swagger UI

Each service exposes Swagger UI at `http://localhost:<port>/swagger-ui.html` after startup.

---

## How to Test

### Run all tests

```bash
./mvnw test
```

### Run tests for a single module

```bash
./mvnw -pl auth-service test
```

### Run a specific test class or method

```bash
./mvnw -pl auth-service test -Dtest=AuthServiceApplicationTests
./mvnw -pl auth-service test -Dtest=SomeClass#someMethod
```

### Manual end-to-end flow (curl)

```bash
BASE=http://localhost:8080/api/v1

# 1. Register
curl -s -X POST $BASE/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"email":"chef@example.com","password":"secret123","companyName":"Ghost Kitchen A"}' | jq .

# 2. Login — copy the token
TOKEN=$(curl -s -X POST $BASE/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"chef@example.com","password":"secret123"}' | jq -r '.token')

AUTH="Authorization: Bearer $TOKEN"

# 3. Create a catalog item
curl -s -X POST $BASE/catalog/items \
  -H "$AUTH" -H 'Content-Type: application/json' \
  -d '{"name":"Chicken Breast","category":"Protein","unit":"lb","preferredQty":50}' | jq .

# 4. Generate a purchase plan (use the item id from step 3)
curl -s -X POST $BASE/procurement/plan \
  -H "$AUTH" -H 'Content-Type: application/json' \
  -d '{"catalogItemIds":[1],"refreshPrices":true}' | jq .

# 5. Submit the plan (use planId from step 4)
curl -s -X POST $BASE/procurement/plan/<planId>/submit \
  -H "$AUTH" -H 'Content-Type: application/json' \
  -d '{"lineItems":[{"catalogItemId":1,"vendorId":1,"quantity":50,"unitPrice":3.49}]}' | jq .

# 6. View order history
curl -s "$BASE/orders" -H "$AUTH" | jq .

# 7. Spend analytics
curl -s "$BASE/analytics/spend-summary?days=30" -H "$AUTH" | jq .

# 8. Logout
curl -s -X POST $BASE/auth/logout -H "$AUTH" | jq .
```

### Bulk CSV import example

```csv
name,category,unit,preferredQty
Tomatoes,Produce,lb,20
Olive Oil,Pantry,fl oz,64
Invalid Row,,, 
```

```bash
curl -s -X POST $BASE/catalog/items/bulk \
  -H "$AUTH" \
  -F "file=@ingredients.csv" | jq .
# Response includes successCount, failureCount, and per-row errors
```

### Eureka dashboard

Visit `http://localhost:8761` to verify all services are registered and healthy.
