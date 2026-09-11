from datetime import datetime, timezone
from types import SimpleNamespace
import unittest

from analytics.loaders import (
    RECONCILIATION_COLUMNS,
    RISK_COLUMNS,
    TRANSACTION_COLUMNS,
    load_reconciliations,
    load_risk_flags,
    load_transactions,
)


class CursorStub:
    def __init__(self, columns: list[str], rows: list[tuple] | None = None) -> None:
        self.description = [SimpleNamespace(name=name) for name in columns]
        self.rows = [] if rows is None else rows
        self.sql = ""
        self.parameters = []

    def __enter__(self) -> "CursorStub":
        return self

    def __exit__(self, *_: object) -> None:
        return None

    def execute(self, sql: str, parameters: list[datetime]) -> None:
        self.sql = sql
        self.parameters = parameters

    def fetchall(self) -> list[tuple]:
        return self.rows


class ConnectionStub:
    def __init__(self, cursor: CursorStub) -> None:
        self.stub = cursor

    def cursor(self) -> CursorStub:
        return self.stub


class LoaderTest(unittest.TestCase):
    def test_transaction_loader_selects_only_required_columns(self) -> None:
        cursor = CursorStub(TRANSACTION_COLUMNS)
        frame = load_transactions(ConnectionStub(cursor))
        self.assertEqual(TRANSACTION_COLUMNS, frame.columns.tolist())
        self.assertNotIn("SELECT *", cursor.sql.upper())
        self.assertIn("AT TIME ZONE 'UTC'", cursor.sql)

    def test_boundaries_are_parameterized_and_normalized_to_naive_utc(self) -> None:
        cursor = CursorStub(TRANSACTION_COLUMNS)
        boundary = datetime.fromisoformat("2026-09-01T05:30:00+05:30")
        load_transactions(ConnectionStub(cursor), boundary, boundary)
        self.assertEqual(2, cursor.sql.count("%s"))
        self.assertNotIn("2026-09-01", cursor.sql)
        self.assertEqual(datetime(2026, 9, 1, 0, 0), cursor.parameters[0])
        self.assertIsNone(cursor.parameters[0].tzinfo)

    def test_reconciliation_loader_uses_indexed_event_timestamp(self) -> None:
        cursor = CursorStub(RECONCILIATION_COLUMNS)
        load_reconciliations(ConnectionStub(cursor), datetime(2026, 9, 1, tzinfo=timezone.utc))
        self.assertIn("reconciled_at >= %s", cursor.sql)

    def test_risk_loader_joins_for_business_reference(self) -> None:
        cursor = CursorStub(RISK_COLUMNS)
        load_risk_flags(ConnectionStub(cursor))
        self.assertIn("JOIN transactions", cursor.sql)
        self.assertIn("t.transaction_reference", cursor.sql)

    def test_naive_boundary_is_rejected(self) -> None:
        cursor = CursorStub(TRANSACTION_COLUMNS)
        with self.assertRaisesRegex(ValueError, "timezone-aware"):
            load_transactions(ConnectionStub(cursor), datetime(2026, 9, 1))

    def test_unexpected_schema_is_rejected(self) -> None:
        cursor = CursorStub(["wrong"])
        with self.assertRaisesRegex(RuntimeError, "Unexpected query columns"):
            load_transactions(ConnectionStub(cursor))

