"""Seal binary/product tests only; new runtime recovery remains explicitly unverified."""
import hashlib,json,shutil
from pathlib import Path
R=Path(__file__).resolve().parent.parent;P=R/'lab/out/streamhouse-oracle-gate-worktree';N=R/'lab/out/product-recovery-binding-20261007'
D=R/'docs/workorders/evidence/DELIVERY-product-recovery-binding-20261007.json';assert not D.exists()
def sha(p):
    with p.open('rb') as f:return hashlib.file_digest(f,'sha256').hexdigest()
proof=json.loads((N/'product-proof.json').read_text());assert proof['tests']==[754,0,0,0] and proof['adapter_unchanged'] and proof['spi_all_classes_unchanged']
assert (N/'product-build.rc.txt').read_text()=='0' and sha(N/'application.jar')==proof['product_sha256']
previous=[]
for name in ['DELIVERY-unified-recovery-20261007.json','DELIVERY-physical-prefix-20261007.json','DELIVERY-dictionary-resume-20261007.json','DELIVERY-undo-graph-resume-20261007.json','DELIVERY-transaction-identity-resume-20261007.json','DELIVERY-cross-window-range-20261007.json','RESEARCH-cross-window-active-20261007.json']:
    manifest=json.loads((D.parent/name).read_text())
    for item in manifest['files']:
        path=R/item['path'];assert sha(path)==item['sha256'] and path.stat().st_size==item['bytes']
    previous.append(dict(manifest=name,files=len(manifest['files']),unchanged=True))
for name in ['src/main/java/com/streamhouse/app/baseline/OracleInputBinding.java','src/main/java/com/streamhouse/app/baseline/OracleInputBindingService.java','src/main/java/com/streamhouse/app/port/OracleApprovedInput.java','src/test/java/com/streamhouse/app/baseline/OracleInputBindingServiceTest.java','pom.xml']:shutil.copyfile(P/'server/streamhouse-app'/name,N/Path(name).name)
for name in ['build_recovery_binding_product19.py','seal_recovery_binding_product19.py']:shutil.copyfile(R/'lab'/name,N/name)
files=[]
for path in sorted(N.rglob('*')):
    if not path.is_file() or 'secret' in path.name.lower():continue
    digest=sha(path);assert sha(path)==digest;files.append(dict(path=path.relative_to(R).as_posix(),bytes=path.stat().st_size,sha256=digest))
out=dict(proof=proof,product_code_commit=json.loads((N/'product-build.command.json').read_text())['commit'],previous_capsules=previous,files_count=len(files),files=files,double_sha256_verified=True,new_http_recovery_verified=False,new_target_cp_ack_recovery_verified=False,independent_review='PENDING',production_deployed=False,site_accepted=False,released=False)
D.write_text(json.dumps(out,indent=2))
for item in files:
    path=R/item['path'];assert sha(path)==item['sha256'] and path.stat().st_size==item['bytes']
print(json.dumps(dict(files=len(files),product=proof['product_sha256'],tests=proof['tests'],commit=out['product_code_commit'])))
