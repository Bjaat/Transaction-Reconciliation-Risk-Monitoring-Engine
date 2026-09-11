import unittest

import pandas as pd

from analytics.loaders import RECONCILIATION_COLUMNS
from analytics.reconciliation_analysis import analyze_reconciliations


def reconciliations() -> pd.DataFrame:
    return pd.DataFrame([
        ["TX-1", "SET-1", "MATCHED", None, None, None, "USD", "USD", "2026-09-01T23:30:00-01:00"],
        ["TX-2", "SET-2", "MATCHED", None, None, None, "USD", "USD", "2026-09-02T00:30:00Z"],
        ["TX-3", "SET-3", "AMOUNT_MISMATCH", None, None, None, "USD", "USD", "2026-09-02T01:00:00Z"],
        ["TX-4", None, "MISSING_SETTLEMENT", None, None, None, "EUR", None, "2026-09-03T01:00:00Z"],
    ], columns=RECONCILIATION_COLUMNS)


class ReconciliationAnalysisTest(unittest.TestCase):
    def test_outcomes_and_rates(self) -> None:
        result = analyze_reconciliations(reconciliations())
        self.assertEqual(4, result.summary["reconciliation_count"])
        self.assertEqual(2, result.summary["matched_count"])
        self.assertEqual("50.00", str(result.summary["reconciliation_rate_percent"]))
        self.assertEqual("50.00", str(result.summary["mismatch_rate_percent"]))

    def test_result_grouping(self) -> None:
        result = analyze_reconciliations(reconciliations())
        counts = dict(zip(result.result_counts["result"], result.result_counts["count"]))
        self.assertEqual({"AMOUNT_MISMATCH": 1, "MATCHED": 2, "MISSING_SETTLEMENT": 1}, counts)

    def test_trends_use_utc_day(self) -> None:
        result = analyze_reconciliations(reconciliations())
        september_second = result.daily_trends[
            result.daily_trends["utc_day"] == "2026-09-02T00:00:00Z"
        ]
        self.assertEqual(3, int(september_second["count"].sum()))

    def test_empty_input_returns_zero_rates(self) -> None:
        result = analyze_reconciliations(pd.DataFrame(columns=RECONCILIATION_COLUMNS))
        self.assertEqual("0.00", str(result.summary["reconciliation_rate_percent"]))
        self.assertTrue(result.result_counts.empty)
        self.assertTrue(result.daily_trends.empty)

