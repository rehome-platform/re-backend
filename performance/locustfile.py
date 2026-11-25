"""Bounded read-only catalogue test. Only loopback targets are accepted."""
import os
from urllib.parse import urlsplit

from locust import HttpUser, LoadTestShape, between, events, task


@events.init_command_line_parser.add_listener
def load_test_options(parser):
    parser.add_argument("--load-profile", choices=["smoke", "stress", "capacity"], default="smoke",
                        help="Smoke, staged stress, or high-capacity test with recovery")
    parser.add_argument("--max-users", type=int, default=20,
                        help="Target user ceiling for the shape, between 1 and 10000")


@events.init.add_listener
def validate_target(environment, **kwargs):
    target = urlsplit(environment.host or "")
    options = environment.parsed_options
    if (target.scheme != "http" or target.hostname not in {"localhost", "127.0.0.1", "::1"}
            or target.username or target.password or target.path not in {"", "/"}
            or target.query or target.fragment):
        raise SystemExit("Use an explicit http://localhost:PORT test backend")
    maximum = getattr(options, "max_users", 20)
    if not 1 <= maximum <= 10000:
        raise SystemExit("--max-users must be between 1 and 10000")
    if getattr(options, "num_users", None) and options.num_users > maximum:
        raise SystemExit("Use --max-users for this load shape instead of -u/--users")



class CatalogueUser(HttpUser):
    wait_time = between(0.5, 1.5)
    connection_timeout = 2
    network_timeout = 5

    def on_start(self):
        token = os.environ.get("LOAD_TEST_TOKEN")
        if token:
            self.client.headers["Authorization"] = "Bearer " + token

    def catalogue(self, params, name):
        with self.client.get("/products", params=params, name=name,
                             timeout=(2, 5), catch_response=True) as response:
            if response.status_code == 0:
                # Locust uses 0 when a request exception prevented an HTTP response.
                # Preserve the original exception instead of masking all transport errors as HTTP 0.
                response.failure(getattr(response, "error", None)
                                 or RuntimeError("Transport error: no HTTP response"))
                return
            if response.status_code != 200:
                response.failure("HTTP " + str(response.status_code))
                return
            try:
                payload = response.json()
                result = payload["result"]
                if payload.get("isSuccess") is not True or not isinstance(result["content"], list):
                    response.failure("Unexpected API payload")
            except (ValueError, KeyError, TypeError):
                response.failure("Invalid catalogue JSON")

    @task(5)
    def warm_catalogue(self):
        self.catalogue({"page": 0, "size": 20}, "GET /products [repeat/cache]")

    @task(2)
    def search(self):
        self.catalogue({"search": "ao", "page": 0, "size": 20}, "GET /products [search]")

    @task(1)
    def pagination(self):
        self.catalogue({"page": 1, "size": 20}, "GET /products [page 1]")


class BoundedSpike(LoadTestShape):
    """Default: 45 seconds. Stress: 90 seconds per level, then 60 seconds recovery."""
    def tick(self):
        elapsed = self.get_run_time()
        options = self.runner.environment.parsed_options
        maximum = getattr(options, "max_users", 20)
        profile = getattr(options, "load_profile", "smoke")
        if profile in {"stress", "capacity"}:
            stages = ((1000, 2000, 5000, 10000) if profile == "capacity"
                      else (20, 50, 100, 200, 500, 1000, 2000, 5000, 10000))
            levels = [users for users in stages if users <= maximum]
            if not levels or levels[-1] != maximum:
                levels.append(maximum)
            stage = int(elapsed // 90)
            if stage < len(levels):
                users = levels[stage]
                return users, 200 if profile == "capacity" or users > 1000 else 20
            if elapsed < len(levels) * 90 + 60:
                return min(3, maximum), max(100, (maximum + 9) // 10)
            return None
        if elapsed < 15:
            return min(3, maximum), 3
        if elapsed < 25:
            return min(20, maximum), 20
        if elapsed < 45:
            return min(3, maximum), 20
        return None
