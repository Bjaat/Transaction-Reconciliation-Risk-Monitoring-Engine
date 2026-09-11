from __future__ import annotations

from dataclasses import dataclass
from decimal import Decimal, ROUND_HALF_UP

import pandas as pd

from analytics.frame_utils import grouped_counts, require_columns, utc_day_strings, utc_timestamps


REQUIRED_COLUMNS = {"transaction_reference", "result", "reconciled_at"}


@dataclass(frozen=True)
class ReconciliationAnalytics:
    summary: dict[str, object]
    result_counts: pd.DataFrame
    daily_trends: pd.DataFrame


def _percentage(part: int, total: int) -> Decimal:
    if total == 0:
        return Decimal("0.00")
    return (Decimal(part) * Decimal("100") / Decimal(total)).quantize(
        Decimal("0.01"), rounding=ROUND_HALF_UP
    )


def analyze_reconciliations(frame: pd.DataFrame) -> ReconciliationAnalytics:
    require_columns(frame, REQUIRED_COLUMNS)
    data = utc_timestamps(frame, "reconciled_at")
    result_counts = grouped_counts(data, "result", "result")
    total = len(data)
    matched = int((data["result"] == "MATCHED").sum()) if total else 0

    if data.empty:
        daily_trends = pd.DataFrame(columns=["utc_day", "result", "count"])
    else:
        daily = data.assign(utc_day=utc_day_strings(data["reconciled_at"]))
        daily_trends = (
            daily.groupby(["utc_day", "result"]).size().rename("count")
            .reset_index().sort_values(["utc_day", "result"]).reset_index(drop=True)
        )

    return ReconciliationAnalytics(
        summary={
            "reconciliation_count": total,
            "matched_count": matched,
            "mismatch_count": total - matched,
            "reconciliation_rate_percent": _percentage(matched, total),
            "mismatch_rate_percent": _percentage(total - matched, total),
        },
        result_counts=result_counts,
        daily_trends=daily_trends,
    )

