# API Error Response Format

Every error response from this API has the same shape, produced by `GlobalExceptionHandler`:

```json
{
  "timestamp": "2026-09-04T10:15:30",
  "status": 404,
  "error": "Not Found",
  "message": "Product not found with id: 42",
  "path": "/api/v1/products/42"
}
```

Validation failures (invalid request bodies) additionally include a `fieldErrors` map:

```json
{
  "timestamp": "2026-09-04T10:16:02",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed for one or more fields",
  "path": "/api/v1/categories",
  "fieldErrors": {
    "name": "Category name is required"
  }
}
```

## Status codes used

| Status | When |
|--------|------|
| 400 | Request body fails bean validation (`@Valid`) |
| 404 | The requested resource doesn't exist (`ResourceNotFoundException`) |
| 409 | Conflict — duplicate name/SKU, insufficient stock, or a concurrent-update conflict (`DuplicateResourceException`, `InsufficientStockException`, or an optimistic locking failure) |
| 500 | Anything unexpected |

## Why a custom exception hierarchy instead of `ResponseStatusException`

Week 1 used `ResponseStatusException` directly from the service layer. That scatters HTTP-status decisions across the business logic and ties every error to whatever `ResponseStatusException`'s default response shape happens to be. This replaces it with a small set of purpose-named exceptions (`ResourceNotFoundException`, `DuplicateResourceException`, `InsufficientStockException`, `InvalidOrderStateException` — the last one reserved for the order status-transition rules coming next) and one `@RestControllerAdvice` that's the single place deciding how each maps to a status code and response body. Service code stays focused on business rules; the HTTP concern lives in one place.
