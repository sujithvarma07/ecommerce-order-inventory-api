# Architecture

This document describes how the pieces of the Order & Inventory API fit together — the request flow through the layers, the domain model, and the reasoning behind a few decisions that aren't obvious just from reading the code. It's meant to be read once, after the README, by someone who wants the "why" rather than the "how to run it."

## Layers

The codebase follows a standard layered structure, with each layer depending only on the one below it:

```
HTTP request
     |
     v
[ Filters ]            RateLimitFilter, RequestLoggingFilter, Spring Security's chain
     |
     v
[ Controller ]         request validation, HTTP status codes, DTO <-> domain translation
     |
     v
[ Service ]            business rules, transactions, cache eviction
     |
     v
[ Repository ]         Spring Data JPA, no business logic
     |
     v
[ Database ]           H2 (dev/test) or PostgreSQL (postgres profile)
```

Controllers never talk to repositories directly, and services never touch `HttpServletRequest`/`HttpServletResponse` — that split is what lets the controller tests (`@WebMvcTest`) and service tests (plain Mockito) each cover one layer without dragging the other one in.

DTOs exist specifically to keep the entities from leaking into the API surface: a `ProductResponse` is shaped for what a client needs to see, not for what the `Product` entity happens to store, and a request DTO can carry its own `@Valid` annotations without touching the entity at all.

## Request flow example: placing an order

`POST /api/v1/orders` is the most involved endpoint, so it's a useful one to trace end to end:

1. `RateLimitFilter` checks the calling IP's request count for the current 60-second window; over the limit, the request is rejected with 429 before it reaches Spring MVC at all.
2. Spring Security's filter chain authenticates the request (HTTP Basic) and confirms the caller is logged in — any role is enough for this endpoint, since placing an order doesn't require being an admin.
3. `OrderController` validates the request body (`@Valid` on `OrderRequest`, a list of `{productId, quantity}`), returning 400 with field-level errors if it's malformed.
4. `OrderServiceImpl.createOrder(...)`, wrapped in a single `@Transactional` method:
   - loads each `Product` referenced in the request,
   - checks each has enough stock, throwing `InsufficientStockException` (409) if not,
   - deducts the ordered quantity from each product's stock and saves it — this is the write that `@Version` optimistic locking protects against a lost update if two orders for the same product are placed at nearly the same instant,
   - builds the `Order` and its `OrderItem`s and saves them,
   - evicts the relevant product cache entries, since their stock just changed.
5. If any step throws, the `@Transactional` boundary rolls back the whole thing — an order is never left half-created with some stock deducted and some not.
6. `GlobalExceptionHandler` converts any exception into the standard `ApiErrorResponse` JSON shape; on success, the controller returns 201 with the created order.

Cancelling or deleting a still-open order runs the same stock logic in reverse (`OrderServiceImpl` restores the quantities to each product), which is what `OrderInventoryFlowIntegrationTest` and `ProductOptimisticLockingIntegrationTest` exercise directly against a real Spring context and real concurrent threads rather than mocks.

## Domain model

```
Category  1 ---- * Product  1 ---- * OrderItem * ---- 1  Order
                                                          |
                                                        status: OrderStatus

AppUser   (username, password hash, role: ADMIN | USER)
```

