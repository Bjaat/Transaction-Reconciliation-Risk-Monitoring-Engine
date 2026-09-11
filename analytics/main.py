from __future__ import annotations

import argparse
from pathlib import Path
import sys

from analytics.config import ConfigurationError, DatabaseConfig, parse_utc_boundary
from analytics.database import open_connection
from analytics.loaders import load_reconciliations, load_risk_flags, load_transactions
from analytics.reconciliation_analysis import analyze_reconciliations
from analytics.report import write_reports
from analytics.risk_analysis import analyze_risk_flags
from analytics.transaction_analysis import analyze_transactions


def _arguments(arguments: list[str] | None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Generate read-only reconciliation analytics")
    parser.add_argument("--from", dest="from_value", help="inclusive ISO-8601 boundary with offset")
    parser.add_argument("--to", dest="to_value", help="inclusive ISO-8601 boundary with offset")
    parser.add_argument(
        "--output",
        type=Path,
        default=Path(__file__).resolve().parent / "output",
        help="report output directory (default: analytics/output)",
    )
    return parser.parse_args(arguments)


def run(arguments: list[str] | None = None) -> list[Path]:
    args = _arguments(arguments)
    from_time = parse_utc_boundary(args.from_value)
    to_time = parse_utc_boundary(args.to_value)
    if from_time and to_time and from_time > to_time:
        raise ConfigurationError("--from must be before or equal to --to")

    config = DatabaseConfig.from_environment()
    with open_connection(config) as connection:
        transaction_frame = load_transactions(connection, from_time, to_time)
        reconciliation_frame = load_reconciliations(connection, from_time, to_time)
        risk_frame = load_risk_flags(connection, from_time, to_time)

    return write_reports(
        args.output,
        analyze_transactions(transaction_frame),
        analyze_reconciliations(reconciliation_frame),
        analyze_risk_flags(risk_frame),
        from_time,
        to_time,
    )


def main() -> int:
    try:
        paths = run()
    except (ConfigurationError, RuntimeError, OSError) as exc:
        print(f"Analytics failed: {exc}", file=sys.stderr)
        return 1
    print(f"Generated {len(paths)} analytics outputs:")
    for path in paths:
        print(path)
    return 0

