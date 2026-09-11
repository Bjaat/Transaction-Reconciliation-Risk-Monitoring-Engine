from __future__ import annotations

from decimal import Decimal

import pandas as pd


def require_columns(frame: pd.DataFrame, columns: set[str]) -> None:
    missing = sorted(columns.difference(frame.columns))
    if missing:
        raise ValueError("Missing required DataFrame column(s): " + ", ".join(missing))


def utc_timestamps(frame: pd.DataFrame, column: str) -> pd.DataFrame:
    result = frame.copy()
    result[column] = pd.to_datetime(result[column], utc=True, errors="raise")
    return result


def decimal_value(value: object) -> Decimal:
    if pd.isna(value):
        raise ValueError("Monetary values cannot be null")
    return value if isinstance(value, Decimal) else Decimal(str(value))


def grouped_counts(frame: pd.DataFrame, column: str, label: str) -> pd.DataFrame:
    if frame.empty:
        return pd.DataFrame(columns=[label, "count"])
    return (
        frame.groupby(column, dropna=False).size()
        .rename("count").reset_index().rename(columns={column: label})
        .sort_values(label, kind="stable").reset_index(drop=True)
    )


def utc_day_strings(series: pd.Series) -> pd.Series:
    return pd.to_datetime(series, utc=True, errors="raise").dt.strftime("%Y-%m-%dT00:00:00Z")

