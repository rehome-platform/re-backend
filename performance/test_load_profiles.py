"""Unit checks only: no HTTP traffic and no backend required."""
import importlib.util
from pathlib import Path
from types import SimpleNamespace
import unittest

spec = importlib.util.spec_from_file_location("catalogue_load_test", Path(__file__).with_name("locustfile.py"))
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class LoadProfilesTest(unittest.TestCase):
    def shape(self, profile, maximum, elapsed):
        options = SimpleNamespace(load_profile=profile, max_users=maximum)
        shape = module.BoundedSpike()
        shape.runner = SimpleNamespace(environment=SimpleNamespace(parsed_options=options))
        shape.get_run_time = lambda: elapsed
        return shape.tick()

    def test_smoke_keeps_original_schedule(self):
        for elapsed, expected in ((0, (3, 3)), (15, (20, 20)), (25, (3, 20)), (45, None)):
            self.assertEqual(self.shape("smoke", 20, elapsed), expected)

    def test_thousand_users_ramp_and_recover_then_stop(self):
        for i, users in enumerate((20, 50, 100, 200, 500, 1000)):
            self.assertEqual(self.shape("stress", 1000, i * 90), (users, 20))
        self.assertEqual(self.shape("stress", 1000, 540), (3, 100))
        self.assertIsNone(self.shape("stress", 1000, 600))

    def test_custom_limit_is_reached_without_exceeding_it(self):
        self.assertEqual(self.shape("stress", 300, 360), (300, 20))
        self.assertEqual(self.shape("stress", 300, 450), (3, 100))
        self.assertIsNone(self.shape("stress", 300, 510))

    def test_remote_hosts_and_invalid_limits_are_rejected(self):
        for host, maximum in (("http://example.com", 1000), ("http://127.0.0.1:8080", 10001),
                              ("http://127.0.0.1:8080", 0)):
            env = SimpleNamespace(host=host, parsed_options=SimpleNamespace(max_users=maximum, num_users=None))
            with self.assertRaises(SystemExit):
                module.validate_target(env)

    def test_ten_thousand_capacity_ramps_and_recovers_within_schedule(self):
        for i, users in enumerate((1000, 2000, 5000, 10000)):
            self.assertEqual(self.shape("capacity", 10000, i * 90), (users, 200))
        self.assertEqual(self.shape("capacity", 10000, 360), (3, 1000))
        self.assertIsNone(self.shape("capacity", 10000, 420))
        env = SimpleNamespace(host="http://127.0.0.1:8080",
                              parsed_options=SimpleNamespace(max_users=10000, num_users=None))
        module.validate_target(env)

    def test_capacity_custom_ceiling_and_extended_stress(self):
        self.assertEqual(self.shape("capacity", 3000, 180), (3000, 200))
        self.assertEqual(self.shape("capacity", 3000, 270), (3, 300))
        self.assertIsNone(self.shape("capacity", 3000, 330))
        self.assertEqual(self.shape("stress", 10000, 720), (10000, 200))
        self.assertEqual(self.shape("stress", 10000, 810), (3, 1000))
        self.assertIsNone(self.shape("stress", 10000, 870))

    def test_request_exception_is_preserved_in_failure_statistics(self):
        from unittest.mock import Mock
        from requests.exceptions import ReadTimeout
        response = Mock(status_code=0, error=ReadTimeout("read timed out"))
        context = Mock()
        context.__enter__ = Mock(return_value=response)
        context.__exit__ = Mock(return_value=False)
        user = Mock()
        user.client.get.return_value = context
        module.CatalogueUser.catalogue(user, {}, "test")
        response.failure.assert_called_once_with(response.error)
        response.json.assert_not_called()


if __name__ == "__main__":
    unittest.main()
