import unittest

import pandas as pd

from analytics.loaders import RISK_COLUMNS
from analytics.risk_analysis import analyze_risk_flags


def flags() -> pd.DataFrame:
    return pd.DataFrame([
        ["TX-1", "LARGE_AMOUNT", "HIGH", "OPEN", "2026-09-01T23:30:00-01:00"],
        ["TX-1", "DUPLICATE_TRANSACTION", "CRITICAL", "RESOLVED", "2026-09-02T00:30:00Z"],
        ["TX-2", "LARGE_AMOUNT", "HIGH", "OPEN", "2026-09-03T00:00:00Z"],
        ["TX-3", "MANUAL_REVIEW", "LOW", "DISMISSED", "2026-09-03T03:00:00Z"],
    ], columns=RISK_COLUMNS)


class RiskAnalysisTest(unittest.TestCase):
    def test_status_severity_and_rule_counts(self) -> None:
        result = analyze_risk_flags(flags())
        self.assertEqual({"DISMISSED": 1, "OPEN": 2, "RESOLVED": 1}, dict(
            zip(result.status_counts["status"], result.status_counts["count"])
        ))
        self.assertEqual({"CRITICAL": 1, "HIGH": 2, "LOW": 1}, dict(
            zip(result.severity_counts["severity"], result.severity_counts["count"])
        ))
        self.assertEqual(3, len(result.rule_counts))

    def test_high_severity_and_open_summary(self) -> None:
        result = analyze_risk_flags(flags())
        self.assertEqual(2, result.summary["open_flag_count"])
        self.assertEqual(3, result.summary["high_severity_flag_count"])

    def test_multiple_flags_by_transaction(self) -> None:
        result = analyze_risk_flags(flags())
        self.assertEqual([{"transaction_reference": "TX-1", "flag_count": 2}],
                         result.multiple_flag_transactions.to_dict("records"))

    def test_daily_trends_use_utc(self) -> None:
        result = analyze_risk_flags(flags())
        september_second = result.daily_trends[
            result.daily_trends["utc_day"] == "2026-09-02T00:00:00Z"
        ]
        self.assertEqual(2, int(september_second["count"].sum()))

    def test_empty_input_returns_stable_frames(self) -> None:
        result = analyze_risk_flags(pd.DataFrame(columns=RISK_COLUMNS))
        self.assertEqual(0, result.summary["risk_flag_count"])
        self.assertTrue(result.multiple_flag_transactions.empty)
        self.assertEqual(["transaction_reference", "flag_count"],
                         result.multiple_flag_transactions.columns.tolist())

