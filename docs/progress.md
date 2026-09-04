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

## Week 2 — Business Logic & Database Integration (in progress)

- [x] Standardized exception handling and API error responses — replaced the Week 1 `ResponseStatusException` calls with a small domain exception hierarchy (`ResourceNotFoundException`, `DuplicateResourceException`, `InsufficientStockException`, `InvalidOrderStateException`) and one `GlobalExceptionHandler` (`@RestControllerAdvice`) that returns a consistent JSON error shape everywhere, including field-level detail on validation failures — see `docs/api-error-format.md`
- [x] Optimized entities/relationships — added indexes on the columns actually being queried (`products.category_id`, `products.sku`, `orders.customer_email`, `orders.status`) and optimistic locking (`@Version` on `Product`) so concurrent stock updates fail safely instead of silently overwriting each other
- [ ] Business logic: actually deduct stock on order placement (currently still validated only, not decremented) and restore it on cancellation
- [ ] Order status workflow rules (using `InvalidOrderStateException`, added above but not yet wired in)
- [ ] Expanded CRUD: stock adjustment endpoint, low-stock query
- [ ] Logging and application-level monitoring
- [ ] Postman collection for API testing

### Notes

- Tests were updated to assert against the new exception types rather than `ResponseStatusException`.

## Week 3 — Security, Performance & Integration

- [ ] Not started

## Week 4 — Testing, Deployment & Documentation

- [ ] Not started

---

### Notes

- Chose Category as the first slice because it's the simplest independent entity — good for shaking out project conventions (DTO shape, service/controller split, error responses) before building out Product, Order, and Inventory on top of the same pattern.
- Global exception handling (`@ControllerAdvice`) is deliberately deferred to the Week 2 scope item that calls it out explicitly; for now, service-layer errors use `ResponseStatusException` directly.
