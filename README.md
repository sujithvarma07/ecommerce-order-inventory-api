# Order & Inventory API

A REST API for managing e-commerce orders, inventory, and product catalog, built with Spring Boot.

## Status

Work in progress. This project is being built out incrementally — see `docs/progress.md` for what's done so far and what's next.

## Tech Stack

- Java 17
- Spring Boot 3.3
- Spring MVC (REST controllers)
- Spring Data JPA / Hibernate
- PostgreSQL (H2 for local dev)
- Maven
- Lombok

## Project Structure

```
src/main/java/com/ecommerce/orderinventory/
  config/       application-level configuration
  controller/   REST controllers
  service/      service interfaces
  service/impl/ service implementations
  repository/   Spring Data JPA repositories
  entity/       JPA entities
  dto/          request/response DTOs
  exception/    custom exceptions and error handling
```

## Getting Started

### Prerequisites

- JDK 17+
- Maven 3.9+
- PostgreSQL (optional for local dev — an in-memory H2 database is used by default)

### Run locally

The app requires a few environment variables at startup (see [Configuration](#configuration) below) — there are no committed default credentials, on purpose. `scripts/set-local-env.sh` is a gitignored (never committed) convenience script with placeholder values you can edit and source:

```bash
source scripts/set-local-env.sh
mvn spring-boot:run
```

Or export them directly:

```bash
export ADMIN_USERNAME=admin
export ADMIN_PASSWORD=<choose-your-own>
export CUSTOMER_USERNAME=customer
export CUSTOMER_PASSWORD=<choose-your-own>
mvn spring-boot:run
```

The app starts on `http://localhost:8080` with the `dev` profile active by default (H2 in-memory database, console at `/h2-console`).

To run against PostgreSQL, activate the `postgres` profile and set the relevant env vars:

```bash
export DB_HOST=localhost
export DB_PORT=5432
export DB_NAME=order_inventory_db
export DB_USERNAME=<your-db-username>
export DB_PASSWORD=<your-db-password>
mvn spring-boot:run -Dspring-boot.run.profiles=postgres
```

## Configuration

Every credential is supplied via environment variable — none are committed to this repository, in `application.yml` or anywhere else. The automated test suite is the one exception: it uses fixed, obviously-fake fixture credentials (`test-admin` / `test-customer`, activated only by the `test` Spring profile) against a throwaway in-memory H2 database that's torn down after each run, which is standard practice and not a real secret.

| Variable | Required in | Purpose |
|---|---|---|
| `ADMIN_USERNAME` / `ADMIN_PASSWORD` | always | Seeded admin account (catalog management, order fulfillment) |
| `CUSTOMER_USERNAME` / `CUSTOMER_PASSWORD` | always | Seeded demo customer account (placing orders) |
| `DB_HOST` / `DB_PORT` / `DB_NAME` | `postgres` profile | Database connection target |
| `DB_USERNAME` / `DB_PASSWORD` | `postgres` profile | Database credentials |
| `DB_POOL_MAX_SIZE` / `DB_POOL_MIN_IDLE` | optional, `postgres` profile | HikariCP pool sizing (defaults: 20 / 5) |
| `CORS_ALLOWED_ORIGINS` | optional | Comma-separated allowed origins for browser clients (default `http://localhost:3000`) |
| `RATE_LIMIT_PER_MINUTE` | optional | Requests/minute per client before `/api/v1/**` returns 429 (default `120`) |

For local dev, export these in your shell profile or edit and source `scripts/set-local-env.sh` (gitignored, never committed) — never commit real values. In a real deployment these belong in the platform's secret manager (e.g. environment configuration in your hosting provider, a Kubernetes Secret, AWS Secrets Manager) rather than in any file that goes into version control.

## API Endpoints

| Method | Endpoint                  | Description            |
|--------|----------------------------|-------------------------|
| POST   | `/api/v1/categories`       | Create a category       |
| GET    | `/api/v1/categories`       | List categories, paginated (`?page=&size=&sort=`) |
| GET    | `/api/v1/categories/{id}`  | Get a category by id    |
| PUT    | `/api/v1/categories/{id}`  | Update a category       |
| DELETE | `/api/v1/categories/{id}`  | Delete a category       |
| POST   | `/api/v1/products`         | Create a product         |
| GET    | `/api/v1/products`         | List products, paginated (`?page=&size=&sort=`), optionally filter with `?categoryId=` |
| GET    | `/api/v1/products/{id}`    | Get a product by id      |
| PUT    | `/api/v1/products/{id}`    | Update a product         |
| PATCH  | `/api/v1/products/{id}/stock` | Adjust stock by a signed delta (`{"delta": -10}`); rejected if it would take stock negative |
| GET    | `/api/v1/products/low-stock?threshold=10` | List products at or below a stock threshold (defaults to 10) |
| DELETE | `/api/v1/products/{id}`    | Delete a product         |
| POST   | `/api/v1/orders`           | Place an order (list of `{productId, quantity}`) — deducts stock from each product; requires being logged in (any role) |
| GET    | `/api/v1/orders`           | List orders, paginated (`?page=&size=&sort=`), newest first by default |
| GET    | `/api/v1/orders/{id}`      | Get an order by id       |
| PATCH  | `/api/v1/orders/{id}/status` | Update an order's status (`PENDING`/`CONFIRMED`/`SHIPPED`/`CANCELLED`), enforcing the allowed transitions below |
| DELETE | `/api/v1/orders/{id}`      | Delete an order — restores stock if the order was still `PENDING`/`CONFIRMED` |

### Order status transitions

```
PENDING   -> CONFIRMED, CANCELLED
CONFIRMED -> SHIPPED, CANCELLED
SHIPPED   -> (final state, no further transitions)
CANCELLED -> (final state, no further transitions)
```

Moving an order to `CANCELLED` from `PENDING` or `CONFIRMED` restores the stock that was deducted when the order was placed. An unlisted transition (e.g. `SHIPPED` -> `PENDING`) is rejected with a 409 and a message naming both states.

## Pagination & sorting

The three list endpoints (`GET /api/v1/categories`, `GET /api/v1/products`, `GET /api/v1/orders`) return a Spring Data `Page` rather than a bare array, using standard query parameters:

- `page` — zero-indexed page number (default `0`)
- `size` — page size (default `20`)
- `sort` — `property,direction`, repeatable for multi-field sort (default `id,asc` for categories/products, `createdAt,desc` for orders — most recent orders first)

Example: `GET /api/v1/products?page=1&size=10&sort=price,desc`

The response body includes `content` (the page of results) alongside `totalElements`, `totalPages`, `number` (current page), `size`, and `first`/`last` flags.

## Caching

Frequently-read, individually-keyed lookups are cached in-process (Spring Cache, `ConcurrentMapCacheManager`):

- `GET /api/v1/categories/{id}` and `GET /api/v1/products/{id}` are cached by id.
- The cache entry for a given id is evicted on update, delete, and (for products) stock adjustment, so a write is never followed by a stale read.
- List endpoints aren't cached — they're paginated/sorted in many different combinations, so caching by page/sort key wouldn't reliably pay off, and the underlying queries are already covered by the indexes added in Week 2.
- The in-memory cache is per-instance, which is fine for a single-instance deployment; a multi-instance deployment would swap in a shared cache (e.g. Redis) behind the same `CacheManager` bean without touching the service layer.

## Authentication & authorization

The API uses Spring Security with HTTP Basic auth and role-based access control, with two roles (`ADMIN`, `USER`):

- All `GET` endpoints under `/api/v1/**` are public (no auth required) so the catalog and order data can be browsed freely.
- Placing an order (`POST /api/v1/orders`) requires being logged in, but works for either role — a regular customer doesn't need admin rights to buy something.
- Catalog management (`POST`/`PUT`/`PATCH`/`DELETE` on categories and products) and order fulfillment (`PATCH`/`DELETE` on an existing order — status changes, deletion) require the `ADMIN` role.
- `/actuator/health` and `/actuator/info` are public; other actuator endpoints (e.g. `/actuator/metrics`) require authentication.
- Credentials live in the `app_users` table (`username`, BCrypt-hashed `password`, `role`), backed by a `UserDetailsService` that loads from the database rather than an in-memory list.
- Two demo accounts are seeded on startup if they don't already exist — one `ADMIN`, one `USER` — from the required environment variables described in [Configuration](#configuration). There are no committed default passwords; the app won't start without these set.
- Authentication and authorization failures return the same standardized JSON error shape as the rest of the API (401 / 403) instead of Spring Security's default HTML/plain-text responses.

## CORS

Cross-origin requests to `/api/v1/**` are allowed only from an explicit origin allowlist (`app.cors.allowed-origins`, env var `CORS_ALLOWED_ORIGINS`, comma-separated, defaulting to `http://localhost:3000` for local frontend development) rather than `*`, since the API accepts credentials (HTTP Basic).

## Rate limiting

A simple fixed-window rate limiter applies to every `/api/v1/**` request: each client IP gets up to `app.rate-limit.requests-per-minute` (env var `RATE_LIMIT_PER_MINUTE`, default `120`) requests per rolling 60-second window before getting a `429 Too Many Requests` back, in the same standardized JSON error shape as the rest of the API. The counters are in-process (per instance) — a multi-instance deployment would move them to a shared store (e.g. Redis) behind the same filter, without changing anything about how the limit is enforced per request.

## API documentation (Swagger / OpenAPI)

Interactive API docs are available once the app is running:

- Swagger UI: `http://localhost:8080/swagger-ui.html`
- Raw OpenAPI spec: `http://localhost:8080/v3/api-docs`

Both are public (no auth needed to view them); using "Try it out" against a write endpoint still needs valid credentials, same as calling the API directly.

## Error responses

All error responses (validation failures, not-found, conflicts, unexpected exceptions) share one JSON shape, produced by a single `@RestControllerAdvice`. See [`docs/api-error-format.md`](docs/api-error-format.md) for the exact structure and examples, including how field-level validation errors are reported.

## Monitoring

Spring Boot Actuator is enabled with the `health`, `info`, and `metrics` endpoints exposed:

| Endpoint | Description |
|----------|-------------|
| `GET /actuator/health` | Application health/status |
| `GET /actuator/info`   | Build/app info |
| `GET /actuator/metrics` | Available metrics (requires authentication) |

Every request is also logged (method, URI, response status, duration) via a servlet filter, excluding `/actuator/**` paths to keep health-check polling out of the logs.

## Connection pooling

HikariCP (Spring Boot's default) is explicitly tuned per profile rather than left on defaults: a small pool for the H2 dev profile, and a larger, env-overridable pool for the Postgres profile (`DB_POOL_MAX_SIZE` / `DB_POOL_MIN_IDLE`), plus connection-timeout, idle-timeout, max-lifetime, and a leak-detection threshold so a connection that's checked out and never returned shows up in the logs instead of silently starving the pool.

## Performance

See [`docs/performance-notes.md`](docs/performance-notes.md) for how to reproduce and capture measurable before/after results for the caching, pagination, and indexing work (a runnable script, `scripts/benchmark.sh`, plus what each number is meant to demonstrate). The results table in that doc is filled in after running the app locally.

## Testing

Two layers of automated tests:

- **Unit tests** (`src/test/java/.../service/`) — Mockito-based, no Spring context, covering service-layer business logic in isolation (validation, exceptions, business rules).
- **Integration tests** (`src/test/java/.../integration/`) — real Spring context against the H2 in-memory database (`dev` + `test` profiles):
  - `OrderInventoryFlowIntegrationTest` drives the full stack over real HTTP: public catalog browsing, admin-only catalog writes, authenticated-but-not-admin order placement, stock deduction on order creation, and stock restoration on cancellation — the actual flows called out for Week 3 integration testing.
  - `ProductOptimisticLockingIntegrationTest` runs two concurrent stock updates against the same product from separate threads/transactions and asserts that exactly one succeeds while the other fails with an optimistic locking exception, rather than either silently overwriting the other (a lost update) — verifying the `@Version` field added in Week 2 actually does its job under real concurrency, not just in a single-threaded unit test.

Run everything with `mvn test`.

## API testing

A Postman collection covering every endpoint above — plus a handful of deliberate error-case requests (duplicate SKU, validation failure, not-found, insufficient stock, invalid status transition, unauthorized write) — is at [`docs/postman/order-inventory-api.postman_collection.json`](docs/postman/order-inventory-api.postman_collection.json). Import it into Postman and set the `baseUrl`, `adminUsername`/`adminPassword`, and `customerUsername`/`customerPassword` collection variables to match whatever you exported for `ADMIN_USERNAME`/`ADMIN_PASSWORD`/`CUSTOMER_USERNAME`/`CUSTOMER_PASSWORD` when starting the app.

## Documentation

- [`docs/coding-standards.md`](docs/coding-standards.md) — coding conventions used in this project
- [`docs/api-error-format.md`](docs/api-error-format.md) — standardized error response shape
- [`docs/performance-notes.md`](docs/performance-notes.md) — how to reproduce and capture performance results
- [`docs/postman/order-inventory-api.postman_collection.json`](docs/postman/order-inventory-api.postman_collection.json) — Postman collection for manual API testing
- [`docs/progress.md`](docs/progress.md) — running log of what's been built
