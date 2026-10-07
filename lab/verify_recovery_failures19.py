"""Check binding refusal, atomic late failure, committed ACK failure and recovery independently."""
import base64, hashlib, json, sys
from pathlib import Path
R = Path(__file__).resolve().parent.parent
N = R / 'lab/out/recovery-failures-20261007'
P = json.loads((R / 'lab/out/split-history-candidate-20261007/projection-r2/actual-projection.json').read_text())
e = P['evidence']
label = sys.argv[1]
out = N / (label + '-proof.json')
assert not out.exists()
text = (N / (label + '.stdout.txt')).read_text(encoding='utf-8')
lines = [v.strip() for v in text.splitlines() if v.strip()]
task = (N / 'task-id.txt').read_text()
acks = list((N / 'acks').glob('*.ack.json')) if (N / 'acks').is_dir() else []
batches = [v.split('|') for v in lines if v.startswith('BATCH|')]
proof = dict(stage=label, task_id=task, actual_authenticated_http=True, source_fixture_actions=0)

def image(values):
    def value(v):
        if v is None:
            return 'NULL'
        if isinstance(v, (int, float)):
            return 'NUMBER:' + str(v)
        return 'VARCHAR2:' + base64.urlsafe_b64encode(v.encode()).decode().rstrip('=')
    return ';'.join(k + '=' + value(v) for k, v in values.items())

if label in ['missing-state', 'mixed-state', 'late-state']:
    assert 'TARGET|0|0|0' in lines and 'ROW|0|history-v3-restored|anchor|<NULL>' in lines
    assert 'TASK|' + task + '|FAILED' in lines
    assert 'CP|' in lines and 'WINDOW|PENDING' in lines and 'GATES|0' in lines
    assert not any(v.startswith(('EVENT|', 'LEDGER|', 'COMMIT|')) for v in lines)
    assert not acks
    if label in ['missing-state', 'mixed-state']:
        assert batches[-1][2:5] == ['REFUSED', '2', '0']
        if label == 'missing-state':
            assert batches[-1][5] == 'ORACLE_BINDING_REFUSED: /evidence/recovery.state'
        else:
            assert batches[-1][5].startswith('ORACLE_APPROVED_INPUT_REFUSED: ')
            assert 'RECOVERY' in batches[-1][5]
            registered = json.loads((N / 'mixed-approved-binding.json').read_text())['recovery']['checkpointSha256']
            assert hashlib.sha256((N / 'recovery.state').read_bytes()).hexdigest() == registered
        proof.update(events=0, fail_closed_before_target=True, reason=batches[-1][5])
    else:
        assert batches[-1][2:5] == ['GATED', '0', '2']
        artifacts = list((N / 'failures').rglob('*.applyfail.txt'))
        assert len(artifacts) == 1
        failure = artifacts[0].read_text(encoding='utf-8')
        assert 'ORA-02290' in failure and 'SHRF7_LATE_CK' in failure and 'Caused by:' in failure
        assert 'ORACLE_JDBC_TARGET_APPLY_FAILED' in failure
        assert str(batches[-1][1]) in failure
        proof.update(events=2, full_database_error='ORA-02290 SHRF7_LATE_CK',
            target_rows_and_all_ledgers_rolled_back=True, control_gate_checkpoint_ack_unadvanced=True,
            preceding_update_inferred_from_second_full_before_match=True,
            failure_sha256=hashlib.sha256(artifacts[0].read_bytes()).hexdigest())
else:
    assert 'TARGET|2|2|1' in lines and 'ROW|0|history-product-restored|anchor|<NULL>' in lines
    assert 'WINDOW|CONSUMED' in lines and 'GATES|1' in lines
    expected = ['|'.join(['EVENT', str(i), env['eventId'], e['events'][i]['payloadSha256'], image(env['before']), image(env['after']), env['operation']]) for i, env in enumerate(P['envelopes'])]
    assert [v for v in lines if v.startswith('EVENT|')] == expected
    assert sorted(v for v in lines if v.startswith('LEDGER|')) == sorted('LEDGER|' + v['eventId'] for v in P['envelopes'])
    assert 'COMMIT|' + e['batchSha256'] + '|oracle-history-SHRF7-20261007|' + e['schemaSequenceSha256'] + '|2' in lines
    published = [b for b in batches if b[2] == 'PUBLISHED']
    assert len(published) == 1 and published[0][3:5] == ['0', '2']
    cp_raw = next(v[3:] for v in lines if v.startswith('CP|')).encode()
    cp = json.loads(cp_raw)
    assert cp['lastWindowKey'] == 'history-70-71-recovery-failures' and cp['sourceId'] == 'oracle-v3-HIST' and cp['engine'] == 'oracle'
    assert cp['lastBatchId'] == int(published[0][1]) and cp['oracleEvidence'] == e
    expected_receipt = dict(targetId='oracle-history-SHRF7-20261007', source=e['source'], baselineSha256=e['baselineSha256'],
        schemaSha256=e['schemaSha256'], batchSha256=e['batchSha256'], envelopesSha256=P['envelopesSha256'], events=2, schemaSequenceSha256=e['schemaSequenceSha256'])
    assert cp['targetCommit'] == expected_receipt
    proof.update(events=2, ordered_events_members_receipt_exact=True, checkpoint_full_evidence_exact=True,
        published_batch_state_preserved=True, published_batch_id=published[0][1])
    if label == 'ack-blocked-state':
        assert 'TASK|' + task + '|FAILED' in lines and not acks
        assert (N / 'acks').is_file()
        assert any('ORACLE_ACK_PENDING' in v for v in lines if v.startswith('FAIL|'))
        files = list((N / 'failures').rglob('*.applyfail.txt'))
        assert len(files) == 2
        failures = [f.read_text(encoding='utf-8') for f in files]
        assert any('ORACLE_ACK_PENDING' in v and 'FileAlreadyExistsException' in v for v in failures)
        proof.update(target_and_control_committed=True, persistent_ack_files=0, full_ack_exception_preserved=True)
    else:
        assert 'TASK|' + task + '|DONE' in lines and len(acks) == 1 and acks[0].read_bytes() == cp_raw
        proof['ack_sha256'] = hashlib.sha256(acks[0].read_bytes()).hexdigest()
out.write_text(json.dumps(proof, indent=2))
print(json.dumps(proof))
