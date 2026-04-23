Context

This is a research/documentation task, not a code implementation task. The user wants a structured list of future improvements they can make to the
CloudKitchens Smart Procurement Optimizer — a Spring Boot 3.2.5 microservices monorepo with 7 modules (config-server, eureka-server, api-gateway,
auth-service, catalog-service, procurement-service, orders-service).

The codebase is architecturally sound but pre-production: entities, DTOs, controllers, and services are implemented, but the project has zero test coverage
(except a trivial context-load test), no Docker/K8s/CI-CD, observability wired but unconfigured, and several feature gaps.

 ---
Future Improvements

🔴 Critical (production blockers)

1. Test Coverage — all modules

- Every module has an empty src/test/java/ directory; test dependencies (Testcontainers, WireMock, Mockito, spring-security-test) are already declared in
  pom.xml but unused.
- What to add:
    - @DataJpaTest + Testcontainers (MySQL) for all DAO classes
    - @WebMvcTest + @WithMockUser for every controller (validation, auth, error responses)
    - @SpringBootTest integration tests for key flows: register → login → logout, plan generation → override → submit, sub-order retry, analytics queries
    - WireMock stubs in procurement-service to simulate Amazon/Walmart/Kroger responses
    - Contract tests for Feign clients (CatalogClient, ProcurementRetryClient)
    - Target: 70 %+ line coverage per service

2. Database Migrations (Flyway)

- All services use ddl-auto: update, which is dangerous in production (can silently drop/alter columns).
- What to add:
    - Add flyway-core dependency to each service pom.xml
    - Create src/main/resources/db/migration/ with versioned SQL scripts:
        - V1__create_operators.sql, V2__create_catalog_items.sql, V3__create_orders.sql, V4__add_indexes.sql
    - Switch all services to ddl-auto: validate
    - Keep orders-service at ddl-auto: none (read-only)

3. Docker & Local Dev Environment

- No Dockerfile or docker-compose.yml exists anywhere.
- What to add:
    - Multi-stage Dockerfile for each Spring Boot service
    - Root docker-compose.yml that starts all 7 services + MySQL (port 3307) + MongoDB (27017) + Redis (6379) in correct startup order (config-server → eureka →
      rest)
    - .dockerignore

4. CI/CD Pipeline

- No .github/workflows/, Jenkinsfile, or any automation.
- What to add:
    - GitHub Actions workflow with stages: compile → test (with coverage gate) → Docker build & push → deploy to staging
    - Dependency vulnerability scan (OWASP Dependency-Check or Snyk)
    - Fail pipeline if coverage drops below threshold

 ---
🟠 High Priority (security & observability)

5. Secrets Management

