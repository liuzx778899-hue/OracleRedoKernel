"""Offline full-event verification of frozen raw108; SQL truth remains an independent ruler."""
from pathlib import Path
import hashlib, json, subprocess, sys

ROOT = Path(__file__).resolve().parent.parent
BASE = ROOT / 'lab/out/array-rollback-20261011'
OUT = BASE / sys.argv[1]
OUT.mkdir(parents=True, exist_ok=False)
JAVA = Path('D:/work/Java/jdk-17.0.7/bin')
KERNEL = ROOT / 'target/oracle-redo-kernel-0.1.0-SNAPSHOT.jar'
LIBS = ROOT / 'lab/out/array-normal-20261010/target-r1/libs'
CP = str(KERNEL) + ';' + str(LIBS / '*')
sha = lambda p: hashlib.sha256(Path(p).read_bytes()).hexdigest()

def run(name, command):
    result = subprocess.run([str(x) for x in command], cwd=ROOT, capture_output=True)
    (OUT / (name + '.stdout')).write_bytes(result.stdout)
    (OUT / (name + '.stderr')).write_bytes(result.stderr)
    (OUT / (name + '.rc')).write_text(str(result.returncode) + '\n')
    assert result.returncode == 0, name + ': inspect preserved stderr'
    return result.stdout

acquisition = json.loads((BASE / 'source/acquisition.json').read_text())
snapshot = BASE / 'source/snapshots' / (acquisition['id'] + '.snapshot')
raw = BASE / 'source/inputs/108.raw'
assert sha(raw) == '716ac017f4a5c6c5c034046830fc820eac089e58fbb277bc3cfecd7f70232ecb'
assert sha(snapshot) == acquisition['snapshotSha256']
run('compile', [JAVA / 'javac.exe', '-cp', CP, '-d', OUT, ROOT / 'lab/ArrayNormalProbe.java'])
projection = json.loads(run('projection', [JAVA / 'java.exe', '-Xmx1024m', '-cp', str(OUT) + ';' + CP,
    'ArrayNormalProbe', snapshot, acquisition['snapshotSha256'], raw]))
events = projection['events']
truth = json.loads((BASE / 'source/truth.json').read_text())
expected = {int(r[0]): {'ID': int(r[0]), 'TXT': r[1], 'NOTE': r[2]} for r in truth if int(r[0]) != 1}
actual = {int(e['after']['ID']): e['after'] for e in events}
assert len(events) == len(actual) == len(expected) == 20
assert actual == expected and set(actual) == set(range(200, 220))
assert len({e['eventId'] for e in events}) == 20
assert all(e['operation'] == 'INSERT' and e['before'] is None
    and e['metadata']['beforeComplete'] and e['metadata']['afterComplete'] for e in events)
assert [e['transaction']['sequence'] for e in events] == list(range(1, 21))
assert len({e['transaction']['transactionId'] for e in events}) == 1
proof = dict(rawSha256=sha(raw), baselineSha256=sha(snapshot), kernelSha256=sha(KERNEL),
    events=20, uniqueEventIds=20, completeRowDifferences=0, rolledBackEvents=0,
    envelopesSha256=projection['envelopesSha256'], fullTransactionAndSavepointRollback=True,
    partialArrayNodeCompensation=False, pendingRecoveryVerified=False, productIntegrated=False,
    sqlTruthUsedAsEventInput=False, nativeUsedAsEventInput=False)
(OUT / 'proof.json').write_text(json.dumps(proof, indent=2) + '\n')
print(json.dumps(proof))
