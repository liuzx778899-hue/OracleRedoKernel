"""One-shot new baseline for the owned cross-window experiment; source SELECT only."""
import datetime,hashlib,json,shutil,subprocess
from pathlib import Path

R=Path(__file__).resolve().parent.parent
N=R/'lab/out/product-active-20261007'
S='oraredo-19c-lab-20261004'
assert N.is_dir()
(N/'executed-prepare-active-inputs-r3.py').write_bytes(Path(__file__).read_bytes())
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()

def run(name,args,body=None):
    args=list(map(str,args))
    (N/(name+'.command.json')).write_text(json.dumps(args))
    p=subprocess.run(args,input=body,capture_output=True,timeout=180)
    for suffix,data in [('stdout.txt',p.stdout),('stderr.txt',p.stderr),('rc.txt',str(p.returncode).encode())]:
        (N/(name+'.'+suffix)).write_bytes(data)
    assert p.returncode==0,(name,p.returncode)
    return p.stdout.decode()

assert (N/'source-ownership.rc.txt').read_text()=='0' and not (N/'collector-preflight.stdout.txt').read_text().strip()
shutil.copyfile(R/'lab/out/unified-recovery-20261007/kernel.jar',N/'kernel.jar')
for name in ['adapter.jar','spi.jar']:
    shutil.copyfile(R/'lab/out/split-history-product-20261007'/name,N/name)
shutil.copyfile(R/'lab/out/v3-product-runtime-20261006/ojdbc8.jar',N/'ojdbc8.jar')
shutil.copyfile(R/'lab/out/oracle-snapshot-acquisition-20261005/source.secret',N/'source.secret')
acquired=json.loads((N/'acquire.stdout.txt').read_text())[1]['data']
verified=json.loads((N/'verify.stdout.txt').read_text())[1]['data']
assert acquired['state']=='ACQUIRED_ONLY'
assert all(acquired[k]==verified[k] for k in acquired if k!='registeredAt')
assert abs((datetime.datetime.fromisoformat(acquired['registeredAt'])-datetime.datetime.fromisoformat(verified['registeredAt'])).total_seconds())<=0.000001
snapshot=N/'snapshots'/(str(acquired['id'])+'.snapshot')
assert sha(snapshot)==acquired['snapshotSha256']
metadata=json.loads(acquired['metadataJson'])
assert int(metadata['scn'])==int(acquired['snapshotScn']) and metadata['rowCount']==1 and metadata['dictionaryBaseline']
(N/'baseline-reference.json').write_text(json.dumps(dict(path=str(snapshot),scn=int(acquired['snapshotScn']),sha256=acquired['snapshotSha256'],acquisition_id=int(acquired['id']),authority=acquired['authority'],actual_product_http=True,new_logs_only=True,events=0,source_dml=0,target_actions=0),indent=2))
# Run the independent ruler in the new directory, preserving its exact executable source.
truth=(R/'lab/verify_v3_history_baseline19.py').read_text()
truth=truth.replace("O=R/'lab/out/v3-historical-raw-20261006'","O=R/'lab/out/product-active-20261007'")
driver=N/'fresh-baseline-truth.py'
truth=truth.replace('R=Path(__file__).resolve().parent.parent;', 'R=Path('+repr(str(R))+');')
driver.write_text(truth)
run('independent-baseline-driver',[__import__('sys').executable,driver])
(N/'preparation-proof.json').write_text(json.dumps(dict(baseline=json.loads((N/'baseline-reference.json').read_text()),truth=json.loads((N/'baseline-truth-proof.json').read_text()),artifacts={x:sha(N/x) for x in ['kernel.jar','adapter.jar','spi.jar','ojdbc8.jar']},source_mutations=0,cross_window_recovery='NOT_VERIFIED'),indent=2))
print('NEW_CROSS_WINDOW_BASELINE_VERIFIED_NO_SOURCE_MUTATIONS')
