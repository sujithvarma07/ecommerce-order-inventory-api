#!/usr/bin/env bash
#
# Local benchmarking helper for the performance work in docs/performance-notes.md.
# Run this against a locally running instance of the app (dev profile / H2) after seeding
# some data. It does not hit any remote service and makes no changes to the database beyond
# what you seed yourself.
#
# Usage:
#   ADMIN_USERNAME=... ADMIN_PASSWORD=... ./scripts/benchmark.sh [baseUrl] [productId] [categoryId]
#
# Defaults: baseUrl=http://localhost:8080, productId=1, categoryId=1

set -euo pipefail

BASE_URL="${1:-http://localhost:8080}"
PRODUCT_ID="${2:-1}"
CATEGORY_ID="${3:-1}"
RUNS=20

time_request() {
  local url="$1"
  curl -o /dev/null -s -w "%{time_total}\n" "$url"
}

avg() {
  awk '{ sum += $1; n += 1 } END { if (n > 0) printf "%.4f", sum / n; else print "n/a" }'
}

echo "== Benchmark target: $BASE_URL =="
echo

echo "-- GET /api/v1/products/$PRODUCT_ID (cold, first call goes to the DB) --"
time_request "$BASE_URL/api/v1/products/$PRODUCT_ID"

echo
echo "-- GET /api/v1/products/$PRODUCT_ID x $RUNS (cache should keep these fast and flat) --"
for i in $(seq 1 "$RUNS"); do
  time_request "$BASE_URL/api/v1/products/$PRODUCT_ID"
done | tee /tmp/cached_times.txt
echo "average: $(avg < /tmp/cached_times.txt)s"

echo
echo "-- GET /api/v1/products?page=0&size=20 x $RUNS (paginated list) --"
for i in $(seq 1 "$RUNS"); do
  time_request "$BASE_URL/api/v1/products?page=0&size=20"
done | tee /tmp/list_times.txt
echo "average: $(avg < /tmp/list_times.txt)s"

echo
echo "-- GET /api/v1/products?categoryId=$CATEGORY_ID&page=0&size=20 x $RUNS (indexed category filter) --"
for i in $(seq 1 "$RUNS"); do
  time_request "$BASE_URL/api/v1/products?categoryId=$CATEGORY_ID&page=0&size=20"
done | tee /tmp/category_times.txt
echo "average: $(avg < /tmp/category_times.txt)s"

echo
echo "Done. Copy the averages above into the results table in docs/performance-notes.md."
