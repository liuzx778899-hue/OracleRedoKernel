"""Seal historical adapter projection; product HTTP/target/checkpoint remain pending."""
import hashlib,json,shutil,subprocess
from pathlib import Path
R=Path(__file__).resolve().parent.parent;N=R/'lab/out/historical-product-candidate-20261006'
dest=R/'docs/workorders/evidence/DELIVERY-historical-projection-20261006.json';assert not dest.exists()
def sha(p):
    h=hashlib.sha256()
    with p.open('rb') as f:
        for b in iter(lambda:f.read(1048576),b''):h.update(b)
    return h.hexdigest()
old=json.loads((R/'docs/workorders/evidence/DELIVERY-historical-row-schema-20261006.json').read_text())
for f in old['files']:
    p=R/f['path'];assert sha(p)==f['sha256'] and p.stat().st_size==f['bytes']
for name in ['OracleHistoricalProjectionProbe.java','build_historical_projection19.py','verify_historical_projection19.py','seal_historical_projection19.py']:shutil.copyfile(R/'lab'/name,N/('sealed-'+name))
base=R/'integration/streamhouse-engine-oracle/src'
for name in ['OracleBatchProjection.java','OracleHistoricalBatchProjection.java']:shutil.copyfile(base/'main/java/com/streamhouse/engines/oracle'/name,N/('sealed-'+name))
shutil.copyfile(base/'test/java/com/streamhouse/engines/oracle/OracleHistoricalBatchProjectionTest.java',N/'sealed-OracleHistoricalBatchProjectionTest.java')
proof=json.loads((N/'historical-projection-proof.json').read_text());build=json.loads((N/'projection-build-proof.json').read_text())
assert build['tests']==[24,0,0,0] and build['adapter_sha256']==proof['adapter_sha256']==sha(N/'adapter.jar')
assert proof['complete_projected_events']==2 and proof['static_v2_projection_json_equal']
files=[]
for p in sorted(N.rglob('*')):
    if not p.is_file() or 'secret' in p.name.lower() or p.name.startswith('projection-seal-driver.'):continue
    a=sha(p);assert a==sha(p);files.append(dict(path=str(p.relative_to(R)).replace('\\','/'),bytes=p.stat().st_size,sha256=a))
out=dict(code_commit=subprocess.check_output(['git','rev-parse','HEAD'],cwd=R).decode().strip(),build=build,projection=proof,previous_189_unchanged=True,double_sha256_verified=True,source_actions=0,target_actions=0,product_rebuilt=False,http_accepted=False,checkpoint_ack_accepted=False,release_accepted=False,files_count=len(files),files=files)
dest.write_text(json.dumps(out,indent=2))
for f in files:assert sha(R/f['path'])==f['sha256']
print(json.dumps(dict(files=len(files),postcheck_hashes_match=True,adapter_sha256=build['adapter_sha256'],tests=build['tests'])))
