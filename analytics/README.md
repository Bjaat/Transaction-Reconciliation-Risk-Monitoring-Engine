# Python/Pandas Analytics

Phase 7 is a read-only analytics layer over the PostgreSQL database owned by
the Spring Boot application. It does not reproduce transaction,
reconciliation, or risk business logic.

## Requirements

- Python 3.11 or newer
- PostgreSQL containing the Flyway-managed application schema
- Network access from Python to PostgreSQL

From the repository root on PowerShell:

```powershell
python -m venv .venv
.\.venv\Scripts\Activate.ps1
python -m pip install -r analytics\requirements.txt
```

## Configuration

Set the database values in the process environment. Credentials are required
and are never embedded in source or written to reports.

```powershell
$env:DB_HOST = "localhost"
$env:DB_PORT = "5432"
$env:DB_NAME = "reconciliation_db"
$env:DB_USER = "reconciliation_user"
$env:DB_PASSWORD = "your-local-password"
```

`DB_HOST` defaults to `localhost`, `DB_PORT` to `5432`, and the optional
`DB_CONNECT_TIMEOUT` to 10 seconds. `DB_NAME`, `DB_USER`, and `DB_PASSWORD`
must be provided.

For shared environments, use a PostgreSQL login granted `CONNECT`, `USAGE` on
the application schema, and `SELECT` only on the three analytics tables.
The Python connection also marks its database session read-only.

## Run

Generate analytics for all available data:

```powershell
python -m analytics
```

Apply an inclusive instant-based window and choose another output directory:

```powershell
python -m analytics --from "2026-09-01T00:00:00Z" --to "2026-09-30T23:59:59Z" --output work\september-analytics
```

Boundaries must contain a UTC offset. They are normalized to UTC before being
bound to parameterized SQL. The existing `TIMESTAMP` columns are explicitly
interpreted as UTC when loaded, matching the Phase 6 reporting convention.

## Outputs

The default `analytics/output/` directory receives:

- `analytics_summary.json`
- `transaction_currency_summary.csv`
- `transaction_status_counts.csv`
- `transaction_type_counts.csv`
- `transaction_daily_trends.csv`
- `reconciliation_result_counts.csv`
- `reconciliation_daily_trends.csv`
- `risk_status_counts.csv`
- `risk_severity_counts.csv`
- `risk_rule_counts.csv`
- `risk_daily_trends.csv`
- `risk_multiple_flag_transactions.csv`

Transaction amounts and averages remain exact decimal values and are always
partitioned by currency. Trends use UTC calendar days. Generated files are
ignored by Git; only `analytics/output/.gitkeep` is retained.

## Tests

The analytics transformations and loader behavior do not require a database:

```powershell
python -m unittest discover -s analytics\tests -v
```

The loader tests use connection doubles to verify selected columns,
parameterized filtering, UTC normalization, and empty-result behavior. Running
`python -m analytics` is the end-to-end database connectivity check.

## Design notes

- `config.py` reads and validates environment configuration.
- `database.py` opens a read-only `psycopg` session.
- `loaders.py` performs one bounded, explicit-column query per source dataset.
- The three analysis modules contain pure Pandas transformations.
- `report.py` writes machine-readable CSV and JSON outputs.

Matplotlib, NumPy, an ORM, and a web framework are deliberately absent: the
repository does not require charts, and these additions would not improve the
implemented reporting workflow.

