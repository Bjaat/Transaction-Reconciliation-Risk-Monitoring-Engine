from datetime import timezone
import unittest

from analytics.config import ConfigurationError, DatabaseConfig, parse_utc_boundary


class DatabaseConfigTest(unittest.TestCase):
    def test_reads_required_values_and_defaults(self) -> None:
        config = DatabaseConfig.from_environment({
            "DB_NAME": "reconciliation_db",
            "DB_USER": "analytics_reader",
            "DB_PASSWORD": "secret",
        })
        self.assertEqual("localhost", config.host)
        self.assertEqual(5432, config.port)
        self.assertEqual(10, config.connect_timeout)

    def test_rejects_missing_credentials(self) -> None:
        with self.assertRaisesRegex(ConfigurationError, "DB_PASSWORD"):
            DatabaseConfig.from_environment({"DB_NAME": "db", "DB_USER": "user"})

    def test_rejects_invalid_port(self) -> None:
        with self.assertRaisesRegex(ConfigurationError, "between 1 and 65535"):
            DatabaseConfig.from_environment({
                "DB_NAME": "db", "DB_USER": "user", "DB_PASSWORD": "pw", "DB_PORT": "70000",
            })

    def test_connection_parameters_do_not_build_a_dsn_string(self) -> None:
        config = DatabaseConfig("db", 5432, "name", "user", "pw")
        self.assertEqual("pw", config.connection_parameters()["password"])


class TimestampConfigurationTest(unittest.TestCase):
    def test_equivalent_offsets_normalize_to_same_utc_instant(self) -> None:
        local = parse_utc_boundary("2026-09-01T05:30:00+05:30")
        utc = parse_utc_boundary("2026-09-01T00:00:00Z")
        self.assertEqual(utc, local)
        self.assertEqual(timezone.utc, local.tzinfo)

    def test_different_instants_remain_different(self) -> None:
        local = parse_utc_boundary("2026-09-01T00:00:00+05:30")
        utc = parse_utc_boundary("2026-09-01T00:00:00Z")
        self.assertNotEqual(utc, local)

    def test_rejects_naive_boundary(self) -> None:
        with self.assertRaisesRegex(ConfigurationError, "include a UTC offset"):
            parse_utc_boundary("2026-09-01T00:00:00")

