"""Run authenticated loopback tests and sample resources outside the Locust process."""
import argparse
import base64
import csv
import getpass
import json
import os
from pathlib import Path
import subprocess
import sys
import time
from urllib.parse import urlsplit

import psutil
import requests


HERE = Path(__file__).resolve().parent


def validate_host(host):
    target = urlsplit(host)
    if (target.scheme != "http" or target.hostname not in {"localhost", "127.0.0.1", "::1"}
            or target.username or target.password or target.path not in {"", "/"}
            or target.query or target.fragment):
        raise ValueError("Use an explicit HTTP loopback backend without a path or credentials")
    return target.port or 80


def capacity_levels(maximum):
    levels = [n for n in (1000, 2000, 5000, 10000) if n <= maximum]
    if not levels or levels[-1] != maximum:
        levels.append(maximum)
    return levels


def token_remaining(token):
    """Expiry hint only. The backend still validates the JWT during preflight and requests."""
    try:
        segment = token.split(".")[1]
        claims = json.loads(base64.urlsafe_b64decode(segment + "=" * (-len(segment) % 4)))
        return float(claims["exp"]) - time.time()
    except (IndexError, ValueError, KeyError, TypeError):
        return None


def preflight(host, token):
    # Do not retry as anonymous: that would silently remove the authentication workload.
    response = requests.get(host.rstrip("/") + "/products", params={"page": 0, "size": 1},
                            headers={"Authorization": "Bearer " + token}, timeout=(2, 5))
    if response.status_code != 200:
        raise ValueError("Authenticated preflight returned HTTP " + str(response.status_code))
    data = response.json()
    if data.get("isSuccess") is not True or not isinstance((data.get("result") or {}).get("content"), list):
        raise ValueError("Unexpected catalogue response during preflight")


def discover_java(port):
    try:
        for connection in psutil.net_connections(kind="tcp"):
            if (connection.status == psutil.CONN_LISTEN and connection.laddr.port == port
                    and connection.pid):
                process = psutil.Process(connection.pid)
                if process.name().lower().startswith("java"):
                    return process
    except (psutil.Error, OSError, RuntimeError):
        pass
    return None


class ProcessSampler:
    def __init__(self, root, include_children=False):
        self.root = root
        self.include_children = include_children
        self.known = {}
        self.read()

    def read(self):
        if self.root is None:
            return {}
        try:
            processes = [self.root]
            if self.include_children:
                processes += self.root.children(recursive=True)
        except psutil.Error:
            return {}
        cpu = rss = threads = 0
        measured = 0
        for candidate in processes:
            try:
                key = (candidate.pid, candidate.create_time())
                if key not in self.known:
                    self.known[key] = candidate
                    candidate.cpu_percent(None)
                    continue
                process = self.known[key]
                cpu += process.cpu_percent(None)
                rss += process.memory_info().rss
                threads += process.num_threads()
                measured += 1
            except psutil.Error:
                continue
        if not measured:
            return {}
        return {"cpu_pct_one_core_100": round(cpu, 2),
                "cpu_pct_machine": round(cpu / (psutil.cpu_count() or 1), 2),
                "rss_mb": round(rss / 1024 ** 2, 2), "threads": threads}


def aggregate_history(prefix):
    path = Path(str(prefix) + "_stats_history.csv")
    if not path.exists():
        raise ValueError("History not found: " + str(path))
    with path.open(newline="", encoding="utf-8-sig") as source:
        rows = [row for row in csv.DictReader(source) if row.get("Name") == "Aggregated"]
    if not rows:
        raise ValueError("History has no aggregate samples")
    return rows


def read_pool_samples(path, start, end, java_pid=None):
    samples = []
    with Path(path).open(newline="", encoding="utf-8-sig") as source:
        for row in csv.DictReader(source):
            try:
                sample = {name: int(row[name]) for name in
                          ("timestamp_ms", "java_pid", "active", "idle", "total", "waiting", "max")}
            except (ValueError, TypeError, KeyError):
                # The sampler may be appending its final line while this file is read.
                continue
            if (start <= sample["timestamp_ms"] / 1000 <= end
                    and (java_pid is None or sample["java_pid"] == java_pid)):
                samples.append(sample)
    return sorted(samples, key=lambda sample: sample["timestamp_ms"])


