"""Seal real V3 product acquisition and recovery without claiming historical publication."""
import hashlib,json,shutil,subprocess
from pathlib import Path
R=Path(__file__).resolve().parent.parent;P=R/'lab/out/streamhouse-oracle-gate-worktree';N=R/'lab/out/v3-product-runtime-20261006'
D=R/'docs/workorders/evidence/DELIVERY-v3-product-acquisition-20261006.json';assert not D.exists()
def sha(path):
    h=hashlib.sha256()
    with path.open('rb') as stream:
        for block in iter(lambda:stream.read(1048576),b''):h.update(block)
    return h.hexdigest()
previous=[]
for name in ['historical-target','historical-schema-protocol','historical-projection','historical-row-schema','v3-history-raw']:
    file=R/'docs/workorders/evidence'/('DELIVERY-'+name+'-20261006.json');manifest=json.loads(file.read_text())
    for item in manifest['files']:
        path=R/item['path'];assert sha(path)==item['sha256'] and path.stat().st_size==item['bytes'],item['path']
    previous.append(dict(manifest=file.relative_to(R).as_posix(),files=len(manifest['files']),unchanged=True))
for path in [R/'lab/seal_v3_product_acquisition19.py',R/'lab/SysDictionaryBaselineResearchProbe.java',
    P/'server/streamhouse-app/src/main/java/com/streamhouse/app/baseline/OracleAcquisitionService.java',
    P/'server/streamhouse-app/src/main/java/com/streamhouse/app/web/OracleAcquisitionController.java',
    P/'server/streamhouse-app/src/test/java/com/streamhouse/app/baseline/OracleAcquisitionServiceTest.java']:
    shutil.copyfile(path,N/('sealed-'+path.name))
files=[]
for path in sorted(N.rglob('*')):
    if not path.is_file() or 'secret' in path.name.lower() or path.suffix=='.log' or 'logs' in path.relative_to(N).parts:continue
    digest=sha(path);assert digest==sha(path)
    files.append(dict(path=path.relative_to(R).as_posix(),bytes=path.stat().st_size,sha256=digest))
build=json.loads((N/'build-proof.json').read_text());truth=json.loads((N/'baseline-truth-proof.json').read_text())
assert build['tests']==[750,0,0,0] and truth['differences']==0
assert (N/'restarted-state.stdout.txt').read_text().strip()=='true|0|running'
out=dict(root_code_commit=subprocess.check_output(['git','rev-parse','HEAD'],cwd=R).decode().strip(),
    product_code_commit=subprocess.check_output(['git','rev-parse','HEAD'],cwd=P).decode().strip(),build=build,actual_acquisition=truth,
    recovery=json.loads((N/'recovery-proof.json').read_text()),restart=json.loads((N/'restart-proof.json').read_text()),
    original_failure_records=['first-truth-driver.failure-from-tool.txt','metadata-r1.failure-from-tool.txt','recovery-first.failure-from-tool.txt','missing-history-poison.stdout.txt'],
    previous_capsules=previous,helper_running=True,helper_name='ork-oracle-v3-product-20261006',helper_port=37261,
    source_fixture_dml=0,source_logging_changes=0,owned_control_schema_created=True,target_actions=0,
    historical_http_publication=False,checkpoint_ack=False,independent_review='PENDING',site_accepted=False,release_accepted=False,
    files_count=len(files),double_sha256_verified=True,files=files)
D.write_text(json.dumps(out,indent=2))
for item in files:assert sha(R/item['path'])==item['sha256']
print(json.dumps(dict(files=len(files),previous=previous,postcheck=True,acquisition=truth)))
