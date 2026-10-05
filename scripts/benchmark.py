"""KTPM baseline runner (Python standard library, suitable for Kaggle CPU).
Run prepare against your development instance, then run unchanged fixtures/workloads for P1/P2.
Tokens in fixture files are private. No credentials or tokens are printed in reports.
"""
import argparse
import hashlib
import os
import platform
import threading
import concurrent.futures
import json
import math
import random
import statistics
import time
import urllib.error
import urllib.request
import uuid
from collections import Counter
from datetime import datetime, timedelta, timezone
from decimal import Decimal
from pathlib import Path

def percentile(values, fraction):
    ordered = sorted(values)
    return ordered[max(0, math.ceil(fraction * len(ordered)) - 1)] if ordered else None

def latency_summary(values):
    return {"averageMs": statistics.mean(values) if values else None,
            "p95Ms": percentile(values, .95), "p99Ms": percentile(values, .99)}

class ResourceMeter:
    """Sample only supplied server/database process trees; exclude load generator."""
    def __init__(self, pids):
        self.pids = [pid for pid in pids if pid]
        self.stop_event = threading.Event()
        self.first, self.last = {}, {}
        self.peak_rss, self.samples = 0, 0
        self.thread = None
        if self.pids:
            import psutil
            self.psutil = psutil
            for pid in self.pids:
                psutil.Process(pid).status()

    def sample(self):
        processes = {}
        for pid in self.pids:
            try:
                root = self.psutil.Process(pid)
                for process in [root] + root.children(recursive=True):
                    processes[process.pid] = process
            except self.psutil.Error:
                continue
        rss = 0
        for process in processes.values():
            try:
                key = (process.pid, process.create_time())
                cpu = process.cpu_times()
                value = cpu.user + cpu.system
                self.first.setdefault(key, value)
                self.last[key] = value
                rss += process.memory_info().rss
            except self.psutil.Error:
                continue
        self.peak_rss = max(self.peak_rss, rss)
        self.samples += 1

    def start(self):
        if self.pids:
            self.sample()
            def worker():
                while not self.stop_event.wait(.25):
                    self.sample()
            self.thread = threading.Thread(target=worker, daemon=True)
            self.thread.start()

    def finish(self, elapsed):
        if not self.pids:
            return {"available": False, "reason": "No server/database PIDs supplied"}
        self.stop_event.set()
        self.thread.join()
        self.sample()
        cpu = sum(max(0, value - self.first[key]) for key, value in self.last.items())
        return {"available": True, "cpuSeconds": cpu,
                "cpuPercentOfOneCore": 100 * cpu / elapsed,
                "peakRssMiB": self.peak_rss / 1024**2, "samples": self.samples,
                "limitations": "RSS sums shared pages; sampling can miss short-lived processes. CPU includes server and PostgreSQL, excludes client."}

def api(base, path, method="GET", body=None, token=None, timeout=30):
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = "Bearer " + token
    request = urllib.request.Request(base.rstrip("/") + path, data=None if body is None else json.dumps(body).encode(), headers=headers, method=method)
    try:
        with urllib.request.urlopen(request, timeout=timeout) as response:
            payload = response.read()
            return response.status, json.loads(payload) if payload else None
    except urllib.error.HTTPError as error:
        return error.code, None
    except (urllib.error.URLError, TimeoutError):
        return 0, None

def require(result, expected):
    status, body = result
    if status != expected:
        raise RuntimeError(f"Setup request failed: HTTP {status}, expected {expected}")
    return body

def login(args, name, password):
    return require(api(args.base, "/api/auth/login", "POST", {"login": name, "password": password}), 200)["accessToken"]

def prepare(args):
    seller = login(args, "seller", "Seller123!")
    now = datetime.now(timezone.utc)
    auction = require(api(args.base, "/api/auctions", "POST", {
        "name": "Benchmark " + uuid.uuid4().hex[:12], "description": "Manual auction load test",
        "condition": "NEW", "imageUrl": None, "startingPrice": 100, "minimumBidStep": 1,
        "startingTime": (now - timedelta(seconds=1)).isoformat(),
        "endingTime": (now + timedelta(minutes=50)).isoformat()
    }, seller), 201)
    tokens = []
    suffix = uuid.uuid4().hex[:10]
    for i in range(args.users):
        name = f"bench{suffix}_{i}"
        require(api(args.base, "/api/auth/register", "POST", {
            "username": name, "email": name + "@benchmark.local",
            "password": "Benchmark123!"
        }), 201)
        token = login(args, name, "Benchmark123!")
        tokens.append(token)
    Path(args.fixture).write_text(json.dumps({"base": args.base, "auctionId": auction["id"], "tokens": tokens, "createdAt": now.isoformat()}), encoding="utf-8")
    if os.name != "nt":
        Path(args.fixture).chmod(0o600)
    print(f"Prepared auction {auction['id']} and {len(tokens)} users. Fixture saved privately to {args.fixture}.")

