# Coding Standards

Conventions followed in this project, based on a review of common Spring Boot best practices.

## Package structure

Layered by responsibility rather than by feature, since the project is small enough that this stays easy to navigate:

- `entity` — JPA entities only, no business logic
- `repository` — Spring Data JPA interfaces
- `dto` — request/response objects; entities are never returned directly from controllers
- `service` / `service.impl` — interface + implementation split, so controllers depend on abstractions
- `controller` — thin; delegates to services, no business logic
- `exception` — custom exceptions and centralized error handling

## Naming

- Classes: `PascalCase`
- Methods/variables: `camelCase`
- REST paths: plural nouns, lowercase, hyphenated if multi-word (`/api/v1/order-items`)
- DTOs suffixed `Request` / `Response` depending on direction

## Controllers

- Controllers only handle HTTP concerns: binding, validation trigger (`@Valid`), status codes.
- No business logic in controllers — always delegate to a service.
- Return `ResponseEntity<T>` explicitly so status codes are intentional, not implicit.

## Validation

- Request DTOs use `jakarta.validation` annotations (`@NotBlank`, `@Size`, etc.).
- Validation errors are surfaced as 400 responses with field-level detail (handled via a global exception handler, see `docs/progress.md` for status).

## Services

- Business rules and orchestration live here, not in controllers or repositories.
- Methods that mutate data are annotated `@Transactional`.
- Services depend on repository interfaces, not on JPA/Hibernate specifics directly.

## Entities

- Every entity tracks `createdAt` / `updatedAt` via `@PrePersist` / `@PreUpdate`.
- No Lombok `@Data` on entities (to avoid `equals`/`hashCode`/`toString` pulling in lazy-loaded associations) — explicit `@Getter`/`@Setter` instead.

## Git

- Small, focused commits — one logical change per commit.
- Commit messages in imperative mood ("Add category endpoints", not "Added" or "Adding").
