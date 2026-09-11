from __future__ import annotations

from contextlib import contextmanager
from collections.abc import Iterator
from typing import Any

from analytics.config import DatabaseConfig


@contextmanager
def open_connection(config: DatabaseConfig) -> Iterator[Any]:
    """Open a read-only PostgreSQL session whose timezone is explicitly UTC."""
    try:
        import psycopg
    except ImportError as exc:
        raise RuntimeError(
            "psycopg is required; install analytics/requirements.txt first"
        ) from exc

    with psycopg.connect(**config.connection_parameters()) as connection:
        connection.read_only = True
        connection.execute("SET TIME ZONE 'UTC'")
        yield connection
