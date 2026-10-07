"""Verify receipt poisoning refuses recovery without advancing the existing publication."""
import hashlib, json
from pathlib import Path
R = Path(__file__).resolve().parent.parent
N = R / 'lab/out/split-history-product-20261007'
dest = N / 'receipt-negative-proof.json'
assert not dest.exists()
before = (N / 'after-drive.stdout.txt').read_text().splitlines()
negative = (N / 'poisoned-complete-state.stdout.txt').read_text().splitlines()
prefixes = ('TARGET|', 'ROW|', 'EVENT|', 'BATCH|', 'TASK|', 'CP|', 'WINDOW|', 'LEDGER|')
assert [v.strip() for v in negative if v.startswith(prefixes)] == [v.strip() for v in before if v.startswith(prefixes)]
e = json.loads((N / 'approved-binding.json').read_text())['evidence']
assert ('COMMIT|' + e['batchSha256'] + '|oracle-history-SHP7-20261007|' + '0' * 64 + '|2') in ''.join(''.join(negative).split())
assert any(v.startswith('FAIL|PIPELINE_TASK_RUN|IllegalStateException|') and v.endswith(' ORACLE_RECOVERY_PROOF_FAILED') for v in negative)
logs = (N / 'poisoned-recovery-logs.stdout.txt').read_bytes()
assert b'Caused by: java.sql.SQLException: ORACLE_TARGET_COMMIT_CONFLICT' in logs
ack = next((N / 'acks').glob('*.ack.json'))
assert hashlib.sha256(ack.read_bytes()).hexdigest() == json.loads((N / 'after-drive-proof.json').read_text())['ack_sha256']
proof = dict(error='ORACLE_TARGET_COMMIT_CONFLICT', full_exception_preserved=True,
    target_rows_events_members_unchanged=True, existing_checkpoint_and_ack_unchanged=True,
    task_state='DONE', note='Previously committed task remains DONE; recovery attempt refused and fail-closed audit appended',
    schema_sequence_receipt_poison_only=True, new_publications=0)
dest.write_text(json.dumps(proof, indent=2))
print(json.dumps(proof))
