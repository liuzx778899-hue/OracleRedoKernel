"""Seal limited historical fault/recovery evidence and preserve every failed candidate."""
import hashlib, json, shutil, subprocess
from pathlib import Path
R = Path(__file__).resolve().parent.parent
N = R / 'lab/out/split-history-late-http-20261007'
P = R / 'lab/out/streamhouse-oracle-gate-worktree'
D = R / 'docs/workorders/evidence/DELIVERY-split-history-late-http-20261007.json'
assert not D.exists()

def sha(path):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()

previous = []
for name in ['DELIVERY-split-history-product-20261007.json', 'DELIVERY-split-history-20261007.json', 'DELIVERY-v3-product-acquisition-20261006.json']:
    file = D.parent / name
    manifest = json.loads(file.read_text(encoding='utf-8'))
    for item in manifest['files']:
        path = R / item['path']
        assert sha(path) == item['sha256'] and path.stat().st_size == item['bytes'], item['path']
    previous.append(dict(manifest=file.relative_to(R).as_posix(), files=len(manifest['files']), unchanged=True))
build = json.loads((N / 'repaired-build-r2/proof.json').read_text())
assert build['tests'] == [752, 0, 0, 0] and build['all_dependency_class_bytes_unchanged']
for file in ['PipelineFacade.java', 'OracleFacadePublicationTest.java']:
    paths = list((P / 'server/streamhouse-pipeline/src').rglob(file))
    assert len(paths) == 1 and sha(paths[0]) == sha(N / 'repaired-build-r2' / file)
assert (N / 'regression-r1/rc.txt').read_text() == (N / 'regression-r2/rc.txt').read_text() == '1'
proofs = {name: json.loads((N / (name + '-proof.json')).read_text()) for name in
    ['bad-binding-state', 'repaired-late-state', 'ack-blocked-state', 'ack-recovered-state', 'duplicate-state', 'restart-state']}
assert proofs['repaired-late-state']['target_rows_and_all_ledgers_rolled_back']
assert proofs['ack-blocked-state']['persistent_ack_files'] == 0 and proofs['ack-blocked-state']['published_batch_state_preserved']
assert proofs['duplicate-state']['repeated_target_control_ack_exact_unchanged'] and proofs['restart-state']['repeated_target_control_ack_exact_unchanged']
assert (N / 'stop-final-state.stdout.txt').read_text().strip() == 'false|143|exited'
source = (N / 'final-source.stdout.txt').read_text()
assert all(v in source for v in ['LOGGING|NO|NO', 'OLD|11|blocking-closed|new-note', 'BASE|0|history-product-restored|anchor', 'META_EQUAL|1'])
failures = list((N / 'failures').rglob('*.applyfail.txt'))
assert len(failures) == 2
for name in ['split_history_late_http19.py', 'build_oracle_failure_evidence19.py', 'verify_split_history_late_http19.py', 'seal_split_history_late_http19.py']:
    shutil.copyfile(R / 'lab' / name, N / ('sealed-' + name))
files = []
for path in sorted(N.rglob('*')):
    if not path.is_file() or 'secret' in path.name.lower() or path.suffix == '.lock':
        continue
    digest = sha(path)
    assert sha(path) == digest
    files.append(dict(path=path.relative_to(R).as_posix(), bytes=path.stat().st_size, sha256=digest))
out = dict(product_build_commit=json.loads((N / 'repaired-build-r2/command.json').read_text())['commit'],
    build=build, proofs=proofs, copied_original_acquisition_registration=True, registration_full_metadata_equal=True,
    source_business_dml=0, source_logging_changes=0, source_acquisitions=0, capture_complete=False,
    same_instance_owned_target=True, independent_target_verified=False, independent_review='PENDING',
    production_deployed=False, site_accepted=False, released=False, helper='false|143|exited',
    failure_dispositions=dict(original_http='Target rollback observed but database cause absent; original ef852 candidate logs/states preserved, not accepted for full late-failure evidence',
        regression_r1='7/1/0/0; missing original exception at the sink reproduced',
        first_repair='740223e/751 passed but new ACK regression showed stale decoded GATED object could overwrite PUBLISHED; candidate retained, never run',
        regression_r2='8/1/0/0; expected PUBLISHED but was GATED reproduced; final dbab51a uses freshly stored batch',
        final='752/0/0/0; real ORA-02290 and ACK FileAlreadyExistsException full files and references retained; actual ACK fault keeps PUBLISHED and restores exactly'),
    previous_capsules=previous, files_count=len(files), double_sha256_verified=True, files=files)
D.write_text(json.dumps(out, indent=2), encoding='utf-8')
for item in files:
    path = R / item['path']
    assert sha(path) == item['sha256'] and path.stat().st_size == item['bytes']
print(json.dumps(dict(files=len(files), tests=build['tests'], product=build['product_sha256'], previous=previous, postcheck=True)))
