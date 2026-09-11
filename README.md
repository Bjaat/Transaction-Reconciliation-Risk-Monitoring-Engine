# Transaction Reconciliation & Risk Monitoring Engine

A backend system that simulates a financial transaction-processing pipeline:
ingestion → validation → risk detection → persistence → settlement →
reconciliation → reporting/analytics.

Built as a **modular monolith** with Spring Boot and PostgreSQL, plus a separate
read-only Python/Pandas analytics component over the same database.

> **Status:** Phase 7 — transaction, reconciliation, and risk analytics with
> exact currency totals, UTC trends, and CSV/JSON outputs.

## Architecture

Layered architecture, enforced by package structure:

```
Controller -> Service -> Repository -> Database
                                      |
                                      v
                              Python/Pandas analytics
                                      |
                                      v
                                 CSV + JSON
```

```
src/main/java/com/reconciliation/engine/
├── controller/       REST controllers for transactions, reconciliation, risk, and reporting
├── service/          Transaction, reconciliation, risk, and reporting orchestration
├── repository/       Spring Data JPA repositories, specifications, and report projections
├── entity/           JPA entities — never exposed directly over the API
├── dto/              Request/response DTOs — transaction/*, reconciliation/*, risk/*, report/*
├── exception/        ResourceNotFoundException, BadRequestException, ApiError, GlobalExceptionHandler (Phase 3)
├── risk/             Independent rule-based risk checks
└── common/           Enums + audit base classes

src/main/resources/db/migration/
├── V1__init_schema.sql                       Initial five-table schema and constraints
├── V2__reconciliation_currency_support.sql   Reconciliation currency fields/result support
└── V3__reporting_indexes.sql                  Reporting range-filter indexes

analytics/
├── config.py, database.py, loaders.py         Secure read-only PostgreSQL ingestion
├── *_analysis.py                              Pure Pandas transformations
├── report.py, main.py                         CSV/JSON generation and CLI
└── tests/                                     Database-independent Python tests
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
- JUnit 5, Mockito, AssertJ, MockMvc, and Testcontainers
- Python 3.11+, Pandas, and psycopg for the separate analytics component (`/analytics`)

## Running locally

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
mvn spring-boot:run
```

The default profile keeps SQL logging quiet. To inspect formatted Hibernate SQL
during development, start with the `dev` profile:

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

### 3. Verify it's up

```bash
curl http://localhost:8080/api/status
```

Expected response:

```json
{"service":"txn-reconciliation-engine","status":"UP","timestamp":"..."}
```

## Testing and database verification

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

Flyway automatically applies or validates migrations `V1` through `V3` on
startup. A fresh database is left at schema version `v3`; the startup log
reports the exact number applied for that database.

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



## Reconciliation API (Phase 4)

Compares a transaction against settlement data (matched by business
`transactionReference`) and persists the outcome as a `ReconciliationLog`.
Reconciliation is **not idempotent by design** — each call appends a new
history entry rather than overwriting the last one, so re-running
reconciliation after a settlement arrives late preserves the earlier
`MISSING_SETTLEMENT` result alongside the later `MATCHED` one.

### Matching rules

For a transaction's settlements (found by the same `transactionReference`),
in order:

1. Zero settlements found → `MISSING_SETTLEMENT`
2. More than one settlement found → `DUPLICATE_SETTLEMENT`
3. Exactly one settlement found, then checked in this order:
   - Currencies differ → `CURRENCY_MISMATCH`
   - Amounts differ → `AMOUNT_MISMATCH`
   - Statuses disagree → `STATUS_MISMATCH` (mapping: `COMPLETED`↔`SETTLED`,
     `FAILED`↔`FAILED`, `REVERSED`↔`REVERSED`, `PENDING`/`PROCESSING`↔`PENDING`
     — `TransactionStatus` and `SettlementStatus` don't share values, so this
     mapping is a documented assumption, not a database-enforced rule)
   - Otherwise → `MATCHED`

### Reconcile a transaction

```http
POST /api/v1/reconciliations
Content-Type: application/json

{ "transactionReference": "BANK-TXN-10001" }
```

Response: `201 Created`

```json
{
  "id": 7,
  "transactionReference": "BANK-TXN-10001",
  "settlementReference": "PSP-REF-88213",
  "status": "MATCHED",
  "expectedAmount": 12500.5000,
  "actualAmount": 12500.5000,
  "expectedCurrency": "INR",
  "actualCurrency": "INR",
  "expectedStatus": "COMPLETED",
  "actualStatus": "SETTLED",
  "explanation": "Transaction and settlement match on amount, currency, and status",
  "reconciledAt": "2026-08-30T11:00:00"
}
```

`404 Not Found` if the transaction reference doesn't exist. `400 Bad Request`
if the request body fails validation.

### Reconciliation history

```http
GET /api/v1/reconciliations/{transactionReference}
```

Returns a JSON array of every past reconciliation result for that
transaction, newest first. `404` if the transaction itself doesn't exist;
an empty array (not an error) if it exists but has never been reconciled.

### Batch reconciliation

```http
POST /api/v1/reconciliations/run
```

Reconciles every transaction currently in the system. One transaction
failing doesn't stop the rest — failures are tallied, not thrown. Response:

```json
{
  "totalProcessed": 137,
  "matched": 110,
  "amountMismatch": 8,
  "currencyMismatch": 2,
  "statusMismatch": 5,
  "missingSettlement": 11,
  "duplicateSettlement": 1,
  "failed": 0
}
```

### Database changes (V2 migration)

