from decimal import Decimal
import unittest

import pandas as pd

from analytics.loaders import TRANSACTION_COLUMNS
from analytics.transaction_analysis import analyze_transactions


def transactions() -> pd.DataFrame:
    return pd.DataFrame([
        ["USD-1", Decimal("100.0000"), "USD", "PAYMENT", "COMPLETED", "2026-09-01T23:30:00-01:00"],
        ["USD-2", Decimal("200.0000"), "USD", "TRANSFER", "PROCESSING", "2026-09-02T00:30:00Z"],
        ["EUR-1", Decimal("100.0000"), "EUR", "PAYMENT", "COMPLETED", "2026-09-02T04:00:00+05:30"],
        ["EUR-2", Decimal("200.0000"), "EUR", "TRANSFER", "PROCESSING", "2026-09-02T05:30:00+05:30"],
    ], columns=TRANSACTION_COLUMNS)


class TransactionAnalysisTest(unittest.TestCase):
    def test_totals_and_averages_remain_currency_separated_and_exact(self) -> None:
        result = analyze_transactions(transactions())
        by_currency = result.currency_summary.set_index("currency")
        self.assertEqual(Decimal("300.0000"), by_currency.loc["USD", "total_amount"])
        self.assertEqual(Decimal("150.0000"), by_currency.loc["USD", "average_amount"])
        self.assertEqual(Decimal("300.0000"), by_currency.loc["EUR", "total_amount"])
        self.assertEqual(2, result.summary["currency_count"])

    def test_status_and_type_counts(self) -> None:
        result = analyze_transactions(transactions())
        self.assertEqual(
            {"COMPLETED": 2, "PROCESSING": 2},
            dict(zip(result.status_counts["status"], result.status_counts["count"])),
        )
        self.assertEqual(
            {"PAYMENT": 2, "TRANSFER": 2},
            dict(zip(result.type_counts["transaction_type"], result.type_counts["count"])),
        )

    def test_daily_trends_group_by_utc_day_and_currency(self) -> None:
        result = analyze_transactions(transactions())
        usd = result.daily_trends[result.daily_trends["currency"] == "USD"]
        self.assertEqual(["2026-09-02T00:00:00Z"], usd["utc_day"].unique().tolist())
        self.assertEqual(Decimal("300.0000"), sum(usd["total_amount"], Decimal("0")))

    def test_empty_input_has_stable_output_columns(self) -> None:
        result = analyze_transactions(pd.DataFrame(columns=TRANSACTION_COLUMNS))
        self.assertEqual(0, result.summary["transaction_count"])
        self.assertTrue(result.currency_summary.empty)
        self.assertEqual(
            ["utc_day", "currency", "transaction_count", "total_amount"],
            result.daily_trends.columns.tolist(),
        )

    def test_missing_columns_fail_clearly(self) -> None:
        with self.assertRaisesRegex(ValueError, "currency"):
            analyze_transactions(pd.DataFrame({"amount": [Decimal("1")]}))

