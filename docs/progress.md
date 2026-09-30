# Progress Log

Running log of work on the Order & Inventory API, tracked against the 4-week plan.

## Week 1 — Backend Setup & API Development ✅ complete

- [x] Set up and configured the Spring Boot dev environment (Maven, Java 17, Spring Boot 3.3)
- [x] Reviewed project structure and settled on coding standards up front, since this is a new project rather than an existing one — see `docs/coding-standards.md`
- [x] Developed RESTful APIs (Spring MVC) for Category, Product, and Order
- [x] Request validation, DTOs, controllers, and service-layer components across all three resources
- [x] Connected the application to a relational database via Spring Data JPA/Hibernate (H2 for dev, Postgres profile for staging/prod)
- [x] Unit tests for the newly developed service-layer components (`CategoryServiceImpl`, `ProductServiceImpl`, `OrderServiceImpl` — 16 tests total, Mockito + AssertJ)

### What's in the API right now

- **Category** — full CRUD
- **Product** — full CRUD, linked to Category, SKU uniqueness enforced, tracks `stockQuantity`
- **Order** — create (multi-item, snapshots product price at order time, validates stock availability, computes total), get by id, list all, update status, delete

### Deliberately out of scope for week 1 (belongs to later weeks per the plan)

- Stock is *checked* on order creation but not yet *decremented* — reserving/deducting inventory is part of the Week 2 business-logic work.
- Global exception handling (`@ControllerAdvice`, standardized error response shape) is a named Week 2 item; for now, service-layer errors use `ResponseStatusException` directly, which already gives correct HTTP status codes.
- No auth yet — that's Week 3.

## Week 2 — Business Logic & Database Integration ✅ complete

- [x] Standardized exception handling and API error responses — replaced the Week 1 `ResponseStatusException` calls with a small domain exception hierarchy (`ResourceNotFoundException`, `DuplicateResourceException`, `InsufficientStockException`, `InvalidOrderStateException`) and one `GlobalExceptionHandler` (`@RestControllerAdvice`) that returns a consistent JSON error shape everywhere, including field-level detail on validation failures — see `docs/api-error-format.md`
- [x] Optimized entities/relationships — added indexes on the columns actually being queried (`products.category_id`, `products.sku`, `orders.customer_email`, `orders.status`) and optimistic locking (`@Version` on `Product`) so concurrent stock updates fail safely instead of silently overwriting each other
- [x] Business logic: order placement now actually deducts stock per line item (not just validates it), and cancelling a `PENDING`/`CONFIRMED` order — or deleting one in those states — restores the stock that was deducted
- [x] Order status workflow rules: an explicit allow-list of transitions (`PENDING -> CONFIRMED/CANCELLED`, `CONFIRMED -> SHIPPED/CANCELLED`, `SHIPPED`/`CANCELLED` are final) enforced in `updateStatus`, throwing `InvalidOrderStateException` on anything else
- [x] Expanded CRUD: `PATCH /api/v1/products/{id}/stock` for signed stock adjustments (rejects a delta that would take stock negative) and `GET /api/v1/products/low-stock` for a low-stock report
- [x] Logging and application-level monitoring: SLF4J logging across the service layer (create/update/delete/status-change events), a request-logging servlet filter (method, URI, status, duration), and Spring Boot Actuator (`health`, `info`, `metrics`)
- [x] Postman collection covering every endpoint plus deliberate error-case requests — see `docs/postman/order-inventory-api.postman_collection.json`
- [x] Test coverage extended to the new business logic: stock deduction on order creation, invalid status transitions, stock restoration on cancellation, stock-adjustment success/failure, low-stock filtering

### Notes

- Tests were updated to assert against the new exception types rather than `ResponseStatusException`.
- Stock deduction and restoration both go through `ProductRepository.save`, so the same optimistic-locking (`@Version`) protection from the entity-optimization work above also covers these paths under concurrent updates.

## Week 3 — Security, Performance & Integration ✅ complete