- JWT secret (jwt.secret) and database passwords appear as plaintext in config-repo/*.yml.
- What to do:
    - Replace hardcoded values with ${ENV_VAR} references or Spring Cloud Config encryption ({cipher} prefix)
    - Integrate HashiCorp Vault or AWS Secrets Manager for production
    - Add environment-specific config profiles: application-dev.yml, application-prod.yml

6. API Gateway Rate Limiting

- api-gateway.yml routes exist but no RequestRateLimiter filters are configured despite Redis being available.
- What to add:
    - RequestRateLimiter filter per route, keyed by operatorId extracted from the JWT
    - Tighter limits on expensive endpoints (POST /procurement/plan, POST /catalog/items/bulk)
    - X-RateLimit-* response headers

7. Observability Configuration

- Micrometer, Zipkin, and CloudWatch dependencies are declared in pom.xml but all config is missing from every service YAML.
- What to add to each config-repo/<service>.yml:
    - management.endpoints.web.exposure.include: health,info,metrics,prometheus
    - management.tracing.sampling.probability: 0.5
    - management.zipkin.tracing.endpoint: http://zipkin:9411/api/v2/spans
    - logback-spring.xml with JSON structured output (correlation ID, service name in every line)
    - Custom Micrometer metrics: plan generation latency, vendor call success/failure counts, circuit breaker state

8. Account Security Gaps (auth-service)

- No brute-force protection, no refresh token, no password reset.
- What to add:
    - Account lockout after N failed login attempts (track in Redis with TTL)
    - Refresh token endpoint (POST /auth/refresh) with short-lived access tokens + longer-lived refresh tokens
    - Password reset flow: POST /auth/forgot-password → email link → POST /auth/reset-password (token in Redis, one-time use)
    - Invalidate all tokens on password change (write all active JTIs to blocklist or change signing secret per-user)

9. Missing GlobalExceptionHandler — orders-service

- orders-service has exception classes but no @ControllerAdvice handler; unhandled exceptions return Spring's default error body.
- What to add:
    - GlobalExceptionHandler mirroring the pattern already in auth, catalog, and procurement services
    - Standardize ErrorResponse shape across all services (add errorCode and requestId fields)

 ---
🟡 Medium Priority (feature completeness & performance)

10. Catalog Service: Pagination & Search

- GET /catalog/items returns all items in memory; no filter or search.
- What to add:
    - Pageable support (?page=0&size=20&sort=name)
    - Filter by category and name prefix (?category=Protein&search=chicken)
    - GET /catalog/categories — list all unique categories for the operator

11. Catalog Service: CSV Bulk Import Hardening

- No file-type check, no row count cap, no CSV injection sanitization, error list is unbounded.
- What to add:
    - MIME type validation (only text/csv or application/vnd.ms-excel)
    - Max file size and max row count enforced before parsing begins
    - Strip leading =, +, -, @ from field values to prevent formula injection
    - Cap rowErrors list at 100 entries with a "…and N more" summary

12. Procurement Service: Move Hardcoded Constants to Config

- PLAN_TTL_HOURS=6, CACHE_FRESHNESS_MINUTES=5, FANOUT_DEADLINE_SECONDS=10, OVERRIDE_STALENESS_MINUTES=5 are all hardcoded in Java.
- What to do:
    - Expose these as @ConfigurationProperties fields bound from config-repo/procurement-service.yml
    - Allows tuning without code change or redeploy

13. Procurement Service: Idempotency on Order Submission

- Network glitches during POST /plan/{planId}/submit can create duplicate orders.
- What to add:
    - Accept an Idempotency-Key request header
    - Cache the response in Redis for 24 h keyed by operatorId + idempotencyKey
    - Return the cached response on duplicate submission

14. Orders Service: Analytics Performance

- getSpendSummary() and getPriceTrends() aggregate directly against order_line_items at query time.
- What to add:
    - Materialized/pre-computed daily summary table (order_daily_summary) populated by a scheduled job
    - Index on order_line_items(catalog_item_id) and orders(submitted_at, operator_id)
    - @Transactional(readOnly = true) on all analytics queries

15. Orders Service: Order Export

- No way to download order history.
- What to add:
    - GET /orders/export?fromDate=&toDate= → streaming CSV response with all orders and line items
    - Leverage StreamingResponseBody to avoid buffering large exports in memory

16. Circuit Breakers for Inter-Service Feign Calls

- Resilience4j circuit breakers exist for vendor clients but not for CatalogClient or ProcurementRetryClient.
- What to add:
    - Register catalogClient and procurementRetryClient instances in resilience4j.circuitbreaker.instances in each service's config-repo YAML
    - Define fallback methods (e.g., return line items without hydrated catalog names rather than failing the entire response)

 ---
🟢 Lower Priority (nice-to-have)

17. Vendor Placeholder URLs

- config-repo/procurement-service.yml contains https://api.amazon.example.com etc. with a TODO comment.
- What to do: Replace with real vendor API endpoints (or WireMock stubs for dev) and add API key configuration.

18. Optimistic Locking on CatalogItem

- Concurrent PUT /catalog/items/{id} requests cause last-write-wins data loss.
- What to add: @Version Long version field on CatalogItem entity; return 409 Conflict on stale update.

19. Soft Delete for Catalog Items

- Deleted items are gone permanently; no audit trail.
- What to add: deleted_at timestamp column; filter WHERE deleted_at IS NULL in all queries; add restore endpoint.

20. Event-Driven Audit Trail (optional / future)

- No immutable event log for compliance or debugging.
- What to consider: Publish domain events (OrderSubmitted, PlanGenerated, VendorUnavailable) to Kafka or RabbitMQ via Spring Cloud Stream; consume in a new
  audit-service or logging pipeline.

21. Kubernetes Manifests

- No K8s deployment configuration.
- What to add:
    - Deployment + Service for each microservice
    - ConfigMap for non-secret config; Secret for credentials
    - Ingress for api-gateway
    - Readiness/liveness probes pointing to /actuator/health
    - HorizontalPodAutoscaler for procurement and orders services

22. API Docs (OpenAPI YAML files)

- Springdoc generates Swagger UI at runtime, but no static OpenAPI specs are committed.
- What to add: Export each service's OpenAPI spec as openapi.yaml and commit to docs/; enables client code generation and contract-first development.

 ---
Verification (for any implemented improvement)

- Build: ./mvnw -DskipTests clean package — must succeed
- Tests: ./mvnw test — all tests must pass, coverage report in target/site/jacoco/
- Integration: start all services in order (config → eureka → rest), run the curl end-to-end flow from README
- Migrations: verify Flyway applied all scripts (SHOW TABLES; in MySQL)
- Observability: hit /actuator/metrics and /actuator/health on each service