def collect_pool_report(path, prefix, maximum, java_pid=None):
    history = aggregate_history(prefix)
    start, end = float(history[0]["Timestamp"]), float(history[-1]["Timestamp"])
    if path is None or not Path(path).exists():
        return {"available": False, "reason": "Backend pool CSV is missing"}
    try:
        samples = read_pool_samples(path, start, end, java_pid)
    except OSError:
        return {"available": False, "reason": "Backend pool CSV could not be read"}
    if not samples:
        return {"available": False, "reason": "No pool samples matched this test's time window and Java PID"}
    with Path(str(prefix) + "_pool.csv").open("w", newline="", encoding="utf-8") as dest:
        writer = csv.DictWriter(dest, fieldnames=list(samples[0]))
        writer.writeheader()
        writer.writerows(samples)
    stages = []
    levels = capacity_levels(maximum)
    for i, label in enumerate([str(n) for n in levels] + ["recovery"]):
        phase = [sample for sample in samples if i * 90 <= sample["timestamp_ms"] / 1000 - start
                 and (i == len(levels) or sample["timestamp_ms"] / 1000 - start < (i + 1) * 90)]
        stages.append({"target_users": label, "samples": len(phase),
                       "active_max": max(sample["active"] for sample in phase) if phase else None,
                       "waiting_max": max(sample["waiting"] for sample in phase) if phase else None,
                       "samples_with_waiters": sum(sample["waiting"] > 0 for sample in phase),
                       "idle_min": min(sample["idle"] for sample in phase) if phase else None})
    gaps = [(b["timestamp_ms"] - a["timestamp_ms"]) / 1000 for a, b in zip(samples, samples[1:])]
    return {"available": True, "samples": len(samples), "stages": stages,
            "max_sample_gap_seconds": max(gaps) if gaps else None,
            "note": "Five-second samples may miss short spikes; zero observed waiters does not exclude every database delay."}


def summarize(prefix, maximum):
    rows = aggregate_history(prefix)
    origin = float(rows[0]["Timestamp"])
    previous_count = previous_failures = 0
    previous_time = origin
    previous_duration = 0.0
    stages = []
    levels = capacity_levels(maximum)
    boundaries = [(str(n), (i + 1) * 90) for i, n in enumerate(levels)]
    boundaries.append(("recovery", len(levels) * 90 + 60))
    for index, (label, end) in enumerate(boundaries):
        # Last recovery interval includes the final writer flush after shape stop.
        candidates = [row for row in rows if previous_time <= float(row["Timestamp"])
                      and (index == len(boundaries) - 1 or float(row["Timestamp"]) - origin < end)]
        if not candidates:
            continue
        last = candidates[-1]
        timestamp = float(last["Timestamp"])
        count = int(last["Total Request Count"])
        failures = int(last["Total Failure Count"])
        requests_in_stage = count - previous_count
        errors_in_stage = failures - previous_failures
        cumulative_duration = float(last["Total Average Response Time"]) * count
        stages.append({"target_users": label, "completed_requests": requests_in_stage,
                       "failures": errors_in_stage,
                       "failure_pct": round(100 * errors_in_stage / requests_in_stage, 2) if requests_in_stage else 0,
                       "average_ms": round((cumulative_duration - previous_duration) / requests_in_stage, 2)
                                     if requests_in_stage else None,
                       "completed_rps": round(requests_in_stage / (timestamp - previous_time), 2)
                                        if timestamp > previous_time else None,
                       "successful_rps": round((requests_in_stage - errors_in_stage) / (timestamp - previous_time), 2)
                                         if timestamp > previous_time else None,
                       "max_observed_users": max(int(row["User Count"]) for row in candidates)})
        previous_count, previous_failures = count, failures
        previous_time, previous_duration = timestamp, cumulative_duration
    first_error = next((row for row in rows if int(row["Total Failure Count"]) > 0), None)
    final = rows[-1]
    early = [row for row in rows if float(row["Timestamp"]) <= float(final["Timestamp"]) - 20]
    recovery = None
    if early:
        before = early[-1]
        recovery = {"completed_requests": int(final["Total Request Count"]) - int(before["Total Request Count"]),
                    "failures": int(final["Total Failure Count"]) - int(before["Total Failure Count"]),
                    "last_window_p95_ms": final.get("95%")}
    return {"peak_users": max(int(row["User Count"]) for row in rows),
            "total_requests": int(final["Total Request Count"]),
            "total_failures": int(final["Total Failure Count"]),
            "first_error_seconds": round(float(first_error["Timestamp"]) - origin, 2) if first_error else None,
            "stages": stages, "last_20_seconds": recovery,
            "note": "Stage boundaries approximate the shape start from the first CSV sample. Counts are completions, including older in-flight requests. Rolling p95 is not a whole-stage percentile."}