- [x] Authentication & authorization (part 1/3) — added Spring Security with HTTP Basic auth and role-based access control: `GET` endpoints stay public, write operations (`POST`/`PUT`/`PATCH`/`DELETE`) require the `ADMIN` role. Credentials are stored in a new `app_users` table (BCrypt-hashed passwords) via a database-backed `UserDetailsService`, with a default admin user seeded on startup for local dev. Authentication/authorization failures (401/403) go through the same standardized JSON error shape as the rest of the API rather than Spring Security's defaults.
- [x] Performance (part 2/3) — the three list endpoints (`GET /categories`, `GET /products`, `GET /orders`) now return a paginated, sortable `Page` instead of a bare array; added `@Cacheable`/`@CacheEvict` around the by-id lookups for categories and products (evicted on every write, including stock adjustment, so nothing stale is ever served); tuned HikariCP explicitly per profile instead of leaving it on defaults, with the Postgres pool size overridable via env vars; marked all read-only service methods `@Transactional(readOnly = true)`
- [x] Integration & polish (part 3/3) — CORS restricted to an explicit origin allowlist (`CORS_ALLOWED_ORIGINS`), interactive API docs via springdoc/Swagger UI at `/swagger-ui.html`, and a per-IP fixed-window rate limiter (`RATE_LIMIT_PER_MINUTE`, default 120/min) on `/api/v1/**` returning 429 in the standard error shape

### Response to review feedback (after Week 1/2 review)

- **Security — no committed credentials**: `application.yml` no longer has *any* default admin/DB password. `ADMIN_USERNAME`/`ADMIN_PASSWORD`/`CUSTOMER_USERNAME`/`CUSTOMER_PASSWORD` and the Postgres `DB_USERNAME`/`DB_PASSWORD` are now required environment variables with no fallback — the app fails to start rather than silently running with a weak default. The only credentials still in the repo are obviously-fake fixture values (`test-admin`/`test-customer`) gated behind a `test` Spring profile used solely by the automated test suite against a throwaway in-memory database. See README > Configuration for the full list and how to set them.
- **Performance — measurable results**: added `scripts/benchmark.sh` and `docs/performance-notes.md`, which document exactly how to reproduce and capture before/after numbers for the caching and indexing work (cache hit vs. miss timing, paginated list timing, `EXPLAIN ANALYZE` for confirming index usage on Postgres). The results table in that doc is intentionally left for whoever runs it locally to fill in — this environment doesn't have a way to run the live app against real data volume, so the honest thing was to build the measurement tooling and be explicit about that rather than typing in numbers that weren't actually measured.
- **Integration — auth/authorization/orders/inventory working together, plus concurrency**: this surfaced a real gap in the part-1 authorization rules (order placement had been lumped in with admin-only catalog writes, which would have blocked ordinary customers from ordering anything). Fixed by splitting the rule: placing an order now only requires being logged in as *any* role, while changing an order's status or deleting it stays admin-only. A new `USER`-role demo account (`CUSTOMER_USERNAME`/`CUSTOMER_PASSWORD`) was added specifically to exercise this. Two new integration tests back this:
  - `OrderInventoryFlowIntegrationTest` — drives the real HTTP stack through the full flow: anonymous/customer/admin requests against categories, products, and orders, confirming public reads, admin-only catalog writes, authenticated-but-not-admin order placement, stock deduction on order creation, and stock restoration on cancellation all work together correctly.
  - `ProductOptimisticLockingIntegrationTest` — runs two concurrent stock updates against the same product row from separate threads/transactions (synchronized with latches so both read the same starting version before either writes) and asserts exactly one succeeds while the other fails with an optimistic locking exception, confirming the `@Version` field from Week 2 actually prevents a lost update under real concurrency rather than only passing in single-threaded unit tests.

### Notes

- `/actuator/metrics` now requires authentication (previously public in Week 2) since it can leak operational detail; `/actuator/health` and `/actuator/info` stay public for uptime checks.
- CSRF is disabled and sessions are stateless, appropriate for a token/credential-per-request REST API rather than a browser session-based app.
- `ProductRepository.findByCategoryId` and the plain `getAll()` service methods became paginated overloads (`Pageable` in, `Page<...>` out) rather than staying as separate unpaginated methods, to avoid maintaining two versions of the same query.
- Caching is in-process (`ConcurrentMapCacheManager`) for now, which is correct for a single instance; swapping in a shared cache for a multi-instance deployment is a `CacheManager` bean change, not a service-layer change.
- Tests were extended to cover the new paginated `getAll`/`getByCategory` methods using `PageImpl`/`PageRequest`.
- Rate limiting and the request-logging filter are both plain `OncePerRequestFilter` `@Component`s (consistent with the existing `RequestLoggingFilter` pattern) rather than being wired into the Spring Security filter chain explicitly — simplest option that still applies to every request.

## Week 4 — Testing, Deployment & Documentation (in progress)

