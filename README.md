# Transaction Reconciliation & Risk Monitoring Engine

A backend system that simulates a financial transaction-processing pipeline:
ingestion → validation → risk detection → persistence → settlement →
reconciliation → reporting/analytics.

Built as a **modular monolith** with Spring Boot, PostgreSQL, and a separate
Python/Pandas analytics component that consumes exported data.

> **Status:** Phase 1 — project scaffolding only. No domain logic yet.

## Architecture

Layered architecture, enforced by package structure:

```
Controller -> Service -> Repository -> Database
```

```
src/main/java/com/reconciliation/engine/
├── config/           Spring configuration (beans, CORS, etc.)
├── controller/        REST controllers (HTTP in/out only, no business logic)
├── service/            Business logic / orchestration
├── repository/         Spring Data JPA repositories
├── entity/             JPA entities (never exposed directly over the API)
├── dto/                Request/response objects exposed over the API
├── mapper/             Entity <-> DTO conversion
├── exception/          Custom exceptions + centralized @ControllerAdvice handling
├── risk/               Risk-detection rules (one class per rule)
├── reconciliation/     Reconciliation engine (transaction <-> settlement matching)
└── common/             Shared enums and small utilities
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

## Roadmap (phases)

1. ✅ Project scaffolding, Spring Boot setup, Postgres via Docker
2. Core domain: `Account` / `Transaction` entities, enums, repositories
3. Transaction API: create/get/list/filter/update + DTOs + validation
4. Exception handling + global error responses
5. Risk detection engine (rule-based)
6. Settlement model + reconciliation engine
7. Reconciliation REST API + persisted reconciliation logs
8. Reporting endpoints
9. Python/Pandas analytics component
10. Integration tests, polish, documentation pass
