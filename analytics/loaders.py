from __future__ import annotations

from datetime import datetime, timezone
from typing import Any

import pandas as pd


TRANSACTION_COLUMNS = [
    "transaction_reference", "amount", "currency", "transaction_type",
    "status", "transaction_timestamp",
]
RECONCILIATION_COLUMNS = [
    "transaction_reference", "settlement_reference", "result",
    "expected_amount", "actual_amount", "amount_difference",
    "expected_currency", "actual_currency", "reconciled_at",
]
RISK_COLUMNS = [
    "transaction_reference", "rule_code", "severity", "status", "detected_at",
]


def _database_boundary(value: datetime) -> datetime:
    if value.tzinfo is None or value.utcoffset() is None:
        raise ValueError("Database boundaries must be timezone-aware")
    return value.astimezone(timezone.utc).replace(tzinfo=None)


def _window(column: str, from_time: datetime | None, to_time: datetime | None) -> tuple[str, list[datetime]]:
    clauses: list[str] = []
    parameters: list[datetime] = []
    if from_time is not None:
        clauses.append(f"{column} >= %s")
        parameters.append(_database_boundary(from_time))
    if to_time is not None:
        clauses.append(f"{column} <= %s")
        parameters.append(_database_boundary(to_time))
    return (" WHERE " + " AND ".join(clauses) if clauses else "", parameters)


def _frame(connection: Any, sql: str, parameters: list[datetime], expected_columns: list[str]) -> pd.DataFrame:
    with connection.cursor() as cursor:
        cursor.execute(sql, parameters)
        rows = cursor.fetchall()
        actual_columns = [description.name for description in cursor.description]
    if actual_columns != expected_columns:
        raise RuntimeError(
            f"Unexpected query columns: expected {expected_columns}, received {actual_columns}"
        )
    return pd.DataFrame(rows, columns=actual_columns)


def load_transactions(connection: Any, from_time: datetime | None = None,
                      to_time: datetime | None = None) -> pd.DataFrame:
    where, parameters = _window("transaction_timestamp", from_time, to_time)
    sql = """
        SELECT transaction_reference, amount, currency, transaction_type, status,
               transaction_timestamp AT TIME ZONE 'UTC' AS transaction_timestamp
        FROM transactions
    """ + where + " ORDER BY transaction_timestamp, transaction_reference"
    return _frame(connection, sql, parameters, TRANSACTION_COLUMNS)


def load_reconciliations(connection: Any, from_time: datetime | None = None,
                         to_time: datetime | None = None) -> pd.DataFrame:
    where, parameters = _window("reconciled_at", from_time, to_time)
    sql = """
        SELECT transaction_reference, settlement_reference, result,
               expected_amount, actual_amount, amount_difference,
               expected_currency, actual_currency,
               reconciled_at AT TIME ZONE 'UTC' AS reconciled_at
        FROM reconciliation_logs
    """ + where + " ORDER BY reconciled_at, id"
    return _frame(connection, sql, parameters, RECONCILIATION_COLUMNS)


def load_risk_flags(connection: Any, from_time: datetime | None = None,
                    to_time: datetime | None = None) -> pd.DataFrame:
    where, parameters = _window("f.detected_at", from_time, to_time)
    sql = """
        SELECT t.transaction_reference, f.rule_code, f.severity, f.status,
               f.detected_at AT TIME ZONE 'UTC' AS detected_at
        FROM risk_flags f
        JOIN transactions t ON t.id = f.transaction_id
    """ + where + " ORDER BY f.detected_at, f.id"
    return _frame(connection, sql, parameters, RISK_COLUMNS)

