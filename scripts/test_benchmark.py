import json
import unittest
from decimal import Decimal
import benchmark
import benchmark_suite as suite


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

    def test_error_classification_and_no_tokens_in_results(self):
        class Client:
            def call(self, path, method, body, token):
                return 201, dict(id=3, auctionId=1, bidderId=2, amount=Decimal(101), accessToken=token)
        fixture=dict(bidders=[dict(id=2,token="PRIVATE_TOKEN")],
                     auctions=[dict(id=1,endingTime="2099-01-01T00:00:00Z")],
                     targets=[dict(id=1,endingTime="2099-01-01T00:00:00Z")],bases={"1":Decimal(100)})
        success=suite.request_job(Client(),dict(kind="bid"),fixture,0)
        rows=[success]+[dict(operation="bid",status=code,validSuccess=False,acceptable=code==409,latencyMs=1)
                        for code in (409,401,429)]
        result=suite.summarize(rows,1)
        self.assertEqual(result['successfulRequests'],1)
        self.assertEqual(result['businessConflictRate'],.25)
        self.assertEqual(result['unexpectedErrorRate'],.5)
        self.assertEqual(result['authFailureRate'],.25)
        self.assertNotIn('PRIVATE_TOKEN',json.dumps(rows))
        self.assertNotIn('PRIVATE_TOKEN',json.dumps(result))


if __name__ == "__main__": unittest.main()