`V1` is untouched. `V2__reconciliation_currency_support.sql` adds
`expected_currency`/`actual_currency` columns to `reconciliation_logs` and
widens the `result` `CHECK` constraint to allow `CURRENCY_MISMATCH` — both
genuinely required to satisfy the API response shape and matching rules
above; nothing else about the schema changed.

## Risk Detection API (Phase 5)

Risk rules are independent `RiskRule` implementations. An evaluation persists a
new `OPEN` flag only when that transaction/rule pair has no existing open flag,
preserving the database's partial-unique-index invariant.

Implemented rules:

- `LARGE_AMOUNT` (`HIGH`) — amount is at least `risk.rules.large-amount-threshold`
  (default `10000`).
- `DUPLICATE_TRANSACTION` (`CRITICAL`) — a nonblank upstream
  `externalReference` is shared by at least two transactions.

### Evaluate one transaction

```http
POST /api/v1/risk/evaluations
Content-Type: application/json

{ "transactionReference": "BANK-TXN-10001" }
```

Returns `201 Created` with the number of rules evaluated and only the flags
created by this run. Repeating the request while those flags remain `OPEN`
returns an empty `flagsCreated` array. Unknown transactions return `404`;
invalid requests return `400` using the existing error format.

### Risk-flag history

```http
GET /api/v1/risk/flags/{transactionReference}
```

Returns all flags newest first. An existing transaction with no flags returns
an empty array; an unknown transaction returns `404`.

### Batch evaluation

```http
POST /api/v1/risk/evaluations/run
```

Evaluates every transaction and returns `totalProcessed`, `flagsCreated`, and
`failed`. One transaction failure does not stop the remaining evaluations.

## Reporting API (Phase 6)

Reporting is read-only and derived from the existing transaction,
reconciliation-log, and risk-flag data. Aggregation happens in PostgreSQL;
entities are not loaded into application memory to calculate the summaries.

All endpoints accept optional inclusive `from` and `to` query parameters as
ISO-8601 offset timestamps. Omitting either side leaves that side unbounded.
If both are supplied, `from` must be before or equal to `to`.
Offsets are preserved semantically: each value is normalized to its UTC instant
before comparison with the existing PostgreSQL `TIMESTAMP` columns. The `from`
and `to` values echoed in responses are therefore UTC-normalized local timestamp
representations.

```http
GET /api/v1/reports/transactions?from=2026-09-01T00:00:00Z&to=2026-09-30T23:59:59Z
```

Returns the transaction total, counts by status and transaction type, and
count/amount totals grouped by currency. Monetary values in different
currencies are deliberately never added together. The window applies to
`transactionTimestamp`.

```http
GET /api/v1/reports/reconciliations?from=2026-09-01T00:00:00Z&to=2026-09-30T23:59:59Z
```

Returns the total reconciliation-log records and counts grouped by result.
Because reconciliation logs are audit history, every matching historical
record is counted. The window applies to `reconciledAt`.

```http
GET /api/v1/reports/risks?from=2026-09-01T00:00:00Z&to=2026-09-30T23:59:59Z
```

Returns the total risk flags and counts grouped by status, severity, and rule
code. Historical resolved/dismissed flags are included. The window applies to
`detectedAt`.

All three endpoints return `200 OK`. An empty dataset produces zero totals and
empty grouping arrays. Invalid timestamps or a reversed window return `400 Bad
Request` using the existing error response format.

### Reporting indexes (V3 migration)

`V3__reporting_indexes.sql` adds one index on
`reconciliation_logs.reconciled_at` and one on `risk_flags.detected_at`. These
columns are the range predicates used by their respective reports. Transaction
reporting already uses the timestamp index created in `V1`; no redundant or
speculative composite indexes are added.

## Python/Pandas Analytics (Phase 7)

Phase 7 reads the existing PostgreSQL data directly through a read-only
`psycopg` session. It selects only the fields required from `transactions`,
`reconciliation_logs`, and `risk_flags`; risk records are joined to
`transactions` only to recover the transaction business reference. SQL window
values are parameterized.

The analytics include exact transaction totals and averages by currency,
transaction status/type distributions, reconciliation outcome and rate
metrics, risk status/severity/rule distributions, transactions with multiple
risk flags, high-severity activity, and UTC daily trends. It generates
machine-readable CSV files and a JSON summary rather than adding a second API
or duplicating Java business logic.

Setup and execution:

```powershell
python -m venv .venv
.\.venv\Scripts\Activate.ps1
python -m pip install -r analytics\requirements.txt

$env:DB_HOST = "localhost"
$env:DB_PORT = "5432"
$env:DB_NAME = "reconciliation_db"
$env:DB_USER = "reconciliation_user"
$env:DB_PASSWORD = "your-local-password"

python -m analytics
python -m unittest discover -s analytics\tests -v
```

Optional inclusive `--from` and `--to` values must be ISO-8601 timestamps with
offsets. Use `--output <directory>` to override `analytics/output`. Generated
reports are ignored by Git. See `analytics/README.md` for the complete output
inventory, UTC semantics, and least-privilege database guidance.

## Roadmap (phases)

1. ✅ Project scaffolding, Spring Boot setup, Postgres via Docker
2. ✅ Database schema (Flyway), JPA entities, repositories
3. ✅ Transaction API: create/get/list/filter/update, DTOs, validation, global error handling
4. ✅ Reconciliation engine: reconcile/history/batch, V2 migration for currency support
5. ✅ Risk detection engine (rule-based)
6. ✅ Reporting endpoints: transaction, reconciliation, and risk summaries
7. ✅ Python/Pandas analytics component
8. Integration tests, polish, documentation pass
