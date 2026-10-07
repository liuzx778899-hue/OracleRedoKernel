"""Seal pending physical prefix evidence, including the original failed test expectation."""
import hashlib,json,re,shutil,zipfile
from pathlib import Path
R=Path(__file__).resolve().parent.parent;N=R/'lab/out/physical-prefix-20261007'
D=R/'docs/workorders/evidence/DELIVERY-physical-prefix-20261007.json';assert not D.exists()
def sha(p):
    with p.open('rb') as f:return hashlib.file_digest(f,'sha256').hexdigest()
proof=json.loads((N/'proof.json').read_text());assert proof['pending']==1 and proof['default_prefix_refused'] and proof['complete_stdout_byte_equal']
assert re.search('Tests run: 328, Failures: 0, Errors: 0, Skipped: 0',(N/'r2-full-build.stdout.txt').read_text())
assert re.search('Tests run: 326, Failures: 1, Errors: 0, Skipped: 0',(N/'full-build.stdout.txt').read_text())
with zipfile.ZipFile(R/'lab/out/cross-window-dictionary-20261007/kernel.jar') as old,zipfile.ZipFile(N/'kernel.jar') as new:
    before={p:old.read(p) for p in old.namelist() if p.endswith('.class')};after={p:new.read(p) for p in new.namelist() if p.endswith('.class')}
    changed=sorted(p for p in before if before[p]!=after[p]);added=sorted(after.keys()-before.keys());assert not before.keys()-after.keys()
    roots=['ArchivedCompensationAssociations','ArchivedCompensationInventory','ArchivedIndexLeafInventory','ArchivedObjectTransactions','ArchivedPhysicalTransactions','IndexLeafPositions']
    assert all(any(p.startswith('com/streamhouse/oracle/'+root) for root in roots) for p in changed+added)
previous=[]
for name in ['DELIVERY-dictionary-resume-20261007.json','DELIVERY-undo-graph-resume-20261007.json','DELIVERY-transaction-identity-resume-20261007.json','DELIVERY-cross-window-range-20261007.json','RESEARCH-cross-window-active-20261007.json']:
    manifest=json.loads((D.parent/name).read_text())
    for item in manifest['files']:
        path=R/item['path'];assert sha(path)==item['sha256'] and path.stat().st_size==item['bytes']
    previous.append(dict(manifest=name,files=len(manifest['files']),unchanged=True))
for root in roots:shutil.copyfile(R/('src/main/java/com/streamhouse/oracle/'+root+'.java'),N/(root+'.java'))
for path in ['src/test/java/com/streamhouse/oracle/PendingTransactionDiscoveryTest.java','lab/PhysicalPrefixProbe.java','lab/verify_physical_prefix19.py','lab/seal_physical_prefix19.py']:shutil.copyfile(R/path,N/Path(path).name)
files=[]
for path in sorted(N.rglob('*')):
    if not path.is_file() or 'secret' in path.name.lower():continue
    digest=sha(path);assert sha(path)==digest;files.append(dict(path=path.relative_to(R).as_posix(),bytes=path.stat().st_size,sha256=digest))
out=dict(proof=proof,tests=[328,0,0,0],first_failure_tests=[326,1,0,0],classes_changed=changed,classes_added=added,classes_unchanged=len(before)-len(changed),previous_capsules=previous,files_count=len(files),files=files,double_sha256_verified=True,unified_resume_verified=False,product_changed=False,production_deployed=False,site_accepted=False,released=False)
D.write_text(json.dumps(out,indent=2))
for item in files:
    path=R/item['path'];assert sha(path)==item['sha256'] and path.stat().st_size==item['bytes']
print(json.dumps(dict(files=len(files),kernel=proof['kernel_sha256'],changed=changed,added=added,unchanged=out['classes_unchanged'])))
