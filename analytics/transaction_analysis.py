from __future__ import annotations

from dataclasses import dataclass
from decimal import Decimal

import pandas as pd

from analytics.frame_utils import (
    decimal_value,
    grouped_counts,
    require_columns,
    utc_day_strings,
    utc_timestamps,
)


REQUIRED_COLUMNS = {
    "transaction_reference", "amount", "currency", "transaction_type",
    "status", "transaction_timestamp",
}


@dataclass(frozen=True)
class TransactionAnalytics:
    summary: dict[str, object]
    currency_summary: pd.DataFrame
    status_counts: pd.DataFrame
    type_counts: pd.DataFrame
    daily_trends: pd.DataFrame


def analyze_transactions(frame: pd.DataFrame) -> TransactionAnalytics:
    require_columns(frame, REQUIRED_COLUMNS)
    data = utc_timestamps(frame, "transaction_timestamp")
    data["amount"] = data["amount"].map(decimal_value)

    currency_rows: list[dict[str, object]] = []
    for currency, group in data.groupby("currency", sort=True):
        total = sum(group["amount"], Decimal("0"))
        count = len(group)
        currency_rows.append({
            "currency": currency,
            "transaction_count": count,
            "total_amount": total,
            "average_amount": total / Decimal(count),
        })
    currency_summary = pd.DataFrame(
        currency_rows,
        columns=["currency", "transaction_count", "total_amount", "average_amount"],
    )

    status_counts = grouped_counts(data, "status", "status")
    type_counts = grouped_counts(data, "transaction_type", "transaction_type")

    if data.empty:
        daily_trends = pd.DataFrame(
            columns=["utc_day", "currency", "transaction_count", "total_amount"]
        )
    else:
        daily = data.assign(utc_day=utc_day_strings(data["transaction_timestamp"]))
        daily_rows: list[dict[str, object]] = []
        for (day, currency), group in daily.groupby(["utc_day", "currency"], sort=True):
            daily_rows.append({
                "utc_day": day,
                "currency": currency,
                "transaction_count": len(group),
                "total_amount": sum(group["amount"], Decimal("0")),
            })
        daily_trends = pd.DataFrame(daily_rows)

    return TransactionAnalytics(
        summary={
            "transaction_count": len(data),
            "currency_count": int(data["currency"].nunique()) if not data.empty else 0,
        },
        currency_summary=currency_summary,
        status_counts=status_counts,
        type_counts=type_counts,
        daily_trends=daily_trends,
    )