def run_one(host, token, maximum, output, java, pool_csv):
    output.parent.mkdir(parents=True, exist_ok=True)
    if Path(str(output) + ".log").exists():
        raise ValueError("Output already exists; choose a new --label")
    command = [sys.executable, "-m", "locust", "-f", str(HERE / "locustfile.py"),
               "--host", host, "--headless", "--load-profile", "capacity", "--max-users", str(maximum),
               "--csv-full-history", "--csv", str(output), "--html", str(output) + ".html"]
    child_env = dict(os.environ, LOAD_TEST_TOKEN=token)
    fields = ["timestamp", "elapsed_seconds", "system_cpu_pct", "system_memory_pct", "available_memory_mb",
              "locust_pid", "java_pid"]
    for name in ("locust", "java"):
        fields.extend(name + "_" + metric for metric in
                      ("cpu_pct_one_core_100", "cpu_pct_machine", "rss_mb", "threads"))
    with Path(str(output) + ".log").open("w", encoding="utf-8") as log_file, \
            Path(str(output) + "_resources.csv").open("w", newline="", encoding="utf-8") as metrics:
        writer = csv.DictWriter(metrics, fieldnames=fields)
        writer.writeheader()
        process = subprocess.Popen(command, cwd=str(HERE.parent), env=child_env,
                                   stdout=log_file, stderr=subprocess.STDOUT)
        load_sampler = ProcessSampler(psutil.Process(process.pid), include_children=True)
        java_sampler = ProcessSampler(java)
        psutil.cpu_percent(None)
        start = time.monotonic()
        last_progress = -30
        try:
            while process.poll() is None:
                elapsed = time.monotonic() - start
                memory = psutil.virtual_memory()
                row = {"timestamp": round(time.time(), 3), "elapsed_seconds": round(elapsed, 2),
                       "system_cpu_pct": psutil.cpu_percent(None), "system_memory_pct": memory.percent,
                       "available_memory_mb": round(memory.available / 1024 ** 2, 2),
                       "locust_pid": process.pid, "java_pid": java.pid if java else ""}
                row.update(("locust_" + key, value) for key, value in load_sampler.read().items())
                row.update(("java_" + key, value) for key, value in java_sampler.read().items())
                writer.writerow(row)
                metrics.flush()
                if elapsed - last_progress >= 30:
                    print("users ceiling=" + str(maximum) + " elapsed=" + str(round(elapsed)) + "s", flush=True)
                    last_progress = elapsed
                time.sleep(2)
        except KeyboardInterrupt:
            # Windows venv launchers may have a child interpreter. Stop only this load tree.
            try:
                root = psutil.Process(process.pid)
                owned = list(reversed(root.children(recursive=True))) + [root]
                for child in owned:
                    try:
                        child.terminate()
                    except psutil.NoSuchProcess:
                        pass
                _, alive = psutil.wait_procs(owned, timeout=5)
                for child in alive:
                    try:
                        child.kill()
                    except psutil.NoSuchProcess:
                        pass
            except psutil.NoSuchProcess:
                pass
            process.wait(timeout=10)
            raise
    report = summarize(output, maximum)
    report["locust_exit_code"] = process.returncode
    report["java_resource_visible"] = java is not None
    report["database_pool"] = collect_pool_report(pool_csv, output, maximum, java.pid if java else None)
    Path(str(output) + "_summary.json").write_text(json.dumps(report, indent=2), encoding="utf-8")
    print(json.dumps(report, indent=2), flush=True)
    return process.returncode


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--host", default="http://127.0.0.1:8080")
    parser.add_argument("--users", type=int, nargs="+", default=[2200, 2500])
    parser.add_argument("--java-pid", type=int)
    parser.add_argument("--pool-csv", type=Path, help="Backend pool CSV; default: auto-detect using Java PID")
    parser.add_argument("--label", default=time.strftime("%Y%m%d-%H%M%S"))
    parser.add_argument("--summarize", type=Path, help="Read existing CSV only; requires one --users ceiling")
    args = parser.parse_args()
    port = validate_host(args.host)
    if not args.users or any(not 1 <= n <= 10000 for n in args.users):
        raise ValueError("Each user ceiling must be between 1 and 10000")
    if args.summarize:
        if len(args.users) != 1:
            raise ValueError("Use exactly one --users value with --summarize")
        print(json.dumps(summarize(args.summarize, args.users[0]), indent=2))
        return 0
    if not args.label or any(c not in "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-_" for c in args.label):
        raise ValueError("--label may contain only letters, digits, dash and underscore")
    java = psutil.Process(args.java_pid) if args.java_pid else discover_java(port)
    if java is not None and not java.name().lower().startswith("java"):
        raise ValueError("--java-pid must identify the backend Java process")
    if java is None:
        print("Backend Java PID is not visible. Java CPU/RAM will be blank, not zero. Use --java-pid if available.", flush=True)
    pool_csv = args.pool_csv or (HERE / "results" / ("backend-pool-" + str(java.pid) + ".csv") if java else None)
    if pool_csv is None or not pool_csv.exists():
        raise ValueError("Backend pool CSV not found. Restart BE with app.performance.diagnostics.enabled=true, wait 5 seconds, or specify --pool-csv PATH.")
    now = time.time()
    if not read_pool_samples(pool_csv, now - 20, now + 1, java.pid if java else None):
        raise ValueError("Backend pool CSV has no fresh samples for this Java PID. Check the running BE diagnostics before testing.")
    token = os.environ.get("LOAD_TEST_TOKEN", "").strip()
    for maximum in args.users:
        needed = len(capacity_levels(maximum)) * 90 + 90
        remaining = token_remaining(token) if token else 0
        if not token or (remaining is not None and remaining < needed):
            if not sys.stdin.isatty():
                raise ValueError("Set LOAD_TEST_TOKEN to a fresh token before running")
            token = getpass.getpass("Fresh access token (without Bearer): ").strip()
            remaining = token_remaining(token) if token else 0
            if not token or (remaining is not None and remaining < needed):
                raise ValueError("Access token has insufficient remaining lifetime for this test")
        preflight(args.host, token)
        prefix = HERE / "results" / ("diagnostic-" + args.label + "-" + str(maximum))
        code = run_one(args.host, token, maximum, prefix, java, pool_csv)
        if code:
            print("Stopping before higher load because this run had failures or could not finish.")
            return code
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (ValueError, requests.RequestException, psutil.Error, OSError) as error:
        # Never print request headers, token claims, environment or response bodies.
        print("Diagnostics could not run: " + (str(error) if isinstance(error, ValueError) else type(error).__name__), file=sys.stderr)
        raise SystemExit(2)
