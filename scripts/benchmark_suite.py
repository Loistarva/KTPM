"""Shared P1/P2 load protocol. Setup/audit use REST only, never implementation SQL.

Run one case locally with --help. Kaggle notebook supplies a fresh SUT per trial.
Reports contain no passwords/JWTs. Smoke results are not official comparisons.
"""
import argparse
import concurrent.futures
import hashlib
import json
import random
import threading
import time
import urllib.error
import urllib.request
import uuid
from collections import Counter
from datetime import datetime, timedelta, timezone
from decimal import Decimal
from pathlib import Path

import benchmark as common

CONFIG_PATH = Path(__file__).with_name("benchmark_config.json")
PASSWORD = "Benchmark123!"


def utc():
    return datetime.now(timezone.utc)


def instant(value):
    return datetime.fromisoformat(value.replace("Z", "+00:00")).timestamp()


def digest(value):
    return hashlib.sha256(json.dumps(value, sort_keys=True, separators=(",", ":")).encode()).hexdigest()


def harness_hash():
    # Ignore Windows/Linux newline conversion, but fingerprint executable protocol.
    h = hashlib.sha256()
    for name in ("benchmark_suite.py", "benchmark.py", "compare_benchmarks.py"):
        h.update(name.encode())
        h.update(Path(__file__).with_name(name).read_bytes().replace(b"\r\n", b"\n"))
    return h.hexdigest()


def specifications(config, profile):
    defaults = config["profiles"][profile]
    for scenario in config["scenarios"]:
        case = dict(defaults, seed=config["seed"], profile=profile)
        for key, value in scenario.items():
            case[key] = value[profile] if isinstance(value, dict) else value
        for concurrency in case.pop("concurrencies"):
            for repeat in range(1, case["repeats"] + 1):
                yield dict(case, concurrency=concurrency, repeat=repeat)


class Client:
    def __init__(self, base, timeout=30):
        self.base, self.timeout = base.rstrip("/"), timeout

    def call(self, path, method="GET", body=None, token=None):
        headers = {"Content-Type": "application/json"}
        if token:
            headers["Authorization"] = "Bearer " + token
        request = urllib.request.Request(self.base + path, method=method, headers=headers,
                                        data=None if body is None else json.dumps(body).encode())
        try:
            with urllib.request.urlopen(request, timeout=self.timeout) as response:
                raw = response.read()
                return response.status, json.loads(raw, parse_float=Decimal) if raw else None
        except urllib.error.HTTPError as error:
            return error.code, None
        except (urllib.error.URLError, TimeoutError, OSError):
            return 0, None
        except (ValueError, UnicodeError):
            return -1, None  # Invalid JSON is a protocol error, never a success.

    def require(self, path, method="GET", body=None, token=None, expected=200):
        status, payload = self.call(path, method, body, token)
        if status != expected:
            raise RuntimeError(f"Setup/audit {method} {path}: HTTP {status}, expected {expected}")
        return payload


def parallel(function, items, workers=8):
    with concurrent.futures.ThreadPoolExecutor(max_workers=workers) as executor:
        return list(executor.map(function, items))


def auction_body(name, start=None, end=None):
    now = utc()
    return dict(name=name, description="KTPM controlled load fixture", condition="USED",
                imageUrl=None, startingPrice="100", minimumBidStep="1",
                startingTime=(start or now - timedelta(seconds=1)).isoformat(),
                endingTime=(end or now + timedelta(hours=24)).isoformat())


