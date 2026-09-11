# Transaction Reconciliation & Risk Monitoring Engine

A financial transaction processing and monitoring backend built with **Java 17, Spring Boot, PostgreSQL, and Python/Pandas**.

The system processes transactions, performs settlement reconciliation, detects rule-based risk conditions, maintains audit history, provides reporting APIs, and exposes a separate read-only Python/Pandas analytics layer.

## Project Status

**Completed through Phase 7 — Core backend, reconciliation, risk monitoring, reporting, and Python/Pandas analytics.**

The project is intentionally designed as a **modular monolith** rather than a microservices system. The goal is to demonstrate production-oriented backend engineering, database design, financial data correctness, testing, reconciliation, risk detection, and analytics without introducing unnecessary distributed-system complexity.

---

## Architecture

```text
                         ┌─────────────────────────┐
                         │       REST Clients      │
                         └────────────┬────────────┘
                                      │
                                      ▼
                         ┌─────────────────────────┐
                         │   Spring Boot Backend   │
                         │                         │
                         │ Controllers             │
                         │      ↓                  │
                         │ Services                │
                         │      ↓                  │
                         │ Repositories            │
                         └────────────┬────────────┘
                                      │
                                      ▼
                         ┌─────────────────────────┐
                         │       PostgreSQL        │
                         │                         │
                         │ Transactions            │
                         │ Settlements             │
                         │ Risk Flags              │
                         │ Reconciliation Logs     │
                         └────────────┬────────────┘
                                      │
                              Read-only SQL
                                      │
                                      ▼
                         ┌─────────────────────────┐
                         │    Python / Pandas      │
                         │      Analytics          │
                         │                         │
                         │ Aggregations             │
                         │ Trends                   │
                         │ Risk metrics             │
                         │ Reconciliation metrics   │
                         │ CSV / JSON reports       │
                         └─────────────────────────┘
```

The Java/Spring Boot application remains the **system of record**. The Python component is an analytics layer that reads PostgreSQL data without modifying the database.

---

## Core Features

### 1. Transaction Management

REST APIs for:

* Creating transactions
* Retrieving transactions
* Listing and paginating transactions
* Filtering transactions
* Updating supported transaction fields
* Bean validation
* Consistent API error responses

Supported transaction types include:

```text
DEPOSIT
WITHDRAWAL
TRANSFER
PAYMENT
REFUND
```

Transactions use explicit business references and monetary values stored using PostgreSQL `NUMERIC(19,4)` rather than floating-point types.

---

### 2. PostgreSQL Persistence

The backend uses:

* PostgreSQL 16
* Spring Data JPA
* Hibernate
* Flyway database migrations
* Database-side constraints and indexes

Flyway owns schema creation and migrations while Hibernate is configured to **validate** the existing schema rather than modify it automatically.

Migrations currently include:

```text
V1 - Initial schema
V2 - Reconciliation currency support
V3 - Reporting timestamp indexes
```

---

### 3. Settlement & Reconciliation

The reconciliation engine compares transaction and settlement information and classifies reconciliation outcomes.

Supported outcomes include:

```text
MATCHED
MISSING_SETTLEMENT
DUPLICATE_SETTLEMENT
CURRENCY_MISMATCH
AMOUNT_MISMATCH
STATUS_MISMATCH
```

Transaction and settlement status mappings are explicitly defined rather than relying on string equality.

Reconciliation results are persisted as **append-only reconciliation history**, providing an audit trail of reconciliation attempts.

---

### 4. Rule-Based Risk Detection

Risk detection uses an extensible `RiskRule` architecture.

Current rules include:

```text
LARGE_AMOUNT
DUPLICATE_TRANSACTION
```

The architecture allows additional rules to be added independently without turning the risk engine into one large conditional method.

Risk flags maintain:

* Rule code
* Severity
* Status
* Detection timestamp
* Associated transaction
* Historical records

A PostgreSQL partial unique index prevents duplicate **OPEN** risk flags for the same transaction and rule while still allowing historical flags to remain stored.

---

### 5. Reporting API

Read-only reporting endpoints provide database-side aggregation for:

