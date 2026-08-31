# Transaction Reconciliation & Risk Monitoring Engine

A backend system that simulates a financial transaction-processing pipeline:
ingestion → validation → risk detection → persistence → settlement →
reconciliation → reporting/analytics.

Built as a **modular monolith** with Spring Boot, PostgreSQL, and a separate
Python/Pandas analytics component that consumes exported data.

> **Status:** Phase 3 — Transaction REST API (create/get/list/filter/update), DTOs, validation, global error handling.
> Risk detection, settlement ingestion, and reconciliation are not implemented yet.

## Architecture

Layered architecture, enforced by package structure:

```
Controller -> Service -> Repository -> Database
```

```
src/main/java/com/reconciliation/engine/
├── config/           Spring configuration — empty so far
├── controller/       REST controllers: TransactionController (Phase 3)
├── service/          Business logic: TransactionService (Phase 3)
├── repository/       Spring Data JPA repositories + TransactionSpecifications (Phase 3)
├── entity/           JPA entities — never exposed directly over the API
├── dto/              Request/response DTOs (Phase 3) — PagedResponse, transaction/*
├── mapper/           Entity <-> DTO conversion — kept inline on the DTO (see below); empty so far
├── exception/        ResourceNotFoundException, BadRequestException, ApiError, GlobalExceptionHandler (Phase 3)
├── risk/             Risk-detection rules — empty so far
├── reconciliation/   Reconciliation engine — empty so far
└── common/           Enums + audit base classes

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

## Transaction API (Phase 3)

All endpoints are under `/api/v1/transactions`. Entities are never returned
directly — every response is a DTO (`TransactionResponse`).

> **Note on field naming vs. the schema:** `accountId` in query params and
> `accountNumber` in request/response bodies both refer to the business
> `Account.accountNumber` (e.g. `"ACC-1001"`), not the internal database id.
> `transactionReference` is the client/upstream-assigned business identifier;
> `externalReference` is a separate, optional upstream-processor reference.
> There is no `description` field — it isn't part of the Phase 2 schema, and
> adding one was out of scope for this phase (see Known Limitations below).

### Create

```http
POST /api/v1/transactions
Content-Type: application/json

{
  "transactionReference": "BANK-TXN-10001",
  "accountNumber": "ACC-1001",
  "amount": 12500.50,
  "currency": "INR",
  "transactionType": "PAYMENT",
  "transactionTimestamp": "2026-08-30T10:30:00",
  "externalReference": "PSP-REF-88213"
}
```

`transactionType` must be one of `DEPOSIT`, `WITHDRAWAL`, `TRANSFER`,
`PAYMENT`, `REFUND`. The transaction is always created with
`status: PENDING` — a client cannot set the initial status.

Response: `201 Created`

```json
{
  "id": 42,
  "transactionReference": "BANK-TXN-10001",
  "accountNumber": "ACC-1001",
  "amount": 12500.5000,
  "currency": "INR",
  "transactionType": "PAYMENT",
  "status": "PENDING",
  "transactionTimestamp": "2026-08-30T10:30:00",
  "externalReference": "PSP-REF-88213",
  "createdAt": "2026-08-30T10:30:05.123",
  "updatedAt": "2026-08-30T10:30:05.123"
}
```

`404 Not Found` if `accountNumber` doesn't match an existing account.
`400 Bad Request` with `fieldErrors` for validation failures.
`409 Conflict` if `transactionReference` already exists.

### Get

```http
GET /api/v1/transactions/{id}
```

`{id}` is the internal numeric id (the `id` field in the response above, not
`transactionReference`). Returns `200 OK` or `404 Not Found`.

### List / Filter

```http
GET /api/v1/transactions?page=0&size=20
GET /api/v1/transactions?accountId=ACC-1001
GET /api/v1/transactions?status=COMPLETED
GET /api/v1/transactions?transactionType=PAYMENT
GET /api/v1/transactions?currency=INR
GET /api/v1/transactions?from=2026-08-01T00:00:00Z&to=2026-08-30T23:59:59Z
GET /api/v1/transactions?minAmount=1000&maxAmount=50000
```

All filters are optional and combinable. `status`/`transactionType` values
are validated against the real enums — an unrecognized value returns `400`,
not a silently-empty result. Response:

```json
{
  "content": [ { "...": "TransactionResponse" } ],
  "page": 0,
  "size": 20,
  "totalElements": 137,
  "totalPages": 7,
  "first": true,
  "last": false
}
```

### Update

```http
PUT /api/v1/transactions/{id}
Content-Type: application/json

{
  "status": "COMPLETED",
  "externalReference": "PSP-REF-88213-CONFIRMED"
}
```

Only `status` and `externalReference` are mutable. `transactionReference`,
`accountNumber`, `amount`, `currency`, `transactionType`, and
`transactionTimestamp` cannot be changed after creation — they aren't even
fields on `UpdateTransactionRequest`, so there's no way to submit them.
No status-transition rules are enforced yet (any valid enum value is
accepted); that belongs to a later phase.

Returns `200 OK` with the updated `TransactionResponse`, or `404 Not Found`.

### Errors

Every error follows the same shape:

```json
{
  "timestamp": "2026-08-30T10:30:00",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed",
  "path": "/api/v1/transactions",
  "fieldErrors": {
    "amount": "must be greater than 0",
    "currency": "must be a 3-letter ISO 4217 code"
  }
}
```

`fieldErrors` is omitted (not present in the JSON) for non-validation
errors. Unexpected server errors return `500` with a generic message — the
real exception is logged server-side, never returned to the client.

### Known limitations (Phase 3)

- No `description`/notes field — not part of the Phase 2 schema; would need
  a new migration, which was out of scope here.
- No optimistic locking (`@Version`) on `Transaction` yet — concurrent
  updates to the same transaction can overwrite each other silently. Worth
  adding before this becomes a multi-writer system.
- `PUT` fully replaces the mutable fields rather than supporting partial
  (`PATCH`-style) updates — acceptable given there are only two mutable
  fields today, but worth revisiting if more become mutable later.
- No idempotency handling beyond the database's unique constraint on
  `transaction_reference` (a resubmitted create returns `409`, not the
  original `201` response).



1. ✅ Project scaffolding, Spring Boot setup, Postgres via Docker
2. ✅ Database schema (Flyway), JPA entities, repositories
3. ✅ Transaction API: create/get/list/filter/update, DTOs, validation, global error handling
4. Risk detection engine (rule-based)
5. Settlement ingestion + reconciliation engine
6. Reconciliation REST API
7. Reporting endpoints
8. Python/Pandas analytics component
9. Integration tests, polish, documentation pass