def prepare(client, case, require_empty=False):
    initial_total = client.require("/api/auctions?size=1")["totalElements"]
    if require_empty and initial_total != 0:
        raise RuntimeError("Official trial requires a fresh empty auction database")
    suffix = uuid.uuid4().hex[:10]

    def account(index):
        name = f"bench_{suffix}_{index}"
        user = client.require("/api/auth/register", "POST",
                              dict(username=name, email=name + "@benchmark.local", password=PASSWORD),
                              expected=201)
        token = client.require("/api/auth/login", "POST",
                               dict(login=name, password=PASSWORD))["accessToken"]
        return dict(id=user["id"], name=name, token=token)

    accounts = parallel(account, range(case["users"] + 1))
    owner, bidders = accounts[0], accounts[1:]
    anchor = utc()
    count = case["auctions"]
    if case["kind"] == "delete":
        count = case["requests"] + case["warmup"]

    def create(index):
        start, end = None, None
        if case["kind"] == "catalog" and index % 4 == 0:
            start, end = anchor + timedelta(hours=1), anchor + timedelta(hours=24)
        if case["kind"] == "scheduler":
            start = anchor + timedelta(seconds=case["startAfterSeconds"]) if index % 2 == 0 else anchor - timedelta(seconds=1)
            end = anchor + timedelta(seconds=case["endAfterSeconds"])
        if case["kind"] == "deadline":
            end = anchor + timedelta(seconds=case["endAfterSeconds"])
        word = ("camera", "watch", "laptop", "phone")[index % 4]
        return client.require("/api/auctions", "POST", auction_body(f"B{index:06d}_{word}", start, end),
                              owner["token"], expected=201)

    auctions = parallel(create, range(count))
    active = [a for a in auctions if a["status"] == "ACTIVE"]
    if case["kind"] == "scheduler":
        for a in active:
            client.require(f"/api/auctions/{a['id']}/bids", "POST", {"amount": "100"},
                           bidders[0]["token"], expected=201)
        # Creation/seeding must finish before the scheduled start, or setup invalidates timing.
        if utc().timestamp() >= anchor.timestamp() + case["startAfterSeconds"]:
            raise RuntimeError("Scheduler fixture setup too slow; increase offsets for BOTH phases")
    for i in range(case.get("historyRows", 0)):
        client.require(f"/api/auctions/{active[0]['id']}/bids", "POST", {"amount": str(100 + i)},
                       bidders[i % len(bidders)]["token"], expected=201)
    targets = active if case["kind"] == "bid" else active[:min(16, len(active))]
    if case["kind"] == "delete":
        targets = []  # Warmup and measurement deliberately remove these auctions.
    bases = {str(a["id"]): Decimal(str(client.require(f"/api/auctions/{a['id']}")["currentPrice"]))
             for a in targets}
    return dict(owner=owner, bidders=bidders, auctions=auctions, targets=targets, bases=bases,
                initialTotal=initial_total, created=count)


def operation(case, index):
    kind = case["kind"]
    if kind in ("mixed", "ramp"):
        return random.Random(case["seed"] + index).choices(
            ["list", "detail", "history", "bid", "create", "my_auctions", "my_bids"],
            weights=[25, 20, 10, 25, 10, 5, 5])[0]
    if kind == "catalog":
        return ("list", "search", "filter", "page", "detail")[index % 5]
    return {"auth": "me", "deadline": "bid"}.get(kind, kind)


