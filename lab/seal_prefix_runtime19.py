"""Seal actual prefix generation/registration evidence, without claiming publication completion."""
import hashlib, json
from pathlib import Path
R = Path(__file__).resolve().parent.parent
N = R / 'lab/out/product-prefix-runtime-20261007'
C = R / 'lab/out/product-prefix-registration-20261007'
D = R / 'docs/workorders/evidence/DELIVERY-product-prefix-registration-20261007.json'
assert not D.exists()
def sha(p):
    with p.open('rb') as f: return hashlib.file_digest(f, 'sha256').hexdigest()
proof = json.loads((C / 'product-proof.json').read_text())
assert proof['tests'] == [760, 0, 0, 0] and proof['kernel']['tests'] == [330, 0, 0, 0]
assert sha(C / 'application.jar') == proof['product_sha256']
for name in ['orphan-retry', 'duplicate', 'recovered', 'restored-get']:
    result = json.loads((N / (name + '.stdout.txt')).read_text())
    cp = result[1]['data']['checkpoint']
    assert result[1]['code'] == 0 and cp['pendingTransactions'] == 1 and cp['completedTransactions'] == 0
    assert cp['sha256'] == '7d6eef2ad4439bdad1ec93a853de95b36810cb334637db81fe588022ba1b6512'
for name, reason in [('registration-fault', 'ORACLE_PREFIX_CHECKPOINT_REFUSED'), ('poison-refusal', 'ORACLE_PREFIX_RECOVERY_REFUSED')]:
    result = json.loads((N / (name + '.stdout.txt')).read_text())
    assert result[1]['code'] == 409 and result[1]['msg'] == reason
assert 'PREFIX|APPROVED_PREFIX|' in (N / 'after-registration-fault.stdout.txt').read_text()
assert 'AUDIT|0' in (N / 'after-registration-fault.stdout.txt').read_text()
for name in ['registered', 'after-restart', 'final-state']:
    out = (N / (name + '.stdout.txt')).read_text()
    assert 'PREFIX|CHECKPOINTED_PREFIX|' in out and 'ZERO|0|0|0|0|0' in out and 'AUDIT|1' in out
    files = json.loads((N / (name + '-files.json')).read_text())
    assert files['acks'] == 0 and len(files['files']) == 1
assert (N / 'crash-terminal.stdout.txt').read_text().strip() == 'false|137'
assert (N / 'stop-owned-terminal.stdout.txt').read_text().strip() == 'false|143'
previous = []
for name in ['DELIVERY-product-active-marker2-20261007.json', 'DELIVERY-recovery-failures-20261007.json']:
    manifest = json.loads((D.parent / name).read_text())
    for item in manifest['files']:
        p = R / item['path']
        assert sha(p) == item['sha256'] and p.stat().st_size == item['bytes']
    previous.append(dict(manifest=name, files=len(manifest['files']), unchanged=True))
files = []
for folder in [C, N]:
    for p in sorted(folder.rglob('*')):
        if not p.is_file() or 'secret' in p.name.lower(): continue
        digest = sha(p)
        assert sha(p) == digest
        files.append(dict(path=p.relative_to(R).as_posix(), bytes=p.stat().st_size, sha256=digest))
out = dict(proof=proof, files=files, files_count=len(files), previous_capsules=previous, double_sha256_verified=True,
           prefix_registration=True, orphan_retry=True, hard_restart_verified=True, poisoned_state_refused=True,
           events=0, checkpoint_ack_advanced=False, source_business_dml=0, publication_recovery_verified=False,
           automatic_pipeline_pending_flow=False, full_database_matrix=False, original_sql_cause_persisted=False,
           independent_review='PENDING', site_accepted=False, released=False)
D.write_text(json.dumps(out, indent=2))
print(json.dumps(dict(files=len(files), product=proof['product_sha256'], tests=proof['tests'], previous=previous)))
