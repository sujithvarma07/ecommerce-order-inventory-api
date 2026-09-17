# Performance Notes

This documents how to reproduce and capture measurable results for the Week 2/3 performance
work (indexes, pagination, caching, connection pool tuning), and what to look for in each case.
The results table at the bottom is intentionally left blank — fill it in after running the steps
below locally with the app started (`mvn spring-boot:run`), since the numbers only mean something
measured against a real running instance and real data volume, not typed in from memory.

## 1. Setup

1. Start the app locally against the `dev` profile (H2 in-memory):
   ```bash
   export ADMIN_USERNAME=admin ADMIN_PASSWORD=<choose-one> \
          CUSTOMER_USERNAME=customer CUSTOMER_PASSWORD=<choose-one>
   mvn spring-boot:run
   ```
2. Seed enough data that pagination/indexing differences are actually visible — a handful of rows
   won't show anything. Create 1 category and ~200 products in it (a quick loop with `curl`, or
   import the Postman collection and run "Create Product" repeatedly with a collection runner).
3. Run `scripts/benchmark.sh` from the project root:
   ```bash
   ADMIN_USERNAME=admin ADMIN_PASSWORD=<same-as-above> ./scripts/benchmark.sh
   ```
   It hits the by-id endpoint repeatedly (to show the cache's effect), the paginated list
   endpoint, and the category-filtered (indexed) endpoint, printing per-request and average
   response times.

## 2. What each result demonstrates

- **Cache effect (`GET /products/{id}`)**: the first call is a cache miss (hits H2 via
  `ProductRepository.findById`); every call after that is served from the in-process
  `ConcurrentMapCacheManager` cache added in Week 3 and should be visibly faster and more
  consistent than the first call, with no further DB round trips until the entry is evicted by a
  write. If the "cold" and "cached" averages come out basically identical, that's a signal the
  cache isn't being hit — check `@EnableCaching` is picked up and `CacheConfig` is on the
  classpath.
- **Indexed filter (`GET /products?categoryId=`)**: `idx_products_category_id` (added in Week 2)
  should let this stay flat as the product count grows, rather than degrading linearly. To see the
  effect directly rather than inferring it from timing, run `EXPLAIN ANALYZE` against a Postgres
  instance (see below) — H2's query plan output is less informative than Postgres's.
- **Paginated list (`GET /products?page=&size=`)**: comparing `size=20` against a much larger
  `size=500` on the same dataset shows the cost of fetching/serializing a large result set — this
  is the concrete reason the list endpoints became paginated in Week 3 rather than returning
  everything.

## 3. Confirming index usage directly (optional, Postgres profile)

If you have a local Postgres available, run against the `postgres` profile instead and inspect the
query plan directly:

```sql
EXPLAIN ANALYZE SELECT * FROM products WHERE category_id = 1;
EXPLAIN ANALYZE SELECT * FROM orders WHERE customer_email = 'jane@example.com';
EXPLAIN ANALYZE SELECT * FROM orders WHERE status = 'PENDING';
```

Look for `Index Scan using idx_products_category_id` (or `idx_orders_customer_email` /
`idx_orders_status`) rather than `Seq Scan` in the plan output. To see the contrast, you can
temporarily `DROP INDEX idx_products_category_id;`, re-run the same `EXPLAIN ANALYZE`, note the
plan switches to a sequential scan, then `CREATE INDEX idx_products_category_id ON
products(category_id);` to restore it (Hibernate will also recreate it automatically on next
startup against the `dev`/`update` profile, or via the mapped `@Index` on the entity for a fresh
Postgres schema).

## 4. Results

Fill in after running `scripts/benchmark.sh` locally (see Setup above). Note the approximate
product row count used, since these numbers are meaningless without that context.

Row count used: ___

| Scenario | Avg response time | Notes |
|---|---|---|
| `GET /products/{id}` — first call (cold) | | |
| `GET /products/{id}` — subsequent calls (cached) | | |
| `GET /products?page=0&size=20` | | |
| `GET /products?categoryId=&page=0&size=20` | | |