def request_job(client, case, fixture, index, warmup=False):
    op = "me" if warmup and case["kind"] == "deadline" else operation(case, index)
    user = fixture["bidders"][index % len(fixture["bidders"])]
    auction = fixture["auctions"][index % len(fixture["auctions"])]
    token, method, body, expected = user["token"], "GET", None, 200
    info = {}
    if op in ("list", "search", "filter", "page"):
        page = index % max(1, (fixture["created"] + 19) // 20) if op == "page" else 0
        path = f"/api/auctions?page={page}&size=20"
        if op == "search":
            path += "&search=camera"
        if op == "filter":
            path += "&status=SCHEDULED"
    elif op == "detail":
        path = f"/api/auctions/{auction['id']}"
        info["auctionId"] = str(auction["id"])
    elif op == "history":
        auction = fixture["targets"][0]
        page = index % max(1, (case.get("historyRows", 0) + 19) // 20)
        path = f"/api/auctions/{auction['id']}/bids?page={page}&size=20"
    elif op == "bid":
        auction = fixture["targets"][index % len(fixture["targets"])]
        amount = fixture["bases"][str(auction["id"])] + Decimal(index + 1)
        path, method, body, expected = f"/api/auctions/{auction['id']}/bids", "POST", {"amount": str(amount)}, 201
        info = dict(auctionId=str(auction["id"]), amount=str(amount), bidderId=str(user["id"]),
                    endsAt=auction["endingTime"])
    elif op == "create":
        path, method, expected, token = "/api/auctions", "POST", 201, fixture["owner"]["token"]
        body = auction_body(f"Load_{index}")
    elif op == "delete":
        path, method, expected, token = f"/api/auctions/{auction['id']}", "DELETE", 204, fixture["owner"]["token"]
        info["auctionId"] = str(auction["id"])
    elif op == "login":
        path, method, token = "/api/auth/login", "POST", None
        body = dict(login=user["name"], password=PASSWORD)
    elif op == "me":
        path = "/api/auth/me"
    elif op == "my_auctions":
        path, token = "/api/auctions/me?page=0&size=20", fixture["owner"]["token"]
    elif op == "my_bids":
        path = "/api/users/me/bids?page=0&size=20"
    else:
        raise ValueError(f"Unknown operation {op}")
    wall_start, start = utc().timestamp(), time.perf_counter()
    status, payload = client.call(path, method, body, token)
    latency = (time.perf_counter() - start) * 1000
    valid = status == expected
    if valid:
        if op in ("list", "search", "filter", "page", "history", "my_auctions", "my_bids"):
            valid = isinstance(payload, dict) and isinstance(payload.get("content"), list)
            if valid and op == "search":
                valid = all("camera" in a.get("name", "").lower() for a in payload["content"])
            if valid and op == "filter":
                valid = all(a.get("status") == "SCHEDULED" for a in payload["content"])
            if valid and op in ("list", "search", "filter", "page"):
                valid = len(payload["content"]) <= 20 and payload.get("totalElements", -1) >= len(payload["content"])
        elif op == "me":
            valid = isinstance(payload, dict) and str(payload.get("id")) == str(user["id"])
        elif op == "login":
            valid = isinstance(payload, dict) and bool(payload.get("accessToken"))
        elif op == "detail":
            valid = isinstance(payload, dict) and str(payload.get("id")) == info["auctionId"]
        elif op == "create":
            valid = isinstance(payload, dict) and payload.get("id") is not None and payload.get("name") == body["name"]
            if valid:
                info["createdId"] = str(payload["id"])
        elif op == "bid":
            valid = (isinstance(payload, dict) and payload.get("id") is not None
                     and str(payload.get("auctionId")) == info["auctionId"]
                     and str(payload.get("bidderId")) == info["bidderId"]
                     and isinstance(payload.get("amount"), (int, float, Decimal))
                     and Decimal(str(payload["amount"])) == Decimal(info["amount"]))
            if valid:
                info["bidId"] = str(payload["id"])
    acceptable = valid or (op == "bid" and status == 409)
    return dict(operation=op, status=status, latencyMs=latency, startedAtUtc=wall_start,
                validSuccess=valid, acceptable=acceptable, **info)


def summarize(samples, elapsed):
    success = [r for r in samples if r["validSuccess"]]
    groups = {}
    for op in sorted({r["operation"] for r in samples}):
        rows = [r for r in samples if r["operation"] == op]
        groups[op] = dict(requests=len(rows), statuses=dict(Counter(str(r["status"]) for r in rows)),
                          successfulRps=sum(r["validSuccess"] for r in rows) / elapsed,
                          unexpectedErrorRate=sum(not r["acceptable"] for r in rows) / max(1, len(rows)),
                          latencyAll=common.latency_summary([r["latencyMs"] for r in rows]),
                          latencySuccessful=common.latency_summary([r["latencyMs"] for r in rows if r["validSuccess"]]))
    return dict(requests=len(samples), elapsedSeconds=elapsed,
                totalRps=len(samples) / elapsed, successfulRps=len(success) / elapsed,
                successfulRequests=len(success),
                latencyAll=common.latency_summary([r["latencyMs"] for r in samples]),
                latencySuccessful=common.latency_summary([r["latencyMs"] for r in success]),
                businessConflictRate=sum(r["status"] == 409 and r["operation"] == "bid" for r in samples) / max(1, len(samples)),
                unexpectedErrorRate=sum(not r["acceptable"] for r in samples) / max(1, len(samples)),
                authFailureRate=sum(r["status"] in (401, 403) for r in samples) / max(1, len(samples)),
                statuses=dict(Counter(str(r["status"]) for r in samples)), operations=groups)


def load(client, case, fixture, concurrency, count=None, duration=None, offset=0, warmup=False):
    # Closed loop: each worker waits for its response before requesting another job.
    counter, lock = 0, threading.Lock()
    barrier = threading.Barrier(concurrency + 1)
    clock = {}

    def worker():
        nonlocal counter
        results = []
        barrier.wait()
        while True:
            with lock:
                if (count is not None and counter >= count) or (duration is not None and time.perf_counter() >= clock["deadline"]):
                    return results
                index = counter + offset
                counter += 1
            row = request_job(client, case, fixture, index, warmup)
            row["offsetSeconds"] = time.perf_counter() - clock["start"] - row["latencyMs"] / 1000
            results.append(row)

    with concurrent.futures.ThreadPoolExecutor(max_workers=concurrency) as executor:
        futures = [executor.submit(worker) for _ in range(concurrency)]
        clock["start"] = time.perf_counter()
        clock["deadline"] = clock["start"] + (duration or 0)
        barrier.wait()
        rows = [r for future in futures for r in future.result()]
    elapsed = time.perf_counter() - clock["start"]
    return rows, summarize(rows, elapsed)


def history(client, auction_id):
    rows, page = [], 0
    while True:
        response = client.require(f"/api/auctions/{auction_id}/bids?page={page}&size=100")
        rows.extend(response["content"])
        page += 1
        if page >= response["totalPages"]:
            return rows


def snapshot(client, fixture):
    return {str(a["id"]): client.require(f"/api/auctions/{a['id']}/bids?size=1")["totalElements"]
            for a in fixture["targets"]}


def audit(client, fixture, samples, before):
    issues, checked = [], 0
    unknown_writes = any(r["operation"] in ("bid", "create", "delete") and r["status"] in (0, -1)
                         for r in samples)
    for auction_id, prior_count in before.items():
        state = client.require(f"/api/auctions/{auction_id}")
        rows = history(client, auction_id)
        accepted = [r for r in samples if r["operation"] == "bid" and r["validSuccess"] and r["auctionId"] == auction_id]
        row_by_id = {str(b["id"]): b for b in rows}
        if len(row_by_id) != len(rows):
            issues.append(f"Duplicate bid IDs: auction {auction_id}")
        if len(rows) != prior_count + len(accepted) and not unknown_writes:
            issues.append(f"Stored bid count differs from acknowledged successes: auction {auction_id}")
        for result in accepted:
            bid = row_by_id.get(result["bidId"])
            if not bid or Decimal(str(bid["amount"])) != Decimal(result["amount"]) or str(bid["bidderId"]) != result["bidderId"]:
                issues.append(f"Acknowledged bid missing/mismatched: {result['bidId']}")
            if result["startedAtUtc"] >= instant(result["endsAt"]):
                issues.append(f"Bid accepted although client sent it after deadline: {result['bidId']}")
        if rows:
            winner = max(rows, key=lambda b: Decimal(str(b["amount"])))
            amounts = sorted(Decimal(str(b["amount"])) for b in rows)
            step = Decimal(str(state["minimumBidStep"]))
            if any(b - a < step for a, b in zip(amounts, amounts[1:])):
                issues.append(f"Duplicate/invalid bid increments: auction {auction_id}")
            if Decimal(str(state["currentPrice"])) != amounts[-1] or str(state["winningBidId"]) != str(winner["id"]) or str(state["winnerUserId"]) != str(winner["bidderId"]):
                issues.append(f"Final leading price/user/bid mismatch: auction {auction_id}")
            label = "WON" if state["status"] == "ENDED" else "WINNING"
            leaders = [b for b in rows if b["status"] == label]
            if len(leaders) != 1 or str(leaders[0]["id"]) != str(winner["id"]):
                issues.append(f"Not exactly one correct leader/winner: auction {auction_id}")
            if state["status"] == "ENDED" and Decimal(str(state["finalPrice"])) != amounts[-1]:
                issues.append(f"Wrong final price: auction {auction_id}")
            if any(str(b["bidderId"]) == str(state["sellerId"]) for b in rows):
                issues.append(f"Seller bid persisted: auction {auction_id}")
        elif Decimal(str(state["currentPrice"])) != Decimal(str(state["startingPrice"])) or state["winningBidId"] is not None:
            issues.append(f"Empty auction has an invalid leading price/bid: {auction_id}")
        checked += 1
    for row in samples:
        if row["validSuccess"] and row["operation"] == "delete":
            if client.call(f"/api/auctions/{row['auctionId']}")[0] != 404:
                issues.append(f"Deleted auction still readable: {row['auctionId']}")
        if row["validSuccess"] and row["operation"] == "create":
            state = client.require(f"/api/auctions/{row['createdId']}")
            if str(state["sellerId"]) != str(fixture["owner"]["id"]):
                issues.append(f"Created auction has wrong owner: {row['createdId']}")
    # Expected collection growth/shrinkage catches lost/duplicate writes across requests.
    expected_total = fixture["initialTotal"] + fixture["created"]
    expected_total += sum(r["validSuccess"] and r["operation"] == "create" for r in samples)
    expected_total -= sum(r["validSuccess"] and r["operation"] == "delete" for r in samples)
    actual_total = client.require("/api/auctions?size=1")["totalElements"]
    if actual_total != expected_total and not unknown_writes:
        issues.append("Collection count differs from fixture plus successful creates/deletes")
    return dict(status="failed" if issues else "indeterminate" if unknown_writes else "passed",
                issues=issues, auctionsChecked=checked, expectedTotal=expected_total, actualTotal=actual_total,
                note="Timeout writes can commit without a received acknowledgment; those trials are indeterminate.")


def scheduler(client, case, fixture):
    events = {}
    for a in fixture["auctions"]:
        events[str(a["id"])] = dict(start=instant(a["startingTime"]), end=instant(a["endingTime"]),
                                     scheduled=a["status"] == "SCHEDULED", seeded= a["status"] == "ACTIVE",
                                     openLastBefore=None, endLastBefore=None, openObserved=None, endObserved=None)
    deadline = max(e["end"] for e in events.values()) + case["graceSeconds"]
    polls, issues = 0, []
    started = time.perf_counter()
    while utc().timestamp() < deadline:
        pending = [key for key, value in events.items() if value["endObserved"] is None]
        if not pending:
            break

        def observe(key):
            sent = utc().timestamp()
            status, state = client.call(f"/api/auctions/{key}")
            return key, sent, utc().timestamp(), status, state

        for key, sent, received, status, state in parallel(observe, pending):
            polls += 1
            event = events[key]
            if status != 200:
                issues.append(f"Scheduler observer HTTP {status}: {key}")
                continue
            if state["status"] == "SCHEDULED":
                event["openLastBefore"] = sent
            elif state["status"] == "ACTIVE":
                if event["scheduled"] and event["openObserved"] is None:
                    event["openObserved"] = received
                event["endLastBefore"] = sent
            elif state["status"] in ("ENDED", "FAILED"):
                event["endObserved"] = received
                expected = "ENDED" if event["seeded"] else "FAILED"
                if state["status"] != expected:
                    issues.append(f"Wrong final scheduler status: {key}")
                if event["seeded"] and (str(state["winnerUserId"]) != str(fixture["bidders"][0]["id"]) or Decimal(str(state["finalPrice"])) != 100):
                    issues.append(f"Wrong scheduler winner/price: {key}")
        time.sleep(.5)
    for key, event in events.items():
        if event["scheduled"] and event["openObserved"] is None:
            issues.append(f"Opening not observed within active window: {key}")
        if event["endObserved"] is None:
            issues.append(f"Auction not finalized before grace deadline: {key}")
        # Another bidder, above current price, sent after endingTime must be rejected.
        status, _ = client.call(f"/api/auctions/{key}/bids", "POST", {"amount": "1000000"}, fixture["bidders"][1]["token"])
        if status != 409:
            issues.append(f"Post-deadline bid expected 409, got {status}: {key}")
        for prefix, due in (("open", event["start"]), ("end", event["end"])):
            observed, previous = event[prefix + "Observed"], event[prefix + "LastBefore"]
            event[prefix + "LagUpperMs"] = max(0, (observed - due) * 1000) if observed is not None else None
            event[prefix + "LagLowerMs"] = max(0, (previous - due) * 1000) if previous is not None else 0
    opened = [v["openLagUpperMs"] for v in events.values() if v["scheduled"] and v["openLagUpperMs"] is not None]
    ended = [v["endLagUpperMs"] for v in events.values() if v["endLagUpperMs"] is not None]
    return dict(elapsedSeconds=time.perf_counter() - started, observerRequests=polls,
                openObservedLag=common.latency_summary(opened), endObservedLag=common.latency_summary(ended),
                missedOpen=sum(v["scheduled"] and v["openObserved"] is None for v in events.values()),
                missedEnd=sum(v["endObserved"] is None for v in events.values()), events=events,
                correctness=dict(status="failed" if issues else "passed", issues=issues),
                note="Lag is an API-observed upper bound, including monitor traffic/response time; not exact internal transition time.")


def run_case(base, case, output, phase="LOCAL", server_pids=(), database_pids=(), require_empty=False,
             experiment=None):
    output = Path(output)
    output.mkdir(parents=True, exist_ok=True)
    client = Client(base)
    setup_start = time.perf_counter()
    fixture = prepare(client, case, require_empty)
    setup_elapsed = time.perf_counter() - setup_start
    report = dict(schemaVersion=2, phase=phase, case=case, caseHash=digest(case),
                  harnessHash=harness_hash(), experiment=experiment or {},
                  recordedAtUtc=utc().isoformat(), freshDatabaseRequired=require_empty,
                  setupSeconds=setup_elapsed, fixture=dict(users=case["users"], auctions=fixture["created"],
                                                         historyRows=case.get("historyRows", 0)))
    meter = common.ResourceMeter(list(server_pids) + list(database_pids))
    all_samples, stages = [], []
    if case["kind"] == "scheduler":
        meter.start()
        start = time.perf_counter()
        try:
            result = scheduler(client, case, fixture)
        finally:
            report["resourceMetrics"] = meter.finish(time.perf_counter() - start)
        report["scheduler"] = result
        report["correctness"] = result["correctness"]
    else:
        warmup, _ = load(client, case, fixture, 1, count=case["warmup"], warmup=True)
        if any(not r["acceptable"] for r in warmup):
            raise RuntimeError("Warmup failed; trial cannot establish a valid baseline")
        # Audits reconcile measured writes relative to post-warmup database state.
        before = snapshot(client, fixture)
        fixture["created"] += sum(r["validSuccess"] and r["operation"] == "create" for r in warmup)
        fixture["created"] -= sum(r["validSuccess"] and r["operation"] == "delete" for r in warmup)
        offset = case["warmup"]
        levels = case.get("levels", [case["concurrency"]])
        meter.start()
        start = time.perf_counter()
        try:
            for level in levels:
                duration = case.get("stageSeconds", case.get("durationSeconds"))
                samples, metrics = load(client, case, fixture, level,
                                        count=None if duration else case["requests"], duration=duration, offset=offset)
                offset += len(samples)
                for sample in samples:
                    sample["stageConcurrency"] = level
                    sample["stage"] = len(stages)
                all_samples.extend(samples)
                stages.append(dict(concurrency=level, metrics=metrics))
        finally:
            report["resourceMetrics"] = meter.finish(time.perf_counter() - start)
        report["metrics"] = summarize(all_samples, sum(s["metrics"]["elapsedSeconds"] for s in stages))
        resource = report["resourceMetrics"]
        successes = report["metrics"]["successfulRequests"]
        resource["cpuMsPerSuccessfulRequest"] = (resource["cpuSeconds"] * 1000 / successes
                                                  if resource.get("available") and successes else None)
        report["stages"] = stages
        report["correctness"] = audit(client, fixture, all_samples, before)
        if report["metrics"]["unexpectedErrorRate"] > 0:
            report["correctness"]["status"] = "failed"
            report["correctness"]["issues"].append("Unexpected HTTP/protocol errors during measured load")
        # Timeline buckets permit sustained-load/ramp trend inspection.
        report["timeline"] = []
        for stage_index, stage in enumerate(stages):
            rows = [r for r in all_samples if r["stage"] == stage_index]
            for bucket in sorted({int(r["offsetSeconds"] // 10) for r in rows}):
                group = [r for r in rows if int(r["offsetSeconds"] // 10) == bucket]
                span = max(.001, min(10, stage["metrics"]["elapsedSeconds"] - bucket * 10))
                report["timeline"].append(dict(stage=stage_index, concurrency=stage["concurrency"],
                                               startSeconds=bucket * 10, metrics=summarize(group, span)))
    (output / "report.json").write_text(json.dumps(report, indent=2), encoding="utf-8")
    with (output / "samples.jsonl").open("w", encoding="utf-8") as stream:
        for row in all_samples:
            stream.write(json.dumps(row) + "\n")
    print(f"{phase} {case['id']} c={case['concurrency']} r={case['repeat']}: "
          f"correctness={report['correctness']['status']}; report={output / 'report.json'}")
    return report


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base", default="http://localhost:8080")
    parser.add_argument("--config", type=Path, default=CONFIG_PATH)
    parser.add_argument("--profile", choices=("smoke", "full"), default="smoke")
    parser.add_argument("--case", required=True)
    parser.add_argument("--concurrency", type=int)
    parser.add_argument("--phase", default="LOCAL")
    parser.add_argument("--output", required=True)
    parser.add_argument("--server-pids", type=int, nargs="*", default=[])
    parser.add_argument("--database-pids", type=int, nargs="*", default=[])
    parser.add_argument("--require-empty", action="store_true")
    args = parser.parse_args()
    config = json.loads(args.config.read_text(encoding="utf-8"))
    cases = [c for c in specifications(config, args.profile) if c["id"] == args.case and c["repeat"] == 1]
    if not cases:
        parser.error("Unknown case; inspect benchmark_config.json")
    case = cases[0]
    if args.concurrency is not None:
        if args.concurrency < 1:
            parser.error("Concurrency must be positive")
        case["concurrency"] = args.concurrency
    run_case(args.base, case, args.output, args.phase, args.server_pids, args.database_pids, args.require_empty)


if __name__ == "__main__":
    main()