```text
Transaction reports
Reconciliation reports
Risk reports
```

Transaction reporting includes:

* Total transaction counts
* Counts by status
* Counts by transaction type
* Currency-separated totals
* Currency-separated monetary amounts

Reconciliation reporting includes:

* Total reconciliation attempts
* Outcome distributions

Risk reporting includes:

* Total risk flags
* Status distribution
* Severity distribution
* Rule distribution

Optional UTC-normalized time windows can be supplied using `from` and `to` parameters.

Aggregations are performed in PostgreSQL rather than loading the entire dataset into Java memory.

---

## Python / Pandas Analytics

Phase 7 adds a separate Python analytics component.

```text
PostgreSQL
    ↓
Read-only parameterized SQL
    ↓
Pandas DataFrames
    ↓
Analytics
    ↓
CSV / JSON reports
```

The analytics component provides:

### Transaction analytics

* Exact transaction totals and averages by currency
* Transaction status distributions
* Transaction type distributions
* Daily transaction trends
* Currency-separated financial metrics

### Reconciliation analytics

* Reconciliation outcome counts
* Reconciliation rates
* Mismatch rates
* Daily reconciliation trends

### Risk analytics

* Risk status distributions
* Risk severity distributions
* Risk rule distributions
* Open risk flag metrics
* High-severity risk metrics
* Transactions with multiple risk flags
* Daily risk trends

The analytics CLI supports inclusive `--from` and `--to` filtering using offset-aware timestamps.

Financial amounts remain represented using `Decimal`, and currencies are never combined into a single monetary total.

---

## Financial Data Correctness

Several design decisions specifically address financial data handling.

### Monetary values

Financial amounts use:

```text
NUMERIC(19,4)
```

rather than floating-point types.

This avoids binary floating-point representation problems for monetary calculations.

### Currency separation

Amounts from different currencies are never combined.

For example:

```text
INR totals
USD totals
EUR totals
```

remain separate metrics.

### Timestamp handling

Timestamp filters are normalized through UTC instants.

Offset-aware inputs are converted to their corresponding UTC instant before database filtering, preventing accidental loss of timezone information.

---

## Database Design

The main domain tables include:

```text
accounts
transactions
settlements
risk_flags
reconciliation_logs
```

Important design choices include:

### Risk flag foreign key

`risk_flags` maintains a real foreign-key relationship to transactions because a risk flag is generated from a known internal transaction.

### Settlement business reference

Settlement and reconciliation data use business transaction references where appropriate because unmatched external settlement data is a valid reconciliation condition.

### Partial unique index

The risk system uses a partial unique index conceptually equivalent to:

```sql
UNIQUE (transaction_id, rule_code)
WHERE status = 'OPEN'
```

This prevents duplicate active alerts without deleting historical risk records.

---

## Testing

The project uses multiple levels of testing.

### Java

* JUnit 5
* Mockito
* AssertJ
* Spring Boot Test
* MockMvc
* Testcontainers
* Real PostgreSQL integration tests

Database-specific behavior is tested against PostgreSQL rather than relying exclusively on an in-memory database.

### Python

The Phase 7 analytics component contains automated tests covering:

* Financial calculations
* Currency separation
* Distributions
* Reconciliation metrics
* Risk metrics
* Timestamp filtering
* Empty datasets
* Report generation

Phase 7 Python testing completed with:

```text
29 tests
29 passed
0 failed
0 skipped
```

The Java project contains the broader backend test suite covering persistence, APIs, reconciliation, risk detection, and reporting.

---

## Local Development

### Requirements

Install:

* Java 17
* Maven 3.9+
* Docker Desktop
* PostgreSQL 16 if running PostgreSQL outside Docker
* Python 3.x for the analytics component

---

## Start PostgreSQL

From the project root:

```powershell
docker compose up -d
```

The development database is configured through the project's Docker Compose configuration.

---

## Run the Spring Boot application

```powershell
mvn spring-boot:run
```

The application starts on:

```text
http://localhost:8080
```

Health/status endpoint:

```text
GET /api/status
```

