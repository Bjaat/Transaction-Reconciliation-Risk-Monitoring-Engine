# Transaction Reconciliation & Risk Monitoring Engine

A backend system that simulates a financial transaction-processing pipeline:
ingestion → validation → risk detection → persistence → settlement →
reconciliation → reporting/analytics.

Built as a **modular monolith** with Spring Boot, PostgreSQL, and a separate
Python/Pandas analytics component that consumes exported data.

> **Status:** Phase 2 — database schema, JPA entities, and repositories.
> No services, controllers, or business logic (risk/reconciliation rules) yet.

## Architecture

Layered architecture, enforced by package structure:

```
Controller -> Service -> Repository -> Database
```

```
src/main/java/com/reconciliation/engine/
├── config/           Spring configuration (beans, CORS, etc.) — empty so far
├── controller/       REST controllers — placeholder /api/status only so far
├── service/          Business logic / orchestration — empty so far
├── repository/       Spring Data JPA repositories (Phase 2)
├── entity/           JPA entities (Phase 2) — never exposed directly over the API
├── dto/              Request/response objects exposed over the API — empty so far
├── mapper/           Entity <-> DTO conversion — empty so far
├── exception/        Custom exceptions + centralized @ControllerAdvice — empty so far
├── risk/             Risk-detection rules (one class per rule) — empty so far
├── reconciliation/   Reconciliation engine — empty so far
└── common/           Enums (Phase 2) + audit base classes

src/main/resources/db/migration/
└── V1__init_schema.sql   Flyway migration: creates all 5 Phase 2 tables
```

Why this shape:
- **Entities never cross the API boundary** — controllers only ever see DTOs,
  so we can change the database schema without breaking API consumers.
- **Risk rules live in their own package**, one rule = one class, instead of
  one large `RiskService` method with a wall of `if` statements.
- **No microservices** — a single deployable unit is enough to demonstrate
  every skill this project is meant to showcase, without the operational
  overhead of distributed systems for something this size.

## Tech stack

- Java 17, Spring Boot 3.3, Spring Web, Spring Data JPA
- PostgreSQL (via Docker Compose for local dev)
- Maven
- JUnit 5, Mockito (added when the first service/tests are written)
- Python + Pandas for the separate analytics component (`/analytics`, added later)

## Running Phase 1 locally

### 1. Start PostgreSQL

```bash
docker compose up -d
```

This starts a Postgres 16 container on `localhost:5432` with:
- database: `reconciliation_db`
- user: `reconciliation_user`
- password: `reconciliation_pass`

### 2. Run the application

```bash
./mvnw spring-boot:run
```

(or `mvn spring-boot:run` if you don't have the wrapper yet — see note below)

### 3. Verify it's up

```bash
curl http://localhost:8080/api/status
```

Expected response:

```json
{"service":"txn-reconciliation-engine","status":"UP","timestamp":"..."}
```

> Note: no entities/tables exist yet, so the app will start and connect to
> Postgres, but there is nothing to query yet — that's Phase 2.

## Running Phase 2 locally

### 1. Run the tests (spins up a real Postgres container via Testcontainers — Docker must be running)

```bash
mvn clean test
```

> **Troubleshooting — "Could not find a valid Docker environment" / `BadRequestException (Status 400)`**
> If `docker version` and `docker run hello-world` both work fine but `mvn clean test` still fails this way, you're hitting a known Testcontainers-vs-Docker-Engine-29 API version mismatch (Docker Engine 29+ requires API ≥ 1.44; older Testcontainers versions don't negotiate it automatically). It's fixed by `src/test/resources/docker-java.properties`, already included in this project, which pins `api.version=1.44`.
>
> **Troubleshooting — "Mapped port can only be obtained after the container is started"**
> This happens if a PostgreSQL Testcontainer is shared across multiple test classes via a base class and started manually (the "singleton container" pattern) — Spring's `@ServiceConnection` support relies on the JUnit `@Testcontainers` extension itself starting the container, and doesn't reliably pick up a manually-started one shared from elsewhere. Each repository test class here declares and starts its own container directly with `@Testcontainers` + `@Container @ServiceConnection`, which avoids this.

### 2. Start Postgres for the running app, then the app itself

```bash
docker compose up -d
mvn spring-boot:run
```

Flyway applies `V1__init_schema.sql` automatically on startup. Watch the
logs for a line like:

```
Successfully applied 1 migration to schema "public"
```

### 3. Inspect the database

```bash
docker exec -it reconciliation-postgres psql -U reconciliation_user -d reconciliation_db
```

Then, inside `psql`:

```sql
-- List all tables
\dt

-- Inspect a table's columns, constraints, and indexes
\d accounts
\d transactions
\d settlements
\d risk_flags
\d reconciliation_logs

-- Confirm Flyway's own bookkeeping table exists and shows the migration as applied
SELECT version, description, success FROM flyway_schema_history;

-- Confirm the partial unique index on risk_flags exists
SELECT indexname, indexdef FROM pg_indexes WHERE tablename = 'risk_flags';
```

## Roadmap (phases)

1. ✅ Project scaffolding, Spring Boot setup, Postgres via Docker
2. ✅ Database schema (Flyway), JPA entities, repositories
3. Transaction API: create/get/list/filter/update + DTOs + validation
4. Exception handling + global error responses
5. Risk detection engine (rule-based)
6. Settlement ingestion + reconciliation engine
7. Reconciliation REST API
8. Reporting endpoints
9. Python/Pandas analytics component
10. Integration tests, polish, documentation pass
