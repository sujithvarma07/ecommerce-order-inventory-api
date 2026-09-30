# Deployment

This documents how to build and run the app as a container, both locally (via Docker Compose,
for a full local stack including a real Postgres instance) and as a starting point for a real
cloud deployment.

## Prerequisites

- Docker and Docker Compose (Docker Desktop on macOS includes both)

## Running locally with Docker Compose

This spins up the app alongside a real Postgres container, which is the closest thing to a
production-like environment you can run on a laptop — closer than the H2 in-memory `dev` profile
used for day-to-day development.

1. Copy the env template and fill in your own values:
   ```bash
   cp .env.example .env
   ```
   Edit `.env` and set `DB_PASSWORD`, `ADMIN_PASSWORD`, and `CUSTOMER_PASSWORD` — these are not
   filled in for you on purpose, consistent with how the app already refuses to start without
   real credentials (see README > Configuration). `.env` is gitignored; nothing in it gets
   committed.

2. Build and start everything:
   ```bash
   docker compose up --build
   ```
   This builds the app image from the multi-stage `Dockerfile` (Maven build stage, then a slim
   JRE runtime stage — the final image doesn't carry the JDK or Maven, only the built jar and a
   JRE), starts Postgres, waits for Postgres to report healthy, then starts the app against it.

3. Once both containers report healthy:
   - App: http://localhost:8080
   - Swagger UI: http://localhost:8080/swagger-ui.html
   - Postgres: reachable on `localhost:5432` with the credentials from `.env`, if you want to
     connect a DB client directly

4. Stop everything with `docker compose down` (add `-v` to also drop the Postgres data volume and
   start clean next time).

## What the image does and doesn't contain

- The runtime image runs as a non-root user, not the container default root.
- No credentials are baked into the image at build time — every credential (`DB_PASSWORD`,
  `ADMIN_PASSWORD`, `CUSTOMER_PASSWORD`, etc.) is supplied at container start via environment
  variables, exactly like running the jar directly. The image is the same regardless of which
  environment it's deployed to; only the env vars change.
- `SPRING_PROFILES_ACTIVE=postgres` is set as the image default, since a containerized deployment
  is assumed to run against a real database rather than the in-memory H2 `dev` profile.

## Building the image without Compose

```bash
docker build -t order-inventory-api:local .
docker run --rm -p 8080:8080 \
  -e DB_HOST=<your-db-host> -e DB_PORT=5432 -e DB_NAME=order_inventory_db \
  -e DB_USERNAME=<your-db-user> -e DB_PASSWORD=<your-db-password> \
  -e ADMIN_USERNAME=admin -e ADMIN_PASSWORD=<your-choice> \
  -e CUSTOMER_USERNAME=customer -e CUSTOMER_PASSWORD=<your-choice> \
  order-inventory-api:local
```

## Taking this to a real cloud environment

The image built here is a standard, self-contained Spring Boot container with no cloud-specific
assumptions baked in, so it's a reasonable starting point for most container-hosting options
(a managed container service, a Kubernetes cluster, a PaaS that accepts a Dockerfile). None of
that is set up in this repo yet — this is deliberately scoped to "build and run the container
correctly," with the specific hosting target left open since it depends on what's available
(budget, existing infrastructure, team preference). Whatever the target turns out to be, the
same two requirements carry over unchanged:

- Real credentials supplied via that platform's environment variable / secret configuration —
  never baked into the image or committed to the repo (see README > Configuration for the full
  list of required variables).
- A reachable Postgres database — either the platform's managed database offering, or a
  separately hosted one — with `DB_HOST`/`DB_PORT`/`DB_NAME`/`DB_USERNAME`/`DB_PASSWORD` pointed
  at it.

The container's health check (`GET /actuator/health`, already wired into `docker-compose.yml` and
into the `Dockerfile`'s runtime expectations) is what most platforms use to decide whether a
freshly started instance is ready for traffic, so it should be pointed at that same endpoint
wherever this ends up running.
