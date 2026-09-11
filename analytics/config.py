from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime, timezone
import os
from typing import Mapping


class ConfigurationError(ValueError):
    """Raised when required analytics configuration is absent or invalid."""


@dataclass(frozen=True)
class DatabaseConfig:
    host: str
    port: int
    dbname: str
    user: str
    password: str
    connect_timeout: int = 10

    @classmethod
    def from_environment(cls, environment: Mapping[str, str] | None = None) -> "DatabaseConfig":
        values = os.environ if environment is None else environment
        required = ("DB_NAME", "DB_USER", "DB_PASSWORD")
        missing = [name for name in required if not values.get(name)]
        if missing:
            raise ConfigurationError(
                "Missing required database environment variable(s): " + ", ".join(missing)
            )

        try:
            port = int(values.get("DB_PORT", "5432"))
            timeout = int(values.get("DB_CONNECT_TIMEOUT", "10"))
        except ValueError as exc:
            raise ConfigurationError("DB_PORT and DB_CONNECT_TIMEOUT must be integers") from exc

        if not 1 <= port <= 65535:
            raise ConfigurationError("DB_PORT must be between 1 and 65535")
        if timeout < 1:
            raise ConfigurationError("DB_CONNECT_TIMEOUT must be positive")

        return cls(
            host=values.get("DB_HOST", "localhost"),
            port=port,
            dbname=values["DB_NAME"],
            user=values["DB_USER"],
            password=values["DB_PASSWORD"],
            connect_timeout=timeout,
        )

    def connection_parameters(self) -> dict[str, str | int]:
        return {
            "host": self.host,
            "port": self.port,
            "dbname": self.dbname,
            "user": self.user,
            "password": self.password,
            "connect_timeout": self.connect_timeout,
        }


def parse_utc_boundary(value: str | None) -> datetime | None:
    """Parse an ISO-8601 boundary and normalize the represented instant to UTC."""
    if value is None:
        return None
    try:
        parsed = datetime.fromisoformat(value.replace("Z", "+00:00"))
    except ValueError as exc:
        raise ConfigurationError(f"Invalid ISO-8601 timestamp: {value}") from exc
    if parsed.tzinfo is None or parsed.utcoffset() is None:
        raise ConfigurationError("Time boundaries must include a UTC offset")
    return parsed.astimezone(timezone.utc)

