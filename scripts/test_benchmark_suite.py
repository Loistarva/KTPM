import copy
import json
import unittest
from pathlib import Path
from unittest.mock import patch
import benchmark_suite as suite
import compare_benchmarks as comparison


class ProtocolTests(unittest.TestCase):
    def test_complete_matrix(self):
        config = json.loads(suite.CONFIG_PATH.read_text(encoding='utf-8-sig'))
        self.assertEqual(108, len(list(suite.specifications(config, 'full'))))
        self.assertEqual(25, len(list(suite.specifications(config, 'smoke'))))
        self.assertEqual(15, len({c['id'] for c in suite.specifications(config, 'full')}))

    def test_workload_is_repeatable(self):
        case = dict(kind='mixed', seed=42)
        self.assertEqual([suite.operation(case, i) for i in range(1000)],
                         [suite.operation(case, i) for i in range(1000)])
        self.assertEqual(7, len({suite.operation(case, i) for i in range(1000)}))

    def test_conflict_never_counts_as_success(self):
        samples = [dict(operation='bid', status=409, latencyMs=2, acceptable=True, validSuccess=False),
                   dict(operation='bid', status=500, latencyMs=5, acceptable=False, validSuccess=False)]
        report = suite.summarize(samples, 2)
        self.assertEqual(0, report['successfulRps'])
        self.assertEqual(.5, report['unexpectedErrorRate'])
        self.assertIsNone(report['latencySuccessful']['p95Ms'])

    def test_wrong_winner_and_acknowledged_bid_are_detected(self):
        class Client:
            def require(self, path):
                if path.startswith('/api/auctions?'):
                    return dict(totalElements=1)
                return dict(currentPrice=101, minimumBidStep=1, winningBidId=99, winnerUserId=2,
                            sellerId=1, status='ACTIVE')
        fixture = dict(owner=dict(id=1), initialTotal=0, created=1)
        row = dict(operation='bid', validSuccess=True, status=201, auctionId='1', bidId='3',
                   amount='102', bidderId='2', startedAtUtc=10, endsAt='1970-01-01T00:00:09Z')
        with patch.object(suite, 'history', return_value=[dict(id=4, amount=101, bidderId=2, status='WINNING')]):
            result = suite.audit(Client(), fixture, [row], {'1': 0})
        self.assertEqual('failed', result['status'])
        self.assertTrue(any('after deadline' in x for x in result['issues']))
        self.assertTrue(any('missing/mismatched' in x for x in result['issues']))
        self.assertTrue(any('price/user/bid' in x for x in result['issues']))

    def test_timeout_write_is_indeterminate(self):
        class Client:
            def require(self, path):
                return dict(totalElements=2)
        row = dict(operation='create', validSuccess=False, status=0)
        result = suite.audit(Client(), dict(initialTotal=0, created=1), [row], {})
        self.assertEqual('indeterminate', result['status'])

    def test_delete_audit_reads_removed_ids(self):
        class Client:
            def require(self, path):
                return dict(totalElements=0)
            def call(self, path):
                return 200, {}
        row = dict(operation='delete', validSuccess=True, status=204, auctionId='1')
        result = suite.audit(Client(), dict(initialTotal=0, created=1), [row], {})
        self.assertEqual('failed', result['status'])


class ComparisonTests(unittest.TestCase):
    def pair(self, profile='full'):
        case = dict(id='read_small', kind='catalog', concurrency=10, repeat=1, repeats=1, profile=profile)
        metrics = dict(totalRps=100, successfulRps=100, unexpectedErrorRate=0, businessConflictRate=0,
                       authFailureRate=0, latencyAll=dict(p95Ms=20,p99Ms=30),
                       latencySuccessful=dict(p95Ms=20,p99Ms=30))
        a = dict(case=case, caseHash=suite.digest(case), harnessHash='same', freshDatabaseRequired=True,
                 experiment=dict(environment=dict(benchmarkSessionId='one', cpu='same', trialMatrix=[dict(id='read_small',concurrency=10,repeat=1)])),
                 correctness=dict(status='passed'), metrics=metrics)
        b = copy.deepcopy(a)
        b['metrics']['successfulRps'] = 150
        b['metrics']['latencySuccessful']['p95Ms'] = 10
        return a, b

    def test_improvement_sign_and_zero_baseline(self):
        a,b = self.pair()
        rows = {r['metric']:r for r in comparison.compare([a],[b])}
        self.assertEqual(50, rows['successfulRps']['improvementPercent'])
        self.assertEqual(50, rows['latencySuccessful.p95Ms']['improvementPercent'])
        self.assertIsNone(rows['unexpectedErrorRate']['improvementPercent'])
        self.assertIsNone(rows['cpuSeconds']['p1Median'])

    def test_failures_and_smoke_block_claims(self):
        for profile, status in [('smoke','passed'), ('full','failed'), ('full','indeterminate')]:
            a,b = self.pair(profile)
            b['correctness']['status']=status
            self.assertTrue(all(r['improvementPercent'] is None for r in comparison.compare([a],[b])))

    def test_protocol_environment_freshness_and_missing_trials_rejected(self):
        for mutate in [lambda b:b.update(harnessHash='changed'),
                       lambda b:b['experiment']['environment'].update(benchmarkSessionId='other'),
                       lambda b:b.update(freshDatabaseRequired=False),
                       lambda b:b.update(caseHash='changed')]:
            a,b=self.pair();mutate(b)
            with self.assertRaises(ValueError): comparison.compare([a],[b])
        a,b=self.pair()
        with self.assertRaises(ValueError): comparison.compare([a],[])
        with self.assertRaises(ValueError): comparison.compare([a,a],[b,b])

    def test_all_repeats_required(self):
        a,b=self.pair();a['case']['repeats']=b['case']['repeats']=3
        with self.assertRaises(ValueError): comparison.compare([a],[b])

    def test_notebook_cells_parse_without_running_kaggle(self):
        import ast
        notebook=json.loads(Path('notebooks/KTPM_CPU_Benchmark.ipynb').read_text(encoding='utf-8'))
        for cell in notebook['cells']:
            if cell['cell_type']=='code': ast.parse(''.join(cell['source']))


if __name__ == '__main__': unittest.main()
