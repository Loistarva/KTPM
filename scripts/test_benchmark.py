import contextlib
import io
import json
import tempfile
import unittest
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import patch

import benchmark


class BenchmarkTest(unittest.TestCase):
    def test_percentile_uses_nearest_rank(self):
        self.assertEqual(benchmark.percentile(range(1, 101), .95), 95)
        self.assertEqual(benchmark.percentile(range(1, 101), .99), 99)
        self.assertIsNone(benchmark.percentile([], .95))

    def test_missing_success_latency_is_unknown(self):
        self.assertIsNone(benchmark.latency_summary([])["p95Ms"])

    def test_missing_process_metrics_are_unknown(self):
        meter = benchmark.ResourceMeter([None, None])
        meter.start()
        self.assertFalse(meter.finish(1)["available"])

    def test_failed_requests_are_not_successful_throughput(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            fixture = root / "fixture.json"
            fixture.write_text(json.dumps({"base": "http://localhost", "auctionId": 1,
                                           "tokens": ["PRIVATE_TOKEN"]}))
            args = SimpleNamespace(fixture=str(fixture), mode="bid", requests=4,
                                   concurrency=1, seed=42, timeout=1, phase="test",
                                   output=str(root / "report.json"),
                                   samples_output=str(root / "samples.jsonl"),
                                   server_pid=None, database_pid=None)
            responses = [(200, {"status": "ACTIVE", "currentPrice": 100,
                                "minimumBidStep": 10}),
                         (201, None), (409, None), (401, None), (429, None)]
            with patch.object(benchmark, "api", side_effect=responses), contextlib.redirect_stdout(io.StringIO()):
                report = benchmark.run(args)
            self.assertEqual(report["successfulRequests"], 1)
            self.assertEqual(report["businessConflictRate"], .25)
            self.assertEqual(report["unexpectedErrorRate"], .5)
            self.assertEqual(report["authFailureRate"], .25)
            self.assertNotIn("PRIVATE_TOKEN", Path(args.output).read_text())
            self.assertNotIn("PRIVATE_TOKEN", Path(args.samples_output).read_text())


if __name__ == "__main__":
    unittest.main()
