# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

Multi-module Maven monorepo for the CloudKitchens **Smart Procurement Optimizer** — a microservices backend that aggregates grocery vendor prices (Amazon / Walmart / Kroger), produces a cost-optimized purchase plan, fans out orders, and exposes history + analytics. See `docs/CloudKitchens_Interview_Prep_Complete.md` for the full product/architecture narrative.

Stack: Java 17, Spring Boot 3.2.5, Spring Cloud 2023.0.1, MySQL, MongoDB, JWT (jjwt 0.12), MapStruct, Lombok, Resilience4j, springdoc-openapi, Testcontainers. Dependency versions are centralized in the root `pom.xml` `<dependencyManagement>` — do not redeclare versions in child `pom.xml` files.

## Modules and ports

| Module                | Port | Role                                                                 |
|-----------------------|------|----------------------------------------------------------------------|
| `config-server`       | 8888 | Spring Cloud Config (profile `native`, serves `classpath:/config-repo/`) |
| `eureka-server`       | 8761 | Service discovery (Netflix Eureka)                                   |
| `api-gateway`         | 8080 | Spring Cloud **Gateway (reactive / WebFlux)** — JWT validation + routing; do NOT add `spring-boot-starter-web` here |
| `auth-service`        | 8081 | Operator register/login/logout, BCrypt + JWT (HS256, 24h). MySQL.   |
| `catalog-service`     | 8082 | Ingredient CRUD + CSV bulk import. MySQL.                           |
| `procurement-service` | 8083 | Price aggregation, optimization, order write-side. MySQL + MongoDB.  |
| `orders-service`      | 8084 | Order history / analytics / retry (read-side). MySQL.               |

## Cross-cutting architecture (non-obvious pieces)

- **Config bootstrap**: every service has a minimal `src/main/resources/application.yml` that only declares its `spring.application.name` and imports `optional:configserver:http://localhost:8888`. The real per-service config (ports, datasources, routes, resilience4j, JWT secret) lives in `config-server/src/main/resources/config-repo/<service-name>.yml`. Edit *those* files — editing a service's own `application.yml` will not pick up any of the runtime config.
- **Startup order** at runtime: `config-server` → `eureka-server` → everything else. Other services will boot without config-server (`optional:` import) but with missing datasources/ports.
- **Gateway routing** (`config-repo/api-gateway.yml`): path-based, all prefixed `/api/v1/{auth|catalog|procurement|orders}/**`, resolved via Eureka `lb://SERVICE-NAME`. The JWT secret is shared between gateway and auth-service via config-server (`jwt.secret`).
- **Polyglot persistence**: `auth-service`, `catalog-service`, `orders-service`, and `procurement-service` all share one MySQL DB (`cloudkitchens_db` on `localhost:3307`). `procurement-service` additionally uses MongoDB (`localhost:27017`, DB `cloudkitchens_procurement`) for `price_snapshots` (TTL 6h).
- **Cross-service table sharing** — important: `procurement-service` *writes* the `orders` / `order_line_items` tables (entity `Order`, `OrderLineItem`). `orders-service` *reads the same rows* via separately defined `@Entity @Immutable` classes `OrderView` / `OrderLineItemView` mapped to the same tables. This is deliberate — they are kept as independent classes (not a shared lib) so the services can deploy independently. When modifying these entities: update the writer (`procurement-service`) first, then let `orders-service` pick up new columns on its next deploy. Do **not** enable DDL on these tables from `orders-service` (its Hibernate should be `ddl-auto=none`).
- **ddl-auto = update** is currently set on auth / catalog / procurement / orders via config-repo — whichever service starts first creates the shared `orders` tables. Treat `procurement-service` as the schema owner.
- **Tenant isolation**: every persisted row carries `operatorId`. Controllers must scope all queries by the operatorId pulled from the JWT, never from the request body.
- **Resilience4j** circuit breakers are pre-configured per external vendor client (`amazonClient`, `walmartClient`, `krogerClient`) in `config-repo/procurement-service.yml`. Wire new Feign clients to these names rather than inventing new instance configs.
- **Annotation processors**: Lombok + MapStruct are configured in the root `pom.xml` `maven-compiler-plugin` executions. New modules inherit this — no per-module setup needed. Note: the parent declares `${lombok.version}` without defining it (Spring Boot parent supplies it); don't override it.
- **Current state**: entities and DTOs exist for all services (see recent commits), but controllers / services / repositories / mapper beans are **not yet implemented** across the microservices. The three `Application.java` files for auth/catalog/procurement/orders are bare `@SpringBootApplication` scaffolds.

## Common commands

All commands run from the repository root unless noted. Use the Maven wrapper (`./mvnw`) — there is no system Maven requirement.

```bash
# Build everything (skip tests)
./mvnw -DskipTests clean package

# Build just one module (and its dependencies if any)
./mvnw -pl procurement-service -am clean package

# Run one service locally (hot reload not configured)
./mvnw -pl config-server   spring-boot:run     # start this FIRST
./mvnw -pl eureka-server   spring-boot:run     # then this
./mvnw -pl api-gateway     spring-boot:run
./mvnw -pl auth-service    spring-boot:run
# ...etc

# Run all tests
./mvnw test

# Run tests in a single module
./mvnw -pl auth-service test

# Run a single test class / method
./mvnw -pl auth-service test -Dtest=AuthServiceApplicationTests
./mvnw -pl auth-service test -Dtest=SomeClass#someMethod
```

Runtime prerequisites for a full local boot: MySQL reachable at `localhost:3307` with DB `cloudkitchens_db` (user `root` / password `yourpassword`) and MongoDB at `localhost:27017`. Credentials live in `config-server/src/main/resources/config-repo/*.yml`.

## Conventions

- Package root for every module: `org.example.<module-name-no-dash>` (e.g. `org.example.procurementservice`), split into `entity/`, `dto/`, and forthcoming `controller/`, `service/`, `repository/`, `mapper/`, `client/`.
- DTOs are Java `record` types with Jakarta validation annotations; entities are Lombok `@Getter @Setter @Builder` POJOs. Use MapStruct mappers (not hand-rolled) for entity ↔ DTO conversion.
- API paths are always `/api/v1/<domain>/...` — the gateway routes rely on this prefix.
- When adding a new external (Feign) client, register its circuit-breaker instance name in `config-repo/procurement-service.yml` under `resilience4j.circuitbreaker.instances`.
