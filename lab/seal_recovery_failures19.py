"""Seal actual product recovery refusal, atomic rollback and ACK-only recovery."""
import hashlib, json, shutil
from pathlib import Path

R = Path(__file__).resolve().parent.parent
N = R / 'lab/out/recovery-failures-20261007'
D = R / 'docs/workorders/evidence/DELIVERY-recovery-failures-20261007.json'
assert not D.exists()

def sha(path):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()

previous = []
for name in ['DELIVERY-recovery-http-20261007.json', 'DELIVERY-product-recovery-binding-20261007.json',
             'DELIVERY-unified-recovery-20261007.json', 'DELIVERY-split-history-product-20261007.json']:
    manifest = json.loads((D.parent / name).read_text())
    for item in manifest['files']:
        path = R / item['path']
        assert sha(path) == item['sha256'] and path.stat().st_size == item['bytes']
    previous.append(dict(manifest=name, files=len(manifest['files']), unchanged=True))
stages = ['missing-state', 'mixed-state', 'late-state', 'ack-blocked-state', 'ack-recovered-state']
proofs = [json.loads((N / (stage + '-proof.json')).read_text()) for stage in stages]
assert [p['events'] for p in proofs] == [0, 0, 2, 2, 2]
for label in ['missing', 'mixed', 'late', 'ack-blocked', 'ack-recovered']:
    assert (N / ('verify-' + label + '-verification.rc.txt')).read_text() == '0'
closure = json.loads((N / 'ack-recovery-no-rewrite-proof.json').read_text())
assert closure['new_batches'] == 0 and closure['target_ledger_members_commit_rows_checkpoint_batches_exact_unchanged']
for item in closure['files']:
    assert sha(N / item['path']) == item['sha256']
assert (N / 'stop-state.stdout.txt').read_text().strip() == 'false|143|exited'
source = (N / 'final-source.stdout.txt').read_text()
assert all(v in source for v in ['LOGGING|NO|NO', 'OLD|11|blocking-closed|new-note', 'BASE|0|history-product-restored|anchor', 'META_EQUAL|1'])
approved = json.loads((N / 'approved-binding.json').read_text())
assert approved['acquisitionId'] == 100747510573780992 and sha(N / 'recovery.state') == approved['recovery']['checkpointSha256']
mixed = json.loads((N / 'mixed-approved-binding.json').read_text())
assert sha(N / 'recovery.mixed.state') == mixed['recovery']['checkpointSha256']
product = json.loads((R / 'lab/out/product-recovery-binding-20261007/product-proof.json').read_text())
assert (N / 'health-ready-sha.stdout.txt').read_text().split()[0] == product['product_sha256']
for name in ['recovery_failures19.py', 'verify_recovery_failures19.py', 'verify_recovery_ack_closure19.py', 'seal_recovery_failures19.py']:
    shutil.copyfile(R / 'lab' / name, N / name)
files = []
for path in sorted(N.rglob('*')):
    if not path.is_file() or 'secret' in path.name.lower():
        continue
    digest = sha(path)
    assert sha(path) == digest
    files.append(dict(path=path.relative_to(R).as_posix(), bytes=path.stat().st_size, sha256=digest))
out = dict(product_sha256=product['product_sha256'], product_tests=product['tests'],
           approved_recovery=approved['recovery'], mixed_recovery=mixed['recovery'], proofs=proofs,
           ack_only_recovery=closure, previous_capsules=previous, source_business_actions=0,
           new_acquisitions=0, source_configuration_changes=0,
           target_scope='owned same-instance cross-schema; not independent target', helper_running=False,
           independent_review='PENDING', production_deployed=False, site_accepted=False, released=False,
           development_D1_closed=False, active_product_cross_publication_recovery_verified=False,
           files_count=len(files), double_sha256_verified=True, files=files)
D.write_text(json.dumps(out, indent=2))
for item in files:
    assert sha(R / item['path']) == item['sha256']
print(json.dumps(dict(files=len(files), stages=stages, product=product['product_sha256'])))
