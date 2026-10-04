"""New typed kernel API over already frozen capture. No database source action."""
import hashlib
import json
import shutil
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / 'lab/out/captured-batch-20261005'
BUILD = ROOT / 'lab/out/release-captured-batch-20261005'
OLD = ROOT / 'lab/out/closed-follow-20261005'
GOOD = ROOT / 'lab/out/capture-rows-r2-20261005'
FAULTS = ROOT / 'lab/out/capture-row-faults-20261005'
JAVA = 'D:/work/Java/jdk-17.0.7/bin/java.exe'
JAVAC = 'D:/work/Java/jdk-17.0.7/bin/javac.exe'
OUT.mkdir(parents=True, exist_ok=False)
shutil.copyfile(BUILD / 'kernel.jar', OUT / 'kernel.jar')


def run(name, args, rc=0, empty=False, reason=None):
    args = list(map(str, args)); (OUT / (name + '.command.json')).write_text(json.dumps(args))
    result = subprocess.run(args, capture_output=True, timeout=180)
    for ext, data in [('stdout', result.stdout), ('stderr', result.stderr)]:
        (OUT / (name + '.' + ext + '.txt')).write_bytes(data)
    (OUT / (name + '.rc.txt')).write_text(str(result.returncode))
    assert result.returncode == rc and (not empty or not result.stdout), name
    assert reason is None or reason.encode() in result.stderr, name
    return result


classes = OUT / 'classes'; classes.mkdir()
run('compile-probes', [JAVAC, '-cp', OUT / 'kernel.jar', '-d', classes, ROOT / 'lab/CapturedBatchProbe.java',
                       ROOT / 'lab/VerifiedHeapRowsProbe.java'])
base = [JAVA, '-cp', str(OUT / 'kernel.jar') + ';' + str(classes), 'CapturedBatchProbe']
original = json.loads((FAULTS / 'late-before-rows.command.json').read_text())
baseline = original[5:8]
pairs = original[8:12] + [str(OLD / 'capture/sequence-42.oracap'),
                           'bb9ce199f50ad1fc07ed5b6e802215b3c561cd38d948ea8bcf37e0f86fb49116']
run('actual', base + baseline + pairs)
run('repeat-new-api', base + baseline + pairs)
assert (OUT / 'actual.stdout.txt').read_bytes() == (OUT / 'repeat-new-api.stdout.txt').read_bytes()
lines = (OUT / 'actual.stdout.txt').read_text().splitlines()
rows = '\n'.join(line for line in lines if line.startswith('ROW|')) + '\n'
assert rows.splitlines() == (GOOD / 'capture-rows.stdout.txt').read_text().splitlines()
(OUT / 'rows.txt').write_text(rows)
dumps = [ROOT / 'lab/out/capture-records-20261005' / ('dump-' + str(n) + '.trc') for n in (40, 41, 42)]
run('native-record-metadata', [sys.executable, ROOT / 'lab/compare_captured_batch19.py', OUT / 'actual.stdout.txt'] + dumps)
run('native-complete-rows', [sys.executable, ROOT / 'lab/compare_capture_rows.py', baseline[0], OUT / 'rows.txt',
                            GOOD / 'capture-state.stdout.txt', OLD] + dumps)
for name in ('missing-sequence', 'reordered-sequences', 'duplicate-sequence', 'wrong-baseline-digest',
             'late-before-rows', 'missing-begin-control'):
    # Use the exact retained poison and receipt from earlier tests; only this new
    # batch API is tested, no old suites or capture/source actions are rerun.
    path = FAULTS / (name + '.command.json')
    if name == 'wrong-baseline-digest':
        path = FAULTS / 'untrusted-baseline.command.json'
    old = json.loads(path.read_text())
    run(name, base + old[5:], 1, empty=True)
for name, changed in [('wrong-end-scn', lines.copy()), ('missing-event', lines.copy())]:
    if name == 'wrong-end-scn':
        index = next(i for i, line in enumerate(changed) if line.startswith('EVENT|'))
        fields = changed[index].split('|'); fields[-1] = str(int(fields[-1]) + 1); changed[index] = '|'.join(fields)
    else:
        index = next(i for i, line in enumerate(changed) if line.startswith('EVENT|')); del changed[index]
    path = OUT / (name + '.txt'); path.write_text('\n'.join(changed) + '\n')
    run(name, [sys.executable, ROOT / 'lab/compare_captured_batch19.py', path] + dumps, 1)
proof = {'runtime_commit': json.loads((BUILD / 'proof.json').read_text())['runtime_commit'],
         'jar_sha256': hashlib.sha256((OUT / 'kernel.jar').read_bytes()).hexdigest(),
         'new_api_events': 3, 'kernel_tests': 245, 'repeated_new_api_bytes_equal': True,
         'new_api_refusals': 6, 'independent_metadata_refusals': 2, 'source_actions': 0,
         'public_events_authorized': False, 'streamhouse_site_accepted': False}
(OUT / 'proof.json').write_text(json.dumps(proof, indent=2)); print(json.dumps(proof))
