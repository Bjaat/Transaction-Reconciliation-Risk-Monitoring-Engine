from __future__ import annotations

from dataclasses import dataclass

import pandas as pd

from analytics.frame_utils import grouped_counts, require_columns, utc_day_strings, utc_timestamps


REQUIRED_COLUMNS = {
    "transaction_reference", "rule_code", "severity", "status", "detected_at",
}
HIGH_SEVERITIES = {"HIGH", "CRITICAL"}


@dataclass(frozen=True)
class RiskAnalytics:
    summary: dict[str, object]
    status_counts: pd.DataFrame
    severity_counts: pd.DataFrame
    rule_counts: pd.DataFrame
    daily_trends: pd.DataFrame
    multiple_flag_transactions: pd.DataFrame


def analyze_risk_flags(frame: pd.DataFrame) -> RiskAnalytics:
    require_columns(frame, REQUIRED_COLUMNS)
    data = utc_timestamps(frame, "detected_at")
    status_counts = grouped_counts(data, "status", "status")
    severity_counts = grouped_counts(data, "severity", "severity")
    rule_counts = grouped_counts(data, "rule_code", "rule_code")

    if data.empty:
        daily_trends = pd.DataFrame(columns=["utc_day", "severity", "count"])
        multiple_flags = pd.DataFrame(columns=["transaction_reference", "flag_count"])
    else:
        daily = data.assign(utc_day=utc_day_strings(data["detected_at"]))
        daily_trends = (
            daily.groupby(["utc_day", "severity"]).size().rename("count")
            .reset_index().sort_values(["utc_day", "severity"]).reset_index(drop=True)
        )
        multiple_flags = (
            data.groupby("transaction_reference").size().rename("flag_count").reset_index()
        )
        multiple_flags = multiple_flags[multiple_flags["flag_count"] > 1].sort_values(
            ["flag_count", "transaction_reference"], ascending=[False, True]
        ).reset_index(drop=True)

    return RiskAnalytics(
        summary={
            "risk_flag_count": len(data),
            "open_flag_count": int((data["status"] == "OPEN").sum()) if not data.empty else 0,
            "high_severity_flag_count": int(data["severity"].isin(HIGH_SEVERITIES).sum()) if not data.empty else 0,
            "transactions_with_multiple_flags": len(multiple_flags),
        },
        status_counts=status_counts,
        severity_counts=severity_counts,
        rule_counts=rule_counts,
        daily_trends=daily_trends,
        multiple_flag_transactions=multiple_flags,
    )

