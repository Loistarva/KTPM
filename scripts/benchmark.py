"""Shared latency, HTTP health probe and process-resource helpers for the P1/P2 suite."""
import json
import math
import statistics
import threading
import time
import urllib.error
import urllib.request

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
        self.timeline = []
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
        self.timeline.append({"offsetSeconds": time.perf_counter() - self.started,
                              "rssMiB": rss / 1024**2,
                              "cpuSeconds": sum(max(0, v - self.first[k]) for k, v in self.last.items())})

    def start(self):
        if self.pids:
            self.started = time.perf_counter()
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
                "timeline": self.timeline,
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
