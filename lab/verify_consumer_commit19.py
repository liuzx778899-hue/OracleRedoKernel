"""Real local SQLite commit/ACK crash experiment over existing raw capture.

No Oracle action and no source-DML replay. Never overwrites an earlier run.
"""
import copy
import hashlib
import json
import sqlite3
import subprocess
import sys
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / 'lab/out/consumer-commit-20261005'
OLD = ROOT / 'lab/out/closed-follow-20261005'
ARTIFACT = ROOT / 'lab/out/capture-rows-r2-20261005'
WORKER = ROOT / 'lab/consume_captured_rows.py'
JAVA = 'D:/work/Java/jdk-17.0.7/bin/java.exe'


def encoded(value):
    return json.dumps(value, sort_keys=True, separators=(',', ':')).encode()


def sha(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def run(name, args, reason=None):
    args = list(map(str, args))
    (OUT / (name + '.command.json')).write_bytes(encoded(args))
    result = subprocess.run(args, capture_output=True, timeout=90)
    for ext, data in [('stdout', result.stdout), ('stderr', result.stderr)]:
        (OUT / (name + '.' + ext + '.txt')).write_bytes(data)
    (OUT / (name + '.rc.txt')).write_text(str(result.returncode))
    if reason:
        assert result.returncode == 1 and not result.stdout and reason.encode() in result.stderr, name
    else:
        assert result.returncode == 0, name
    return result


def spec_for(name):
    spec = copy.deepcopy(BASE)
    spec['target_id'] = 'ork-owned-sqlite-' + name
    return spec


def initialize(name, spec):
    target = OUT / (name + '.sqlite')
    with sqlite3.connect(target) as conn:
        conn.execute('PRAGMA journal_mode=WAL')
        conn.execute('PRAGMA synchronous=FULL')
        conn.executescript('CREATE TABLE target_identity(target_id TEXT PRIMARY KEY);'
                          'CREATE TABLE rows(row_key TEXT PRIMARY KEY,cells TEXT NOT NULL);'
                          'CREATE TABLE applications(event_id TEXT PRIMARY KEY,batch_id TEXT NOT NULL,'
                          'ordinal INTEGER NOT NULL,payload TEXT NOT NULL);'
                          'CREATE TABLE commits(batch_id TEXT PRIMARY KEY,proof TEXT NOT NULL);')
        conn.execute('INSERT INTO target_identity VALUES(?)', (spec['target_id'],))
        conn.execute('INSERT INTO rows VALUES(?,?)', (ROW_KEY, encoded(BASELINE_CELLS).decode()))
    Path(str(target) + '.consumer.lock').write_bytes(b'0')
    return target, OUT / (name + '.ack.json')


def command(name, spec, target, ack):
    path = OUT / (name + '.spec.json')
    assert not path.exists(), name
    path.write_bytes(encoded(spec))
    return [sys.executable, WORKER, path, sha(path), target, ack]


def state(name, target, ack):
    with sqlite3.connect(target) as conn:
        result = {'rows': conn.execute('SELECT * FROM rows').fetchall(),
                  'applications': conn.execute('SELECT * FROM applications ORDER BY ordinal').fetchall(),
                  'commits': conn.execute('SELECT * FROM commits').fetchall(),
                  'integrity_check': conn.execute('PRAGMA integrity_check').fetchall(),
                  'ack_exists': ack.exists()}
    (OUT / (name + '.state.json')).write_bytes(encoded(result))
    assert result['integrity_check'] == [('ok',)]
    return result


def crash(name, args, stage, dual=False):
    marker = OUT / (name + '.ready')
    args = list(map(str, args + [stage, marker]))
    (OUT / (name + '.command.json')).write_bytes(encoded(args))
    with (OUT / (name + '.stdout.txt')).open('wb') as stdout, (OUT / (name + '.stderr.txt')).open('wb') as stderr:
        proc = subprocess.Popen(args, stdin=subprocess.PIPE, stdout=stdout, stderr=stderr)
        try:
            deadline = time.monotonic() + 30
            while not marker.exists() and proc.poll() is None and time.monotonic() < deadline:
                time.sleep(.05)
            assert marker.read_text() == stage, name
            if dual:
                run(name + '-second-consumer', args[:-2], 'CONSUMER_ALREADY_RUNNING')
            proc.kill()  # Actual Windows TerminateProcess, not a simulated exception.
            rc = proc.wait(timeout=10)
            (OUT / (name + '.rc.txt')).write_text(str(rc))
            assert rc != 0
        finally:
            if proc.poll() is None:
                proc.kill()
                proc.wait(timeout=10)
            proc.stdin.close()
    assert not (OUT / (name + '.stdout.txt')).read_bytes()


OUT.mkdir(parents=True, exist_ok=False)
snapshot = OLD / 'baseline/snapshot.stdout.txt'
baseline_sha = '646ed950ea5d54be77ea3ba83f3af31eb181a26ab52dc0dbc01ec2d9ab3048bf'
jar = ARTIFACT / 'kernel.jar'
assert sha(snapshot) == baseline_sha
assert sha(jar) == '7d4345f1f79c7b6eeb75a9dc1702308a0162f72faa4d4cbf6c3987d77b65d1fc'
captures = [(40, '97ffa0ece0e233092bdfad4fb5c0c896bd591648b0295cc42b485d71754359fe'),
            (41, '36732c64aa8b20280d14d0ae4fab6dcaa795029746cf6d7ef65446ed71d23f8f'),
            (42, 'bb9ce199f50ad1fc07ed5b6e802215b3c561cd38d948ea8bcf37e0f86fb49116')]
inputs = [{'path': str(snapshot), 'sha256': baseline_sha}, {'path': str(jar), 'sha256': sha(jar)}]
capture_args = []
for sequence, digest in captures:
    path = OLD / 'capture' / ('sequence-' + str(sequence) + '.oracap')
    assert sha(path) == digest
    inputs.append({'path': str(path), 'sha256': digest})
    capture_args.extend([path, digest])
run('raw-capture-buffered-rows', [JAVA, '-cp', str(jar) + ';' + str(ARTIFACT / 'classes'),
                                 'CapturedRowsProbe', 'rows', snapshot, baseline_sha, '0x0300019b'] + capture_args)
rows_path = OUT / 'raw-capture-buffered-rows.stdout.txt'
assert rows_path.read_bytes() == (ARTIFACT / 'capture-rows.stdout.txt').read_bytes()
inputs.append({'path': str(rows_path), 'sha256': sha(rows_path)})
source = next(line for line in snapshot.read_text().splitlines() if line.startswith('SOURCE|'))
BASE = {'source': source + '|PDB|677737149|3', 'baseline_sha256': baseline_sha,
        'rows_path': str(rows_path), 'inputs': inputs}
baseline = next(line for line in snapshot.read_text().splitlines() if line.startswith('ROW|')).split('|')
ROW_KEY = ':'.join([baseline[1], str((int(baseline[2]) << 22) | int(baseline[3])), baseline[4]])
BASELINE_CELLS = ['VALUE:' + value.lower() for value in baseline[5:]]
summaries = []
for stage in ('normal', 'before-commit', 'after-commit', 'after-ack'):
    spec = spec_for(stage)
    target, ack = initialize(stage, spec)
    args = command(stage, spec, target, ack)
    if stage == 'normal':
        assert json.loads(run(stage, args).stdout)['new_applications'] == 3
    else:
        crash(stage + '-kill', args, stage, dual=stage == 'before-commit')
        interrupted = state(stage + '-interrupted', target, ack)
        expected = 0 if stage == 'before-commit' else 3
        assert len(interrupted['applications']) == expected
        assert len(interrupted['commits']) == bool(expected)
        assert interrupted['ack_exists'] == (stage == 'after-ack')
        resumed = json.loads(run(stage + '-resume', args).stdout)
        assert resumed['new_applications'] == (3 if stage == 'before-commit' else 0)
    result = state(stage + '-final', target, ack)
    assert len(result['applications']) == 3 and len(result['commits']) == 1 and result['ack_exists']
    assert result['rows'] == [(ROW_KEY, encoded(BASELINE_CELLS).decode())]
    ack_before = ack.read_bytes()
    assert json.loads(run(stage + '-repeat', args).stdout)['new_applications'] == 0
    assert ack.read_bytes() == ack_before
    summaries.append({'case': stage, 'applications': 3, 'commits': 1, 'repeat_new_applications': 0})

# Late before-image fault: first two target updates happen but the entire SQL
# transaction must roll back. This is a laboratory artifact mutation, not raw redo.
spec = spec_for('late-before')
bad_rows = OUT / 'late-before.rows.txt'
lines = rows_path.read_text().splitlines()
p = lines[-1].split('|'); cells = p[7].split(','); cells[1] = 'VALUE:00'; p[7] = ','.join(cells)
lines[-1] = '|'.join(p); bad_rows.write_text('\n'.join(lines) + '\n')
spec['rows_path'] = str(bad_rows)
spec['inputs'][-1] = {'path': str(bad_rows), 'sha256': sha(bad_rows)}
target, ack = initialize('late-before', spec)
run('late-before', command('late-before', spec, target, ack), 'CONSUMER_TARGET_BEFORE_MISMATCH')
result = state('late-before-final', target, ack)
assert not result['applications'] and not result['commits'] and not result['ack_exists']
assert result['rows'] == [(ROW_KEY, encoded(BASELINE_CELLS).decode())]

# Valid-looking ACK copied from an actually committed identical batch into a fresh
# target with the same logical identity must never replace the target commit proof.
spec = spec_for('normal'); target, ack = initialize('ack-without-commit', spec)
ack.write_bytes((OUT / 'normal.ack.json').read_bytes())
run('ack-without-commit', command('ack-without-commit', spec, target, ack), 'CONSUMER_ACK_WITHOUT_TARGET_COMMIT')
result = state('ack-without-commit-final', target, ack)
assert not result['applications'] and not result['commits']

for name, reason in [('wrong-target', 'CONSUMER_TARGET_IDENTITY_MISMATCH'),
                     ('wrong-spec-sha', 'CONSUMER_SPEC_DIGEST_MISMATCH'),
                     ('wrong-input-sha', 'CONSUMER_INPUT_DIGEST_MISMATCH')]:
    spec = spec_for(name); target, ack = initialize(name, spec)
    if name == 'wrong-target': spec['target_id'] += '-foreign'
    if name == 'wrong-input-sha': spec['inputs'][0]['sha256'] = '0' * 64
    args = command(name, spec, target, ack)
    if name == 'wrong-spec-sha': args[3] = '0' * 64
    run(name, args, reason)
    result = state(name + '-final', target, ack)
    assert not result['applications'] and not result['commits'] and not ack.exists()

target, ack = OUT / 'normal.sqlite', OUT / 'normal.ack.json'
ack_original = ack.read_bytes()
wrong_ack = json.loads(ack_original); wrong_ack['events'] = 4
ack.write_bytes(encoded(wrong_ack))
run('ack-conflict', [sys.executable, WORKER, OUT / 'normal.spec.json', sha(OUT / 'normal.spec.json'), target, ack],
    'CONSUMER_ACK_CONFLICT')
assert ack.read_bytes() == encoded(wrong_ack)
# Preserve the conflicting ACK and restore only this owned test copy for the next fault.
(OUT / 'ack-conflict.retained.json').write_bytes(ack.read_bytes()); ack.write_bytes(ack_original)
with sqlite3.connect(target) as conn:
    conn.execute("UPDATE applications SET payload='{}' WHERE ordinal=2")
run('target-proof-conflict', [sys.executable, WORKER, OUT / 'normal.spec.json', sha(OUT / 'normal.spec.json'), target, ack],
    'CONSUMER_TARGET_APPLICATION_PROOF_MISMATCH')
assert ack.read_bytes() == ack_original
state('target-proof-conflict-final', target, ack)
proof = {'cases': summaries, 'actual_process_kills': 3, 'consumer_refusals': 8,
         'real_target': 'owned local Windows SQLite WAL/FULL; cells preserve Oracle raw hex',
         'source_actions': 0, 'kernel_rebuilt': False, 'public_events_enabled': False,
         'streamhouse_integration_accepted': False, 'power_loss_accepted': False,
         'worker_sha256': sha(WORKER), 'driver_sha256': sha(Path(__file__))}
(OUT / 'proof.json').write_bytes(encoded(proof))
print(json.dumps(proof, indent=2))
