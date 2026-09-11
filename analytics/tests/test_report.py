from decimal import Decimal
import json
from pathlib import Path
from tempfile import TemporaryDirectory
import unittest

import pandas as pd

from analytics.loaders import RECONCILIATION_COLUMNS, RISK_COLUMNS, TRANSACTION_COLUMNS
from analytics.reconciliation_analysis import analyze_reconciliations
from analytics.report import write_reports
from analytics.risk_analysis import analyze_risk_flags
from analytics.transaction_analysis import analyze_transactions


class ReportTest(unittest.TestCase):
    def test_writes_all_csv_and_json_outputs(self) -> None:
        transaction_frame = pd.DataFrame([
            ["TX-1", Decimal("10.2500"), "USD", "PAYMENT", "COMPLETED", "2026-09-01T00:00:00Z"]
        ], columns=TRANSACTION_COLUMNS)
        reconciliation_frame = pd.DataFrame(columns=RECONCILIATION_COLUMNS)
        risk_frame = pd.DataFrame(columns=RISK_COLUMNS)

        with TemporaryDirectory() as directory:
            paths = write_reports(
                Path(directory),
                analyze_transactions(transaction_frame),
                analyze_reconciliations(reconciliation_frame),
                analyze_risk_flags(risk_frame),
            )
            self.assertEqual(12, len(paths))
            self.assertTrue(all(path.exists() for path in paths))
            summary = json.loads((Path(directory) / "analytics_summary.json").read_text())
            self.assertEqual(1, summary["transactions"]["transaction_count"])

    def test_csv_preserves_decimal_text(self) -> None:
        frame = pd.DataFrame([
            ["TX-1", Decimal("10.2500"), "USD", "PAYMENT", "COMPLETED", "2026-09-01T00:00:00Z"]
        ], columns=TRANSACTION_COLUMNS)
        with TemporaryDirectory() as directory:
            write_reports(
                Path(directory), analyze_transactions(frame),
                analyze_reconciliations(pd.DataFrame(columns=RECONCILIATION_COLUMNS)),
                analyze_risk_flags(pd.DataFrame(columns=RISK_COLUMNS)),
            )
            text = (Path(directory) / "transaction_currency_summary.csv").read_text()
            self.assertIn("10.2500", text)

