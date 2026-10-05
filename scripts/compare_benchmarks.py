"""Strict paired P1/P2 benchmark comparison; never silently compare different protocols."""
import argparse
import csv
import json
import statistics
from pathlib import Path


def flatten(report):
    resource = report.get('resourceMetrics', {})
    result = {'cpuSeconds': resource.get('cpuSeconds'), 'peakRssMiB': resource.get('peakRssMiB'),
              'cpuMsPerSuccessfulRequest': resource.get('cpuMsPerSuccessfulRequest')}
    if 'scheduler' in report:
        value = report['scheduler']
        for event in ('open', 'end'):
            for percentile in ('p95Ms', 'p99Ms'):
                result[event + 'Lag.' + percentile] = value[event + 'ObservedLag'][percentile]
        result['missedOpen'] = value['missedOpen']
        result['missedEnd'] = value['missedEnd']
    else:
        def metrics(prefix, value):
            for name in ('totalRps', 'successfulRps', 'unexpectedErrorRate', 'businessConflictRate', 'authFailureRate'):
                result[prefix + name] = value[name]
            for label in ('latencyAll', 'latencySuccessful'):
                for percentile in ('p95Ms', 'p99Ms'):
                    result[prefix + label + '.' + percentile] = value[label][percentile]
            for name, op in value.get('operations', {}).items():
                result[prefix + 'op.' + name + '.successfulRps'] = op['successfulRps']
                result[prefix + 'op.' + name + '.p95Ms'] = op['latencySuccessful']['p95Ms']
        metrics('', report['metrics'])
        if report['case']['kind'] == 'ramp':
            for stage in report['stages']:
                metrics('stage.' + str(stage['concurrency']) + '.', stage['metrics'])
    return result


def improvement(before, after, higher=False):
    if before is None or after is None or before == 0:
        return None
    return 100 * ((after - before) if higher else (before - after)) / before


def compare(p1, p2):
    def index(reports):
        out = {}
        for r in reports:
            key = (r['case']['id'], r['case']['concurrency'], r['case']['repeat'])
            if key in out:
                raise ValueError('Duplicate trial: ' + str(key))
            out[key] = r
        return out
    left, right = index(p1), index(p2)
    if not left or left.keys() != right.keys():
        raise ValueError('Missing or different cases/repeats; run the same complete matrix')
    environments = [r.get('experiment', {}).get('environment') for r in p1 + p2]
    if not environments[0] or any(v != environments[0] for v in environments):
        raise ValueError('Environment/session/resource budget differs or is missing; rerun both in one kernel')
    matrix = environments[0].get('trialMatrix')
    if matrix is None:
        raise ValueError('Declared experiment matrix missing')
    expected = {(v['id'], v['concurrency'], v['repeat']) for v in matrix}
    if left.keys() != expected:
        raise ValueError('Missing trials from the declared experiment matrix')
    if environments[0].get('selectedCases'):
        raise ValueError('Filtered development runs are not complete official comparisons')
    hashes = {r['harnessHash'] for r in p1 + p2}
    if len(hashes) != 1:
        raise ValueError('Executable benchmark protocol differs')
    groups = {}
    for key in sorted(left):
        a, b = left[key], right[key]
        if a['caseHash'] != b['caseHash'] or a['case'] != b['case']:
            raise ValueError('Workload differs: ' + str(key))
        if not a['freshDatabaseRequired'] or not b['freshDatabaseRequired']:
            raise ValueError('Fresh databases required for comparison')
        groups.setdefault(key[:2], []).append((a, b))
    rows = []
    for (case_id, concurrency), pairs in groups.items():
        repeat_ids = {a['case']['repeat'] for a, _ in pairs}
        if repeat_ids != set(range(1, pairs[0][0]['case']['repeats'] + 1)):
            raise ValueError('Incomplete repetitions: ' + case_id)
        safe = all(r['correctness']['status'] == 'passed' for pair in pairs for r in pair)
        formal = safe and pairs[0][0]['case']['profile'] == 'full' and matrix is not None
        values = [(flatten(a), flatten(b)) for a, b in pairs]
        for metric in sorted(set().union(*(set(a) | set(b) for a, b in values))):
            av = [a.get(metric) for a, _ in values]
            bv = [b.get(metric) for _, b in values]
            # Missing successes/resources remain missing, never converted to zero or dropped silently.
            complete = all(v is not None for v in av + bv)
            meda = statistics.median(av) if complete else None
            medb = statistics.median(bv) if complete else None
            rows.append(dict(case=case_id, concurrency=concurrency, metric=metric, repeats=len(pairs),
                             p1Median=meda, p2Median=medb,
                             p1Min=min(av) if complete else None, p1Max=max(av) if complete else None,
                             p2Min=min(bv) if complete else None, p2Max=max(bv) if complete else None,
                             improvementPercent=improvement(meda, medb, metric.endswith('Rps')) if formal else None,
                             comparisonStatus='eligible' if formal else 'smoke_only' if safe else 'correctness_failed'))
    return rows


def read_reports(root):
    return [json.loads(p.read_text(encoding='utf-8')) for p in sorted(Path(root).rglob('report.json'))]


def write_comparison(p1, p2, output):
    rows = compare(read_reports(p1), read_reports(p2))
    output = Path(output)
    output.mkdir(parents=True, exist_ok=True)
    with (output / 'comparison.csv').open('w', newline='', encoding='utf-8') as stream:
        writer = csv.DictWriter(stream, fieldnames=list(rows[0]))
        writer.writeheader()
        writer.writerows(rows)
    (output / 'comparison.json').write_text(json.dumps(rows, indent=2), encoding='utf-8')
    lines = ['# P1 / P2', '', 'Positive improvement means higher RPS or lower latency/resource usage.',
             'Medians are across trials, not pooled request percentiles. A zero baseline has no percentage.', '',
             '| Case | C | Metric | P1 median | P2 median | Improvement % | Status |',
             '|---|---:|---|---:|---:|---:|---|']
    for row in rows:
        def value(key):
            x = row[key]
            return '-' if x is None else f'{x:.3f}'
        lines.append(f"| {row['case']} | {row['concurrency']} | {row['metric']} | {value('p1Median')} | {value('p2Median')} | {value('improvementPercent')} | {row['comparisonStatus']} |")
    (output / 'comparison.md').write_text('\n'.join(lines) + '\n', encoding='utf-8')
    return rows


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--p1', required=True)
    parser.add_argument('--p2', required=True)
    parser.add_argument('--output', required=True)
    args = parser.parse_args()
    write_comparison(args.p1, args.p2, args.output)