- [x] Expanded testing (part 1/3) — closed real gaps rather than padding numbers:
  - Added the missing not-found/conflict edge-case unit tests on every service write method that didn't already have one (`update`/`delete` on Category and Product, `getByCategory` on Product, `delete` on Order including the stock-restoration and already-shipped paths).
  - Added a new web-layer test suite (`src/test/java/.../controller/`, `@WebMvcTest`) for all three controllers — request validation (400 + field errors), not-found/conflict responses (404/409 in the standard error shape), and success responses (201/204) — a layer that had no direct test coverage before (it was only exercised indirectly through the two integration tests).
  - Added the JaCoCo Maven plugin so `mvn test` produces a line/branch coverage report at `target/site/jacoco/index.html` — coverage is now something you can actually look at, not just estimate.
- [x] Deployment (part 2/3) — added a multi-stage `Dockerfile` (Maven build stage, then a slim JRE runtime stage with no JDK/Maven in the final image, running as a non-root user) and a `docker-compose.yml` that runs the app alongside a real Postgres container. `.env.example` documents every variable Compose needs, with no real values committed (same rule as the rest of the app's configuration); `.env` itself is gitignored. `docs/deployment.md` covers running it locally via Compose, building/running the image directly, and what stays the same versus what changes when this eventually moves to a real cloud target (the image doesn't change, only where the environment variables point).
- [x] Documentation polish (part 3/3) — final README/architecture pass

### Notes

- `@WebMvcTest` slices disable the full Spring Security filter chain (`@AutoConfigureMockMvc(addFilters = false)`) since these tests are about the controller/validation/error-handling layer, not authorization — authorization is already covered end-to-end by `OrderInventoryFlowIntegrationTest` from Week 3.
- No change to production code was needed for the testing batch — purely additive test coverage plus the JaCoCo build step.
- The container image defaults to `SPRING_PROFILES_ACTIVE=postgres` rather than the H2 `dev` profile, since a containerized deployment is assumed to run against a real database; the healthcheck in both the `Dockerfile`'s expectations and `docker-compose.yml` hits the same `/actuator/health` endpoint most hosting platforms would use to decide an instance is ready for traffic.
- A specific cloud hosting target (which provider, managed Postgres vs. self-hosted, etc.) is intentionally left open in `docs/deployment.md` rather than guessed at — the two things that don't change regardless of target (real env-var-supplied credentials, a reachable Postgres instance) are documented; the platform-specific setup is a decision for whoever owns that call.

- **Documentation polish (part 3/3)**: added `docs/architecture.md` — layered structure, a full request-flow trace through placing an order, the domain model (Category/Product/Order/OrderItem/AppUser relationships), the cross-cutting concerns (error shape, rate limiting, caching, connection pooling) and the reasoning behind a handful of decisions that aren't obvious from the code alone (layered vs. hexagonal, DTOs everywhere, optimistic locking over pessimistic, in-process cache before Redis). Reworked the README: `## Status` now reflects that all four planned weeks are complete rather than "work in progress", added a table of contents (the README is long enough now that one earns its place), and linked the new architecture doc from both the new `## Architecture` section and the `## Documentation` list.

## Project status: complete

All four planned weeks are done:

1. **Week 1** — project setup, entities, repositories, initial CRUD REST APIs for categories and products.
2. **Week 2** — orders and inventory business logic, DB relationships, validation, global error handling, pagination.
3. **Week 3** — security (Spring Security, role-based access control, seeded accounts), performance (caching, connection pooling, rate limiting), and the review-feedback pass (no committed secrets, benchmarking tooling instead of invented numbers, integration + concurrency tests, an authorization gap fix).
4. **Week 4** — expanded test coverage (controller-layer tests, edge cases, JaCoCo), containerized deployment (Dockerfile, Docker Compose with Postgres, deployment docs), and this final documentation pass.

Nothing here is a hard stop — the codebase is in a state where the natural next steps (a CI pipeline, a shared cache for multi-instance deployments, moving the container to an actual hosting target) are called out in the docs above as explicit, deliberate "not done yet, and here's what it'd take" rather than gaps that were missed.

---

### Notes

- Chose Category as the first slice because it's the simplest independent entity — good for shaking out project conventions (DTO shape, service/controller split, error responses) before building out Product, Order, and Inventory on top of the same pattern.
- Global exception handling (`@ControllerAdvice`) is deliberately deferred to the Week 2 scope item that calls it out explicitly; for now, service-layer errors use `ResponseStatusException` directly.
