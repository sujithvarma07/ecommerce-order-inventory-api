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

## API Endpoints (so far)

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
| DELETE | `/api/v1/products/{id}`    | Delete a product         |
| POST   | `/api/v1/orders`           | Place an order (list of `{productId, quantity}`) |
| GET    | `/api/v1/orders`           | List all orders          |
| GET    | `/api/v1/orders/{id}`      | Get an order by id       |
| PATCH  | `/api/v1/orders/{id}/status` | Update an order's status (`PENDING`/`CONFIRMED`/`SHIPPED`/`CANCELLED`) |
| DELETE | `/api/v1/orders/{id}`      | Delete an order          |

This closes out the week 1 scope (Category, Product, Order — CRUD, validation, JPA persistence, unit tests). Inventory deduction and richer business rules are next.

## Documentation

- [`docs/coding-standards.md`](docs/coding-standards.md) — coding conventions used in this project
- [`docs/progress.md`](docs/progress.md) — running log of what's been built
