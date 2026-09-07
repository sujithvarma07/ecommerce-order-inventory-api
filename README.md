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

```bash
mvn spring-boot:run
```

The app starts on `http://localhost:8080` with the `dev` profile active by default (H2 in-memory database, console at `/h2-console`).

To run against PostgreSQL, activate the `postgres` profile and set the relevant env vars:

```bash
export DB_HOST=localhost
export DB_PORT=5432
export DB_NAME=order_inventory_db
export DB_USERNAME=postgres
export DB_PASSWORD=postgres
mvn spring-boot:run -Dspring-boot.run.profiles=postgres
```

## API Endpoints

| Method | Endpoint                  | Description            |
|--------|----------------------------|-------------------------|
| POST   | `/api/v1/categories`       | Create a category       |
| GET    | `/api/v1/categories`       | List all categories     |
| GET    | `/api/v1/categories/{id}`  | Get a category by id    |
| PUT    | `/api/v1/categories/{id}`  | Update a category       |
| DELETE | `/api/v1/categories/{id}`  | Delete a category       |
| POST   | `/api/v1/products`         | Create a product         |
| GET    | `/api/v1/products`         | List all products (optionally filter with `?categoryId=`) |
| GET    | `/api/v1/products/{id}`    | Get a product by id      |
| PUT    | `/api/v1/products/{id}`    | Update a product         |
| PATCH  | `/api/v1/products/{id}/stock` | Adjust stock by a signed delta (`{"delta": -10}`); rejected if it would take stock negative |
| GET    | `/api/v1/products/low-stock?threshold=10` | List products at or below a stock threshold (defaults to 10) |
| DELETE | `/api/v1/products/{id}`    | Delete a product         |
| POST   | `/api/v1/orders`           | Place an order (list of `{productId, quantity}`) — deducts stock from each product |
| GET    | `/api/v1/orders`           | List all orders          |
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

## Error responses

All error responses (validation failures, not-found, conflicts, unexpected exceptions) share one JSON shape, produced by a single `@RestControllerAdvice`. See [`docs/api-error-format.md`](docs/api-error-format.md) for the exact structure and examples, including how field-level validation errors are reported.

## Monitoring

Spring Boot Actuator is enabled with the `health`, `info`, and `metrics` endpoints exposed:

| Endpoint | Description |
|----------|-------------|
| `GET /actuator/health` | Application health/status |
| `GET /actuator/info`   | Build/app info |
| `GET /actuator/metrics` | Available metrics |

Every request is also logged (method, URI, response status, duration) via a servlet filter, excluding `/actuator/**` paths to keep health-check polling out of the logs.

## API testing

A Postman collection covering every endpoint above — plus a handful of deliberate error-case requests (duplicate SKU, validation failure, not-found, insufficient stock, invalid status transition) — is at [`docs/postman/order-inventory-api.postman_collection.json`](docs/postman/order-inventory-api.postman_collection.json). Import it into Postman and set the `baseUrl` collection variable (defaults to `http://localhost:8080`).

## Documentation

- [`docs/coding-standards.md`](docs/coding-standards.md) — coding conventions used in this project
- [`docs/api-error-format.md`](docs/api-error-format.md) — standardized error response shape
- [`docs/postman/order-inventory-api.postman_collection.json`](docs/postman/order-inventory-api.postman_collection.json) — Postman collection for manual API testing
- [`docs/progress.md`](docs/progress.md) — running log of what's been built