def run(args):
    fixture = json.loads(Path(args.fixture).read_text(encoding="utf-8"))
    base, auction_id, tokens = fixture["base"], fixture["auctionId"], fixture["tokens"]
    if not tokens:
        raise RuntimeError("Fixture has no users")
    state = require(api(base, f"/api/auctions/{auction_id}"), 200)
    if state["status"] != "ACTIVE" and args.mode != "read":
        raise RuntimeError("Prepare a new fixture: auction is no longer active")
    current = Decimal(str(state["currentPrice"]))
    step = Decimal(str(state["minimumBidStep"]))
    rng = random.Random(args.seed)
    operations = []
    for i in range(args.requests):
        if args.mode == "mixed":
            draw = rng.random()
            operation = "list" if draw < .70 else "detail" if draw < .85 else "bid"
        elif args.mode == "read":
            operation = ("list", "detail", "history")[i % 3]
        else:
            operation = args.mode
        operations.append((i, operation))
    def request(job):
        i, operation = job
        token = tokens[i % len(tokens)]
        if operation == "list":
            path, method, body = "/api/auctions?page=0&size=20", "GET", None
        elif operation == "detail":
            path, method, body = f"/api/auctions/{auction_id}", "GET", None
        elif operation == "history":
            path, method, body = f"/api/auctions/{auction_id}/bids?page=0&size=20", "GET", None
        elif operation == "bid":
            path, method, body = f"/api/auctions/{auction_id}/bids", "POST", {"amount": str(current + step * (1 + i))}
        start = time.perf_counter()
        status, _ = api(base, path, method, body, token, args.timeout)
        return operation, status, (time.perf_counter() - start) * 1000
    meter = ResourceMeter([args.server_pid, args.database_pid])
    meter.start()
    start = time.perf_counter()
    with concurrent.futures.ThreadPoolExecutor(max_workers=args.concurrency) as executor:
        samples = list(executor.map(request, operations))
    elapsed = time.perf_counter() - start
    resources = meter.finish(elapsed)
    latencies = sorted(row[2] for row in samples)
    statuses = Counter(str(row[1]) for row in samples)
    report = {
        "phase": args.phase, "mode": args.mode, "seed": args.seed,
        "requests": len(samples), "concurrency": args.concurrency,
        "elapsedSeconds": round(elapsed, 3),
        "throughputRequestsPerSecond": round(len(samples) / elapsed, 2),
        "successfulRequestsPerSecond": sum(200 <= row[1] < 300 for row in samples) / elapsed,
        "successfulRequests": sum(200 <= row[1] < 300 for row in samples),
        "averageLatencyMs": round(statistics.mean(latencies), 2),
        "p95LatencyMs": round(latencies[max(0, math.ceil(.95 * len(latencies)) - 1)], 2),
        "httpStatuses": dict(statuses),
        "businessConflictRate": statuses["409"] / len(samples),
        "unexpectedErrorRate": sum(1 for _, status, _ in samples if status == 0 or (status >= 400 and status != 409)) / len(samples),
        "authFailureRate": sum(1 for _, status, _ in samples if status in (401, 403)) / len(samples),
        "operations": dict(Counter(row[0] for row in samples)),
        "latencyAll": latency_summary(latencies),
        "latencySuccessful": latency_summary([row[2] for row in samples if 200 <= row[1] < 300]),
        "initialState": {"currentPrice": str(current), "minimumBidStep": str(step)},
        "runnerSha256": hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
        "environment": {"python": platform.python_version(), "platform": platform.platform(), "logicalCpuCount": os.cpu_count()},
        "recordedAtUtc": datetime.now(timezone.utc).isoformat(),
        "resourceMetrics": resources
    }
    Path(args.output).write_text(json.dumps(report, indent=2), encoding="utf-8")
    if args.samples_output:
        Path(args.samples_output).write_text("".join(json.dumps({"operation": op, "status": status, "latencyMs": latency}) + "\n" for op, status, latency in samples), encoding="utf-8")
    print(json.dumps(report, indent=2))
    return report

def main():
    parser = argparse.ArgumentParser()
    sub = parser.add_subparsers(dest="command", required=True)
    p = sub.add_parser("prepare")
    p.add_argument("--base", default="http://localhost:8080")
    p.add_argument("--users", type=int, default=25)
    p.add_argument("--fixture", default="benchmark-fixture.json")
    r = sub.add_parser("run")
    r.add_argument("--fixture", default="benchmark-fixture.json")
    r.add_argument("--mode", choices=["read", "bid", "mixed"], default="read")
    r.add_argument("--concurrency", type=int, default=50)
    r.add_argument("--requests", type=int, default=1000)
    r.add_argument("--timeout", type=int, default=30)
    r.add_argument("--seed", type=int, default=42)
    r.add_argument("--phase", default="P1")
    r.add_argument("--output", default="benchmark-results.json")
    r.add_argument("--samples-output")
    r.add_argument("--server-pid", type=int)
    r.add_argument("--database-pid", type=int)
    args = parser.parse_args()
    if args.command == "prepare":
        if args.users < 1:
            parser.error("--users must be positive")
        prepare(args)
    else:
        if min(args.requests, args.concurrency, args.timeout) < 1:
            parser.error("--requests, --concurrency, --timeout must be positive")
        run(args)

if __name__ == "__main__":
    main()

