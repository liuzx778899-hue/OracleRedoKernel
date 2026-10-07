"""Seal identity-only recovery evidence without claiming full parser or publication recovery."""
import hashlib,json,re,shutil,zipfile
from pathlib import Path
R=Path(__file__).resolve().parent.parent;N=R/'lab/out/cross-window-identities-20261007'
D=R/'docs/workorders/evidence/DELIVERY-transaction-identity-resume-20261007.json';assert not D.exists()
def sha(path):
    with path.open('rb') as f:return hashlib.file_digest(f,'sha256').hexdigest()
proof=json.loads((N/'proof.json').read_text())
assert proof['prefix_process_rc']==23 and proof['separate_jvm_resume_rc']==0 and proof['negative_cases']==2
assert re.search('Tests run: 317, Failures: 0, Errors: 0, Skipped: 0',(N/'full-build.stdout.txt').read_text())
assert int((N/'full-build.rc.txt').read_text())==0
assert 'P|677737149|3|37749008|14|2869|10|2743312' in (N/'pending-identities.state').read_text()
assert 'C|677737149|3|37749008|14|2869' in (N/'closed-identities.state').read_text() and '\nP|' not in (N/'closed-identities.state').read_text()
previous=[]
for name in ['DELIVERY-cross-window-range-20261007.json','RESEARCH-cross-window-active-20261007.json']:
    manifest=json.loads((D.parent/name).read_text())
    for item in manifest['files']:
        path=R/item['path'];assert sha(path)==item['sha256'] and path.stat().st_size==item['bytes']
    previous.append(dict(manifest=name,files=len(manifest['files']),unchanged=True))
with zipfile.ZipFile(R/'lab/out/cross-window-range-20261007/candidate-kernel.jar') as old,zipfile.ZipFile(N/'kernel.jar') as new:
    before={p:old.read(p) for p in old.namelist() if p.endswith('.class')};after={p:new.read(p) for p in new.namelist() if p.endswith('.class')}
    assert before.keys()==after.keys()
    changed=[p for p in before if before[p]!=after[p]]
    assert changed==['com/streamhouse/oracle/TransactionLifecycle.class']
for name in ['seal_transaction_identity_resume19.py','verify_transaction_identity_resume19.py','TransactionIdentityResumeProbe.java']:shutil.copyfile(R/'lab'/name,N/('sealed-'+name))
files=[]
for path in sorted(N.rglob('*')):
    if not path.is_file() or 'secret' in path.name.lower():continue
    digest=sha(path);assert sha(path)==digest;files.append(dict(path=path.relative_to(R).as_posix(),bytes=path.stat().st_size,sha256=digest))
out=dict(proof=proof,tests=[317,0,0,0],classes_changed=changed,classes_unchanged=len(before)-len(changed),previous_capsules=previous,driver_failure_preserved=True,passed_build_halt_resume_not_replayed=True,full_undo_dictionary_state_verified=False,product_changed=False,production_deployed=False,site_accepted=False,released=False,double_sha256_verified=True,files_count=len(files),files=files)
D.write_text(json.dumps(out,indent=2))
for item in files:
    path=R/item['path'];assert sha(path)==item['sha256'] and path.stat().st_size==item['bytes']
print(json.dumps(dict(files=len(files),tests=out['tests'],kernel_sha256=proof['kernel_sha256'],changed=changed,postcheck=True)))
