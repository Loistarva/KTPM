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


class KaggleInputTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        import ast, hashlib, shutil, zipfile
        notebook=json.loads(Path('notebooks/KTPM_CPU_Benchmark.ipynb').read_text(encoding='utf-8'))
        cell=ast.parse(''.join(notebook['cells'][1]['source']))
        selected=[]
        for node in cell.body:
            if isinstance(node,ast.FunctionDef) and node.name in ('source_roots','resolve_input','content_hash','materialize_input'):
                selected.append(node)
            elif isinstance(node,ast.Assign) and any(isinstance(t,ast.Name) and t.id in ('REQUIRED','IGNORED') for t in node.targets):
                selected.append(node)
        cls.helpers=dict(Path=Path,hashlib=hashlib,shutil=shutil,zipfile=zipfile)
        exec(compile(ast.Module(body=selected,type_ignores=[]),'notebook-input-helpers','exec'),cls.helpers)

    def fixture(self, root):
        root.mkdir(parents=True,exist_ok=True)
        for name in self.helpers['REQUIRED']:
            path=root/name;path.parent.mkdir(parents=True,exist_ok=True);path.write_text(name,encoding='utf-8')
        return root

    def test_auto_detect_extracted_dataset_and_copy(self):
        import tempfile
        with tempfile.TemporaryDirectory() as temporary:
            root=Path(temporary);source=self.fixture(root/'input'/'dataset'/'wrapper')
            found=self.helpers['resolve_input']('ktpm-p1-source.zip','P1',root/'input')
            self.assertEqual(source,found)
            copied,digest=self.helpers['materialize_input'](found,root/'working')
            self.assertEqual(digest,self.helpers['content_hash'](source))
            self.assertTrue((copied/'backend/pom.xml').exists())
            self.assertTrue((source/'backend/pom.xml').exists())

    def test_zip_and_extracted_source_have_same_hash(self):
        import tempfile,zipfile
        with tempfile.TemporaryDirectory() as temporary:
            root=Path(temporary);source=self.fixture(root/'input'/'dataset');archive=root/'input'/'ktpm-p1-source.zip'
            with zipfile.ZipFile(archive,'w') as stream:
                for file in source.rglob('*'):
                    if file.is_file(): stream.write(file,file.relative_to(source).as_posix())
            found=self.helpers['resolve_input']('ktpm-p1-source.zip','P1',root/'input')
            self.assertEqual(archive,found)
            _,digest=self.helpers['materialize_input'](found,root/'working')
            self.assertEqual(digest,self.helpers['content_hash'](source))

    def test_two_extracted_phases_require_explicit_paths(self):
        import tempfile
        with tempfile.TemporaryDirectory() as temporary:
            root=Path(temporary);p1=self.fixture(root/'input'/'phase1');p2=self.fixture(root/'input'/'phase2')
            with self.assertRaises(RuntimeError):
                self.helpers['resolve_input']('ktpm-p1-source.zip','P1',root/'input')
            self.assertEqual(p1,self.helpers['resolve_input'](str(p1),'P1',root/'input',False))
            self.assertEqual(p2,self.helpers['resolve_input'](str(p2),'P2',root/'input',False))

    def test_duplicate_archives_rejected(self):
        import tempfile
        with tempfile.TemporaryDirectory() as temporary:
            root=Path(temporary)
            for name in ('a','b'):
                folder=root/name;folder.mkdir();(folder/'ktpm-p1-source.zip').write_bytes(b'zip')
            with self.assertRaises(RuntimeError):
                self.helpers['resolve_input']('ktpm-p1-source.zip','P1',root)

    def test_zip_traversal_rejected_before_extract(self):
        import tempfile,zipfile
        with tempfile.TemporaryDirectory() as temporary:
            root=Path(temporary);archive=root/'bad.zip'
            with zipfile.ZipFile(archive,'w') as stream: stream.writestr('../outside.txt','bad')
            with self.assertRaises(RuntimeError): self.helpers['materialize_input'](archive,root/'working')
            self.assertFalse((root/'outside.txt').exists())


if __name__ == '__main__': unittest.main()
