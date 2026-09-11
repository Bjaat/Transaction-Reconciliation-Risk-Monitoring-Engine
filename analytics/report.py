from __future__ import annotations

from datetime import datetime, timezone
from decimal import Decimal
import json
from pathlib import Path

from analytics.reconciliation_analysis import ReconciliationAnalytics
from analytics.risk_analysis import RiskAnalytics
from analytics.transaction_analysis import TransactionAnalytics


def _json_value(value: object) -> object:
    if isinstance(value, Decimal):
        return str(value)
    raise TypeError(f"Cannot serialize {type(value).__name__}")


def write_reports(
    output_directory: Path,
    transactions: TransactionAnalytics,
    reconciliations: ReconciliationAnalytics,
    risks: RiskAnalytics,
    from_time: datetime | None = None,
    to_time: datetime | None = None,
) -> list[Path]:
    output_directory.mkdir(parents=True, exist_ok=True)
    frames = {
        "transaction_currency_summary.csv": transactions.currency_summary,
        "transaction_status_counts.csv": transactions.status_counts,
        "transaction_type_counts.csv": transactions.type_counts,
        "transaction_daily_trends.csv": transactions.daily_trends,
        "reconciliation_result_counts.csv": reconciliations.result_counts,
        "reconciliation_daily_trends.csv": reconciliations.daily_trends,
        "risk_status_counts.csv": risks.status_counts,
        "risk_severity_counts.csv": risks.severity_counts,
        "risk_rule_counts.csv": risks.rule_counts,
        "risk_daily_trends.csv": risks.daily_trends,
        "risk_multiple_flag_transactions.csv": risks.multiple_flag_transactions,
    }
    written: list[Path] = []
    for filename, frame in frames.items():
        path = output_directory / filename
        frame.to_csv(path, index=False)
        written.append(path)

    summary_path = output_directory / "analytics_summary.json"
    summary = {
        "generated_at": datetime.now(timezone.utc).isoformat(),
        "window": {
            "from": from_time.isoformat() if from_time else None,
            "to": to_time.isoformat() if to_time else None,
        },
        "transactions": transactions.summary,
        "reconciliations": reconciliations.summary,
        "risks": risks.summary,
    }
    summary_path.write_text(
        json.dumps(summary, indent=2, default=_json_value) + "\n", encoding="utf-8"
    )
    written.append(summary_path)
    return written

