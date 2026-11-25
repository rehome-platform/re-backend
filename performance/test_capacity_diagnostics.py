"""No backend, real credentials or load traffic required."""
# Load Locust's gevent setup before the diagnostics runner imports requests/ssl.
import locust
import csv
import importlib.util
from pathlib import Path
from tempfile import TemporaryDirectory
from unittest import TestCase, main
from unittest.mock import Mock, patch

spec = importlib.util.spec_from_file_location("capacity_diagnostics", Path(__file__).with_name("diagnose_capacity.py"))
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class CapacityDiagnosticsTest(TestCase):
    def test_failed_authentication_never_falls_back_to_public_catalogue(self):
        with patch.object(module.requests, "get", return_value=Mock(status_code=401)) as get:
            with self.assertRaisesRegex(ValueError, "HTTP 401"):
                module.preflight("http://127.0.0.1:8080", "test-token")
        self.assertEqual(get.call_count, 1)
        self.assertEqual(get.call_args.kwargs["headers"]["Authorization"], "Bearer test-token")

    def test_unavailable_process_permissions_are_reported_as_missing(self):
        with patch.object(module.psutil, "net_connections", side_effect=RuntimeError("not visible")):
            self.assertIsNone(module.discover_java(8080))
        self.assertEqual(module.ProcessSampler(None).read(), {})

    def test_loopback_restriction_also_applies_to_diagnostics(self):
        for host in ("http://example.com", "https://localhost", "http://user:password@localhost",
                     "http://localhost/admin", "http://localhost?token=test"):
            with self.assertRaises(ValueError):
                module.validate_host(host)
        self.assertEqual(module.validate_host("http://127.0.0.1:8080"), 8080)

    def test_phase_counters_include_final_flush_without_double_counting(self):
        with TemporaryDirectory() as folder:
            prefix = Path(folder) / "threshold"
            fields = ["Timestamp", "Name", "User Count", "Total Request Count", "Total Failure Count",
                      "Total Average Response Time", "95%"]
            samples = [(0, 0, 0, 0, 0), (89, 1000, 200, 0, 10),
                       (179, 2000, 400, 0, 20), (269, 2200, 600, 100, 30),
                       (270, 3, 610, 105, 30), (310, 3, 620, 105, 30), (331, 3, 630, 105, 30)]
            with Path(str(prefix) + "_stats_history.csv").open("w", newline="") as source:
                writer = csv.DictWriter(source, fieldnames=fields)
                writer.writeheader()
                for t, users, count, fail, average in samples:
                    writer.writerow(dict(zip(fields, [10000+t, "Aggregated", users, count, fail, average, "470"])))
            report = module.summarize(prefix, 2200)
        self.assertEqual(report["peak_users"], 2200)
        self.assertEqual([r["completed_requests"] for r in report["stages"]], [200, 200, 200, 30])
        self.assertEqual([r["failures"] for r in report["stages"]], [0, 0, 100, 5])
        self.assertEqual(report["stages"][2]["failure_pct"], 50)
        self.assertEqual(report["stages"][2]["average_ms"], 50)
        self.assertEqual(report["total_requests"], 630)
        self.assertEqual(report["total_failures"], 105)
        self.assertEqual(report["last_20_seconds"]["failures"], 0)

    def test_pool_report_excludes_idle_after_test_and_other_java_processes(self):
        with TemporaryDirectory() as folder:
            prefix = Path(folder) / "test"
            pool = Path(folder) / "backend.csv"
            with Path(str(prefix) + "_stats_history.csv").open("w", newline="") as dest:
                writer = csv.DictWriter(dest, fieldnames=["Name", "Timestamp"])
                writer.writeheader()
                writer.writerows([{"Name": "Aggregated", "Timestamp": "10000"},
                                  {"Name": "Aggregated", "Timestamp": "10330"}])
            pool.write_text("timestamp_ms,java_pid,active,idle,total,waiting,max\n"
                            "9999000,42,0,10,10,0,10\n"
                            "10185000,99,10,0,10,200,10\n"
                            "10185000,42,10,0,10,120,10\n"
                            "10190000,42,10,0,10,130,10\n"
                            "10300000,42,0,10,10,0,10\n"
                            "10400000,42,0,10,10,0,10\n", encoding="utf-8")
            report = module.collect_pool_report(pool, prefix, 2500, java_pid=42)
            with Path(str(prefix) + "_pool.csv").open(newline="") as source:
                saved = list(csv.DictReader(source))
        self.assertTrue(report["available"])
        self.assertEqual(report["samples"], 3)
        self.assertEqual(report["stages"][2]["waiting_max"], 130)
        self.assertEqual(report["stages"][2]["samples_with_waiters"], 2)
        self.assertIsNone(report["stages"][0]["waiting_max"])
        self.assertEqual(len(saved), 3)
        self.assertTrue(all(row["java_pid"] == "42" for row in saved))

    def test_missing_pool_csv_is_not_reported_as_zero_waiters(self):
        with TemporaryDirectory() as folder:
            prefix = Path(folder) / "test"
            Path(str(prefix) + "_stats_history.csv").write_text(
                "Name,Timestamp\nAggregated,10000\nAggregated,10330\n", encoding="utf-8")
            report = module.collect_pool_report(Path(folder) / "missing.csv", prefix, 2500)
        self.assertFalse(report["available"])


if __name__ == "__main__":
    main()