---

## Run Java tests

Docker Desktop should be running because integration tests use Testcontainers.

```powershell
mvn clean test
```

### Docker Engine 29 / Testcontainers compatibility

The project includes:

```text
src/test/resources/docker-java.properties
```

with the required Docker API configuration:

```properties
api.version=1.44
```

This is intentionally retained for compatibility with the Docker/Testcontainers setup used by the project.

---

## Python Analytics

The Phase 7 analytics component reads from PostgreSQL using a read-only database connection.

The analytics component does **not** modify application data.

Typical workflow:

```text
Start PostgreSQL
      ↓
Populate application data
      ↓
Run Python analytics
      ↓
Generate CSV / JSON reports
```

Generated reports are not intended to be committed to Git.

---

## Project Structure

```text
src/
├── main/
│   ├── java/com/reconciliation/engine/
│   │   ├── common/
│   │   ├── config/
│   │   ├── controller/
│   │   ├── dto/
│   │   ├── entity/
│   │   ├── exception/
│   │   ├── reconciliation/
│   │   ├── repository/
│   │   ├── risk/
│   │   └── service/
│   │
│   └── resources/
│       ├── db/migration/
│       └── application.yml
│
└── test/
    └── java/
        └── com/reconciliation/engine/
│
└── <Python analytics component>
│
├── pom.xml
├── docker-compose.yml
├── .gitignore
└── README.md
```

---

## Why a Modular Monolith?

The project deliberately uses a modular monolith instead of microservices.

For the scope of this system, a modular monolith provides:

* Clear separation of business domains
* Simple deployment
* Transactional database access
* Easier local development
* Lower operational complexity
* Enough architectural structure to evolve individual modules

Microservices, Kafka, Redis, Spark, and other distributed infrastructure were intentionally avoided because they would add operational complexity without providing meaningful value at this project scale.

---

## Key Engineering Decisions

| Decision                        | Reason                                           |
| ------------------------------- | ------------------------------------------------ |
| Java 17                         | Stable LTS Java platform                         |
| Spring Boot                     | Mature backend ecosystem                         |
| PostgreSQL                      | Relational integrity and strong SQL capabilities |
| Flyway                          | Version-controlled database migrations           |
| Hibernate validation            | Prevents ORM from silently changing schema       |
| `NUMERIC(19,4)`                 | Correct monetary representation                  |
| DTOs                            | Keeps entities out of the API boundary           |
| Rule-based risk architecture    | Easy to extend with new rules                    |
| Partial unique index            | Prevents duplicate open risk alerts              |
| Append-only reconciliation logs | Auditability                                     |
| PostgreSQL aggregation          | Efficient reporting at database level            |
| Testcontainers                  | Tests real PostgreSQL behavior                   |
| Python/Pandas                   | Flexible analytical processing                   |
| Read-only analytics DB access   | Keeps Java backend as system of record           |
| UTC normalization               | Consistent timestamp filtering                   |

---

## Development Roadmap

```text
Phase 1  ✅ Project foundation
Phase 2  ✅ Persistence and database schema
Phase 3  ✅ Transaction REST API
Phase 4  ✅ Settlement reconciliation
Phase 5  ✅ Rule-based risk detection
Phase 6  ✅ Reporting and analytics API
Phase 6  ✅ Hardening and integration improvements
Phase 7  ✅ Python/Pandas analytics
```

The core planned implementation is complete. Future work would focus on production hardening rather than adding unnecessary functionality.

---

## Future Production Considerations

If this system were moved toward production scale, potential next steps would include:

* Authentication and authorization
* API rate limiting
* Structured logging
* Metrics and distributed tracing
* Optimistic locking/concurrency controls
* Idempotency keys for transaction ingestion
* Background/batch reconciliation
* Partitioning for very large transaction tables
* More advanced risk rules
* CI/CD pipelines
* Secret management
* Database backup and recovery strategy
* Load and performance testing

These are deliberately treated as production evolution rather than requirements for the current project scope.

---

## Author

**Bjaat**

GitHub: [@Bjaat](https://github.com/Bjaat)