- A `Product` belongs to exactly one `Category`; a `Category` can have many products.
- An `Order` is made up of one or more `OrderItem`s, each pointing at the `Product` it was for and the quantity ordered at that time (kept on the `OrderItem` itself, not recomputed later, so a past order's record doesn't change if the product's current stock does).
- `Product` carries a `@Version` column used for optimistic locking on stock updates.
- `AppUser` is independent of the catalog/order graph — it exists purely for authentication, with `role` driving the authorization rules in `SecurityConfig`.

## Cross-cutting concerns

A few things apply across every endpoint rather than belonging to one layer, and are implemented as filters or shared beans rather than duplicated per controller:

- **Error shape** — `GlobalExceptionHandler` (`@RestControllerAdvice`) is the single place that turns any exception, anywhere in the app, into the same `ApiErrorResponse` JSON structure. `RestAuthenticationEntryPoint` and `RestAccessDeniedHandler` produce the same shape for 401/403 so a client never has to handle two different error formats depending on whether the failure was a business rule or a security check.
- **Rate limiting** — `RateLimitFilter` is a plain `OncePerRequestFilter`, not wired into the Spring Security chain, so it runs the same way regardless of whether the request is authenticated.
- **Caching** — `CacheConfig` wires up an in-process `ConcurrentMapCacheManager`. It's a single `CacheManager` bean, which is deliberate: moving to a shared cache (Redis, for a multi-instance deployment) is a change to that one bean, not to every `@Cacheable`/`@CacheEvict` annotation scattered across the services.
- **Connection pooling** — HikariCP settings live entirely in `application.yml`, tuned per Spring profile, rather than in code — no Java class owns pool sizing.

## Configuration and profiles

`application.yml` is a multi-document YAML file, split by Spring profile:

- **base** — sets the default active profile (`dev`).
- **dev** — H2 in-memory database, `h2-console` enabled, used for local development with no external dependencies.
- **postgres** — real PostgreSQL connection, all connection details and pool sizes read from required environment variables (no defaults) so nothing resembling a real credential exists in the file.
- **shared block** (server port, security credentials, CORS, rate limiting, actuator, logging) — applies regardless of which database profile is active. The admin/customer credentials here are also required environment variables with no defaults.
- **test** — placed last in the file specifically so it takes precedence over the shared block when the `test` profile is active; supplies fixed, obviously-fake fixture credentials so the automated test suite doesn't need real environment variables to run.

Spring Boot resolves multi-document YAML by giving later documents in the file precedence among the currently-active ones — the ordering above (shared block before `test`) is what makes the test fixtures actually win when `test` is active, rather than the shared block's unset-and-required variables blowing up the test run.

## Deployment shape

The `Dockerfile` is a two-stage build: a `maven:3.9-eclipse-temurin-17` stage compiles the jar, and only the resulting jar is copied into a slim `eclipse-temurin:17-jre-jammy` runtime image, run as a non-root user. `docker-compose.yml` runs that image alongside a real `postgres:16-alpine` container so local testing happens against the same database engine a real deployment would use, not just H2. See [`docs/deployment.md`](deployment.md) for the operational details; this section is only about how the image relates to the rest of the architecture — the image itself contains no environment-specific configuration, and everything that differs between local Compose, a staging environment, and production is supplied at container start time via environment variables, which is the same pattern `application.yml` already uses for the non-containerized `postgres` profile.

## Why these choices

A few decisions that could reasonably have gone another way:

- **Layered architecture over a more "clean architecture" / hexagonal split** — for a project this size (three entities, straightforward CRUD plus one order-placement workflow), a conventional controller/service/repository split gives the testability that mattered (each layer independently testable) without the ceremony of ports/adapters for a domain that doesn't have complex business rules independent of persistence.
- **DTOs on both request and response, even where they're nearly identical to the entity** — keeps a schema change to the entity (e.g. adding an internal-only column) from silently changing the API contract, and keeps validation annotations on request objects instead of entities that are also used for persistence.
- **In-process cache and rate-limit counters instead of Redis from the start** — correct for a single-instance deployment, and both are isolated behind a single bean/filter so introducing Redis later is a small, localized change rather than a rewrite. Reaching for Redis before there's a second instance to justify it would have been unnecessary complexity.
- **Optimistic locking (`@Version`) over pessimistic row locking for stock updates** — order placement is expected to be read-heavy/write-light per product relative to something like a flash sale, so optimistic locking (fail and let the caller retry on conflict) avoids holding database locks across a transaction, at the cost of needing to handle the occasional conflict — which `ProductOptimisticLockingIntegrationTest` verifies actually happens correctly under real concurrent writes.
