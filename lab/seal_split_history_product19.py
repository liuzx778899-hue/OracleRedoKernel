"""Seal the owned historical product chain and verify prior immutable capsules."""
import hashlib, json, shutil, subprocess
from pathlib import Path
R = Path(__file__).resolve().parent.parent
N = R / 'lab/out/split-history-product-20261007'
P = R / 'lab/out/streamhouse-oracle-gate-worktree'
D = R / 'docs/workorders/evidence/DELIVERY-split-history-product-20261007.json'
assert not D.exists()

def sha(path):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()

prior = []
for name in ['DELIVERY-split-history-20261007.json', 'DELIVERY-autonomous-dictionary-split-20261006.json',
             'DELIVERY-v3-product-acquisition-20261006.json']:
    file = D.parent / name
    manifest = json.loads(file.read_text(encoding='utf-8'))
    for item in manifest['files']:
        path = R / item['path']
        assert path.stat().st_size == item['bytes'] and sha(path) == item['sha256'], item['path']
    prior.append(dict(manifest=file.relative_to(R).as_posix(), files=len(manifest['files']), unchanged=True))
build = json.loads((N / 'product-proof.json').read_text())
adapter = json.loads((N / 'adapter-proof.json').read_text())
assert build['tests'] == [750, 0, 0, 0] and adapter['tests'] == [26, 0, 0, 0]
positive = json.loads((N / 'after-drive-proof.json').read_text())
negative = json.loads((N / 'receipt-negative-proof.json').read_text())
restored = json.loads((N / 'restored-state-proof.json').read_text())
assert positive['persisted_ack_exact_checkpoint_bytes'] and restored['target_control_checkpoint_ack_exact_unchanged']
assert negative['new_publications'] == 0
assert (N / 'stop-state.stdout.txt').read_text().strip() == 'false|143|exited'
source = (N / 'final-source.stdout.txt').read_text()
assert all(value in source for value in ['LOGGING|NO|NO', 'OLD|11|blocking-closed|new-note', 'BASE|0|history-product-restored|anchor', 'ACQ|100747510573780992|ACQUIRED_ONLY|323fd35a8ceee43545c28a9fb32f55cedd41409a3e1b5fb63e08606380cd24a2|2566695'])
for label in ['duplicate-completion', 'restart-completion', 'restored-completion']:
    last = (N / (label + '.stdout.txt')).read_text().strip().splitlines()[-1]
    assert last.endswith('|pipeline.task.done|state=DONE windows=0 published=0'), label
for name in ['build_split_history_product19.py', 'split_history_product_runtime19.py',
             'verify_split_history_product19.py', 'verify_split_history_product_negative19.py', 'seal_split_history_product19.py']:
    shutil.copyfile(R / 'lab' / name, N / ('sealed-' + name))
files = []
for path in sorted(N.rglob('*')):
    if not path.is_file() or 'secret' in path.name.lower() or path.suffix == '.lock':
        continue
    digest = sha(path)
    assert sha(path) == digest
    files.append(dict(path=path.relative_to(R).as_posix(), bytes=path.stat().st_size, sha256=digest))
out = dict(product_code_commit=subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=P).decode().strip(),
    build=build, adapter=adapter, positive=positive, duplicate=json.loads((N / 'duplicate-state-proof.json').read_text()),
    restart=json.loads((N / 'restart-state-proof.json').read_text()), receipt_negative=negative, restored=restored,
    historical_product_http=True, target_checkpoint_ack=True, source_actions=0, source_configuration_changes=0,
    helper='false|143|exited', old_helper_stopped=True, previous_capsules=prior,
    failure_dispositions=dict(receipt='Expected owned poison; full conflict exception/audit kept, exact schema commitment restored',
        comparator='First comparator used Windows default GBK on UTF-8 product log; script/error preserved, ASCII error matched as bytes without re-running HTTP/Java'),
    capture_complete=False, independent_target_verified=False, independent_review='PENDING',
    production_deployed=False, site_accepted=False, released=False,
    files_count=len(files), double_sha256_verified=True, files=files)
D.write_text(json.dumps(out, indent=2), encoding='utf-8')
for item in files:
    path = R / item['path']
    assert sha(path) == item['sha256'] and path.stat().st_size == item['bytes']
print(json.dumps(dict(files=len(files), previous=prior, tests=build['tests'], product=build['product_sha256'], postcheck=True)))
