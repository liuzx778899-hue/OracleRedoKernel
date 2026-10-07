"""Seal actual product baseline/new raw transaction plus independently verified decoder candidate."""
import hashlib, json, shutil, zipfile
from pathlib import Path

R=Path(__file__).resolve().parent.parent
O=R/'lab/out/product-active-20261007';N=R/'lab/out/product-active-marker2-20261007'
D=R/'docs/workorders/evidence/DELIVERY-product-active-marker2-20261007.json'
assert not D.exists()

def sha(path):
    with path.open('rb') as stream:return hashlib.file_digest(stream,'sha256').hexdigest()

previous=[]
for name in ['DELIVERY-recovery-failures-20261007.json','DELIVERY-recovery-http-20261007.json',
             'DELIVERY-product-recovery-binding-20261007.json','DELIVERY-unified-recovery-20261007.json']:
    manifest=json.loads((D.parent/name).read_text())
    for item in manifest['files']:
        path=R/item['path'];assert sha(path)==item['sha256'] and path.stat().st_size==item['bytes']
    previous.append(dict(manifest=name,files=len(manifest['files']),unchanged=True))
proof=json.loads((N/'kernel-proof.json').read_text());assert proof['tests']==[330,0,0,0]
assert sha(N/'kernel.jar')==proof['kernel_sha256'] and proof['pending_transactions']==1 and proof['restore_full_output_exact']
assert (N/'actual-closed.stdout.txt').read_bytes()==(N/'restore-suffix.stdout.txt').read_bytes()
assert (N/'checkpoint-prefix-halt.rc.txt').read_text()=='23'
assert (O/'events-pending.rc.txt').read_text()=='1' and not (O/'events-pending.stdout.txt').read_bytes()
assert 'UNSUPPORTED_KDOM2_UPDATE_LAYOUT' in (O/'events-closed.stderr.txt').read_text()
assert (O/'stopped-state.stdout.txt').read_text().strip()=='false|143|exited'
assert (O/'collector.rc.txt').read_text()=='0' and (O/'transaction.rc.txt').read_text()=='0'
assert json.loads((O/'pending-session-observed.json').read_text())['source_commit_sent'] is False
truth=json.loads((O/'baseline-truth-proof.json').read_text());assert truth['differences']==0
events=json.loads((N/'events-proof.json').read_text());assert events['differences']==0 and events['comparison_poisons_detected']==4
negatives=json.loads((N/'actual-negative-proof.json').read_text());assert len(negatives)==2 and all(p['rc']==1 and p['stdout_bytes']==0 for p in negatives)
with zipfile.ZipFile(O/'kernel.jar') as old,zipfile.ZipFile(N/'kernel.jar') as new:
    classes=[name for name in old.namelist() if name.endswith('.class')]
    assert set(classes)=={name for name in new.namelist() if name.endswith('.class')}
    changed=[name for name in classes if old.read(name)!=new.read(name)]
    assert changed==['com/streamhouse/oracle/HeapRowPatch.class']
for name in ['product_active_runtime19.py','prepare_product_active19.py','capture_product_active19.py','ProductActiveRecordProbe.java',
             'verify_product_active_native19.py','verify_product_active_faults19.py','seal_product_active19.py']:
    shutil.copyfile(R/'lab'/name,N/name)
for path in ['src/main/java/com/streamhouse/oracle/HeapRowPatch.java','src/test/java/com/streamhouse/oracle/Kdom2MarkerTwoTest.java']:
    shutil.copyfile(R/path,N/Path(path).name)
files=[]
for directory in [O,N]:
    for path in sorted(directory.rglob('*')):
        if not path.is_file() or 'secret' in path.name.lower():continue
        digest=sha(path);assert sha(path)==digest
        files.append(dict(path=path.relative_to(R).as_posix(),bytes=path.stat().st_size,sha256=digest))
out=dict(kernel=proof,native_events=events,baseline_truth=truth,negatives=negatives,
         code_commit=json.loads((N/'kernel-build.command.json').read_text())['commit'],
         production_classes_changed=changed,production_classes_unchanged=len(classes)-len(changed),
         previous_capsules=previous,source_transaction_once_only=True,source_replayed=False,
         source_configuration_changes=0,target_actions=0,product_event_bindings=0,product_events=0,
         actual_product_pending_registration_verified=False,actual_product_cross_publication_recovery_verified=False,
         product_helper_running=False,collector_running=False,independent_review='PENDING',
         production_deployed=False,site_accepted=False,released=False,development_D1_closed=False,
         files_count=len(files),double_sha256_verified=True,files=files)
D.write_text(json.dumps(out,indent=2))
for item in files:assert sha(R/item['path'])==item['sha256']
print(json.dumps(dict(files=len(files),kernel=proof['kernel_sha256'],classes_changed=changed,classes_unchanged=out['production_classes_unchanged'])))
