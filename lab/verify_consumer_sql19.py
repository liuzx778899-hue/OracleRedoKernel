"""Only the new independent target comparison; never reruns accepted crash cases."""
import json
import sqlite3
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / 'lab/out/consumer-commit-sql-20261005'
GOOD = ROOT / 'lab/out/consumer-commit-20261005'
OLD = ROOT / 'lab/out/closed-follow-20261005'
OUT.mkdir(parents=True, exist_ok=False)


def run(name, target, ack, expected=0):
    args = [sys.executable, ROOT / 'lab/compare_consumer_sql19.py', target, ack,
            OLD / 'baseline/snapshot.stdout.txt', OLD]
    args = list(map(str, args))
    (OUT / (name + '.command.json')).write_text(json.dumps(args))
    result = subprocess.run(args, capture_output=True, timeout=30)
    for ext, data in [('stdout', result.stdout), ('stderr', result.stderr)]:
        (OUT / (name + '.' + ext + '.txt')).write_bytes(data)
    (OUT / (name + '.rc.txt')).write_text(str(result.returncode))
    assert result.returncode == expected and not result.stderr, name
    proof = json.loads(result.stdout)
    assert bool(proof['mismatches']) == bool(expected), name
    return proof


for name in ('before-commit', 'after-commit', 'after-ack'):
    run(name, GOOD / (name + '.sqlite'), GOOD / (name + '.ack.json'))
run('actual-corrupted-target', GOOD / 'normal.sqlite', GOOD / 'normal.ack.json', 1)
for name in ('missing-application', 'wrong-after', 'wrong-ack'):
    path = OUT / (name + '.sqlite')
    with sqlite3.connect((GOOD / 'after-commit.sqlite').resolve().as_uri() + '?mode=ro', uri=True) as source:
        with sqlite3.connect(path) as dest:
            source.backup(dest)
    ack = OUT / (name + '.ack.json')
    ack.write_bytes((GOOD / 'after-commit.ack.json').read_bytes())
    with sqlite3.connect(path) as conn:
        if name == 'missing-application':
            conn.execute('DELETE FROM applications WHERE ordinal=1')
        elif name == 'wrong-after':
            payload = json.loads(conn.execute('SELECT payload FROM applications WHERE ordinal=1').fetchone()[0])
            payload['after'][1] = 'VALUE:00'
            conn.execute('UPDATE applications SET payload=? WHERE ordinal=1', (json.dumps(payload),))
        else:
            value = json.loads(ack.read_bytes()); value['events'] = 4
            ack.write_text(json.dumps(value))
    run(name, path, ack, 1)
proof = {'independent_real_target_comparisons': 3, 'original_source_commit_snapshots': 3,
         'independent_comparator_counterexamples': 4, 'source_actions': 0,
         'accepted_crash_cases_rerun': False, 'streamhouse_site_accepted': False}
(OUT / 'proof.json').write_text(json.dumps(proof, indent=2))
print(json.dumps(proof))
