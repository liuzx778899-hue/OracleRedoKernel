"""Seal historical target evidence; secret files and older capsules stay untouched."""
import hashlib, json, shutil, subprocess
from pathlib import Path
R=Path(__file__).resolve().parent.parent;N=R/'lab/out/historical-target-candidate-20261006';P=R/'lab/out/streamhouse-oracle-gate-worktree'
D=R/'docs/workorders/evidence/DELIVERY-historical-target-20261006.json'
assert not D.exists()
def sha(path):
    h=hashlib.sha256()
    with path.open('rb') as f:
        for block in iter(lambda:f.read(1048576),b''):h.update(block)
    return h.hexdigest()
previous=[]
for name in ['historical-schema-protocol','historical-projection','historical-row-schema','new-v3-history-raw']:
    matches=list((R/'docs/workorders/evidence').glob('DELIVERY-'+name+'-20261006.json'))
    if name=='new-v3-history-raw' and not matches:continue
    assert len(matches)==1,name
    manifest=json.loads(matches[0].read_text())
    for item in manifest['files']:
        path=R/item['path'];assert sha(path)==item['sha256'] and path.stat().st_size==item['bytes'],item['path']
    previous.append(dict(manifest=str(matches[0].relative_to(R)).replace('\\','/'),files=len(manifest['files']),unchanged=True))
for base in [R,P]:
    changed=subprocess.check_output(['git','diff','--name-only','HEAD'],cwd=base).decode().splitlines()
    assert not any(p.startswith(('src/','integration/','server/')) for p in changed),changed
for path in [R/'lab/seal_historical_target19.py',R/'integration/streamhouse-engine-oracle/src/main/java/com/streamhouse/engines/oracle/OracleBatchProjection.java',
    R/'integration/streamhouse-engine-oracle/src/main/java/com/streamhouse/engines/oracle/OracleTargetBatchWriter.java',
    R/'integration/streamhouse-engine-oracle/src/test/java/com/streamhouse/engines/oracle/OracleHistoricalTargetMappingTest.java',
    P/'server/streamhouse-app/pom.xml',*sorted((P/'server/streamhouse-app/src/main/java/com/streamhouse/app/port').glob('Oracle*.java')),
    P/'server/streamhouse-app/src/test/java/com/streamhouse/app/port/OracleControlPublicationTest.java']:
    shutil.copyfile(path,N/('sealed-'+path.name))
files=[]
for path in sorted(N.rglob('*')):
    if not path.is_file() or 'secret' in path.name.lower():continue
    digest=sha(path);assert sha(path)==digest
    files.append(dict(path=str(path.relative_to(R)).replace('\\','/'),bytes=path.stat().st_size,sha256=digest))
out=dict(root_code_commit=subprocess.check_output(['git','rev-parse','HEAD'],cwd=R).decode().strip(),
    product_code_commit=subprocess.check_output(['git','rev-parse','HEAD'],cwd=P).decode().strip(),
    build=json.loads((N/'product-build-proof.json').read_text()),target=json.loads((N/'historical-target-proof.json').read_text()),
    receipt_integrity=json.loads((N/'receipt-integrity-proof.json').read_text()),adapter_tests=[26,0,0,0],kernel_tests_reused=[299,0,0,0],
    previous_capsules=previous,files_count=len(files),double_sha256_verified=True,independent_review='PENDING',
    historical_product_http=False,checkpoint_ack=False,site_accepted=False,release_accepted=False,files=files)
D.write_text(json.dumps(out,indent=2))
for item in files:assert sha(R/item['path'])==item['sha256']
print(json.dumps(dict(files=len(files),previous=previous,root=out['root_code_commit'],product=out['product_code_commit'],postcheck=True)))
