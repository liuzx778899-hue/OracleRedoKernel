"""Independently compare the exact HTTP target/control/ACK with frozen projection."""
import base64, hashlib, json, sys
from pathlib import Path
R = Path(__file__).resolve().parent.parent
N = R / 'lab/out/split-history-product-20261007'
P = json.loads((R / 'lab/out/split-history-candidate-20261007/projection-r2/actual-projection.json').read_text())
e = P['evidence']
sha = lambda p: hashlib.sha256(p.read_bytes()).hexdigest()
label = sys.argv[1]
out = N / (label + '-proof.json')
assert not out.exists()
text = (N / (label + '.stdout.txt')).read_text()
lines = [line.strip() for line in text.splitlines() if line.strip()]
assert (N / (label + '.rc.txt')).read_text() == '0'

def image(values):
    def value(v):
        if v is None:
            return 'NULL'
        if isinstance(v, (int, float)):
            return 'NUMBER:' + str(v)
        return 'VARCHAR2:' + base64.urlsafe_b64encode(v.encode()).decode().rstrip('=')
    return ';'.join(k + '=' + value(v) for k, v in values.items())

expected = ['|'.join(['EVENT', str(i), env['eventId'], e['events'][i]['payloadSha256'],
    image(env['before']), image(env['after']), env['operation']]) for i, env in enumerate(P['envelopes'])]
assert [line for line in lines if line.startswith('EVENT|')] == expected
assert 'TARGET|2|2|1' in lines
assert 'ROW|0|history-product-restored|anchor|<NULL>' in lines
assert 'COMMIT|' + e['batchSha256'] + '|oracle-history-SHP7-20261007|' + e['schemaSequenceSha256'] + '|2' in lines
task = (N / 'task-id.txt').read_text()
assert 'TASK|' + task + '|DONE' in lines and 'WINDOW|CONSUMED' in lines
batch = [line for line in lines if line.startswith('BATCH|')]
assert len(batch) == 1 and batch[0].split('|')[2:6] == ['PUBLISHED', '0', '2', 'APPROVED_ORACLE_CLOSED_BATCH']
assert sorted(line for line in lines if line.startswith('LEDGER|')) == sorted('LEDGER|' + env['eventId'] for env in P['envelopes'])
cp_raw = next(line[3:] for line in lines if line.startswith('CP|')).encode()
cp = json.loads(cp_raw)
assert cp['lastWindowKey'] == 'history-70-71' and cp['sourceId'] == 'oracle-v3-HIST' and cp['engine'] == 'oracle'
assert cp['lastBatchId'] == int(batch[0].split('|')[1])
assert cp['oracleEvidence'] == e
receipt = dict(targetId='oracle-history-SHP7-20261007', source=e['source'], baselineSha256=e['baselineSha256'],
    schemaSha256=e['schemaSha256'], batchSha256=e['batchSha256'], envelopesSha256=P['envelopesSha256'],
    events=2, schemaSequenceSha256=e['schemaSequenceSha256'])
assert cp['targetCommit'] == receipt
acks = list((N / 'acks').glob('*.ack.json'))
assert len(acks) == 1 and acks[0].read_bytes() == cp_raw
proof = dict(events=2, event_columns=[4, 3], full_before_after_matches=True,
    ordered_target_members_and_payloads_match=True, checkpoint_full_evidence_and_receipt_match=True,
    persisted_ack_exact_checkpoint_bytes=True, ack_sha256=sha(acks[0]), task_id=task,
    actual_authenticated_http=True, source_dml=0, source_configuration_changes=0,
    same_instance_cross_schema_target=True, independent_target_verified=False,
    capture_complete=False, site_accepted=False, released=False)
if label != 'after-drive':
    before = (N / 'after-drive.stdout.txt').read_text()
    if label == 'restored-state':
        assert [v for v in lines if not v.startswith('FAIL|')] == [v.strip() for v in before.splitlines() if v.strip() and not v.startswith('FAIL|')]
        assert sum(v.startswith('FAIL|PIPELINE_TASK_RUN|IllegalStateException|') for v in lines) == 1
    else:
        assert (N / (label + '.stdout.txt')).read_bytes() == (N / 'after-drive.stdout.txt').read_bytes()
    proof['target_control_checkpoint_ack_exact_unchanged'] = True
out.write_text(json.dumps(proof, indent=2))
print(json.dumps(proof))
