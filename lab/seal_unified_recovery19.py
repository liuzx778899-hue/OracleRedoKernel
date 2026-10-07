"""Component binding counterexamples and double-SHA evidence sealing."""
import base64,hashlib,json,re,shutil,subprocess,zipfile
from pathlib import Path
R=Path(__file__).resolve().parent.parent;N=R/'lab/out/unified-recovery-20261007'
D=R/'docs/workorders/evidence/DELIVERY-unified-recovery-20261007.json';assert not D.exists()
def sha(p):
    with p.open('rb') as f:return hashlib.file_digest(f,'sha256').hexdigest()
def reject(name,args,reason):
    assert not (N/(name+'.command.json')).exists();(N/(name+'.command.json')).write_text(json.dumps(args))
    p=subprocess.run(args,capture_output=True,timeout=300,cwd=R)
    for suffix,value in [('stdout.txt',p.stdout),('stderr.txt',p.stderr),('rc.txt',str(p.returncode).encode())]:(N/(name+'.'+suffix)).write_bytes(value)
    assert p.returncode==1 and not p.stdout and reason.encode() in p.stderr,(name,p.returncode)
args=json.loads((N/'active-separate-jvm-resume.command.json').read_text());active=(N/'active.state').read_bytes();historical=(N/'historical.state').read_bytes()
for prefix,name in [(b'I|','mixed-identity'),(b'D|','mixed-dictionary')]:
    replacement=next(line for line in historical.splitlines(keepends=True) if line.startswith(prefix))
    path=N/(name+'.state');path.write_bytes(b''.join(replacement if line.startswith(prefix) else line for line in active.splitlines(keepends=True)))
    candidate=list(args);candidate[6]=str(path);candidate[7]=sha(path);reject(name,candidate,'HISTORICAL_RECOVERY_STATE_CHANGED')
candidate=list(args);candidate[8]=str(R/'lab/out/v3-product-history-20261006/actual-product-v3.snapshot');candidate[9]='323fd35a8ceee43545c28a9fb32f55cedd41409a3e1b5fb63e08606380cd24a2'
reject('wrong-baseline',candidate,'HISTORICAL_RECOVERY_BASELINE_OR_LEAF')
identity=base64.b64decode(next(line for line in active.splitlines() if line.startswith(b'I|'))[2:]);graph=base64.b64decode(next(line for line in active.splitlines() if line.startswith(b'P|')).split(b'|')[-1])
assert identity==(R/'lab/out/cross-window-undo-20261007/resume/identity.state').read_bytes()
assert graph==(R/'lab/out/cross-window-undo-20261007/resume/graph.state').read_bytes()
proof=json.loads((N/'proof.json').read_text());assert re.search('Tests run: 328, Failures: 0, Errors: 0, Skipped: 0',(N/'full-build.stdout.txt').read_text())
for case in proof['cases']:
    output=(N/(case['name']+'-separate-jvm-resume.stdout.txt')).read_bytes();assert output==(R/case['reference']).read_bytes() and output.count(b'EVENT|')==2
with zipfile.ZipFile(R/'lab/out/physical-prefix-20261007/kernel.jar') as old,zipfile.ZipFile(N/'kernel.jar') as new:
    before={p:old.read(p) for p in old.namelist() if p.endswith('.class')};after={p:new.read(p) for p in new.namelist() if p.endswith('.class')}
    changed=sorted(p for p in before if before[p]!=after[p]);added=sorted(after.keys()-before.keys());assert not before.keys()-after.keys()
    assert added==['com/streamhouse/oracle/HistoricalRecovery.class']
    assert all(any(p.startswith('com/streamhouse/oracle/'+root) for root in ['ArchivedPhysicalTransactions','RedoBatch','CapturedHistoricalHeapBatch']) for p in changed)
previous=[]
for name in ['DELIVERY-physical-prefix-20261007.json','DELIVERY-dictionary-resume-20261007.json','DELIVERY-undo-graph-resume-20261007.json','DELIVERY-transaction-identity-resume-20261007.json','DELIVERY-cross-window-range-20261007.json','RESEARCH-cross-window-active-20261007.json']:
    manifest=json.loads((D.parent/name).read_text())
    for item in manifest['files']:
        path=R/item['path'];assert sha(path)==item['sha256'] and path.stat().st_size==item['bytes']
    previous.append(dict(manifest=name,files=len(manifest['files']),unchanged=True))
for name in ['HistoricalRecovery','RedoBatch','ArchivedPhysicalTransactions','CapturedHistoricalHeapBatch']:shutil.copyfile(R/('src/main/java/com/streamhouse/oracle/'+name+'.java'),N/(name+'.java'))
for name in ['UnifiedRecoveryProbe.java','verify_unified_recovery19.py','seal_unified_recovery19.py']:shutil.copyfile(R/'lab'/name,N/name)
files=[]
for path in sorted(N.rglob('*')):
    if not path.is_file() or 'secret' in path.name.lower():continue
    digest=sha(path);assert sha(path)==digest;files.append(dict(path=path.relative_to(R).as_posix(),bytes=path.stat().st_size,sha256=digest))
out=dict(proof=proof,tests=[328,0,0,0],negative_runs=14,prefix_identity_and_graph_equal_prior=True,classes_changed=changed,classes_added=added,classes_unchanged=len(before)-len(changed),previous_capsules=previous,files_count=len(files),files=files,double_sha256_verified=True,product_changed=False,production_deployed=False,site_accepted=False,released=False)
D.write_text(json.dumps(out,indent=2))
for item in files:
    path=R/item['path'];assert sha(path)==item['sha256'] and path.stat().st_size==item['bytes']
print(json.dumps(dict(files=len(files),kernel=proof['kernel_sha256'],changed=changed,added=added,unchanged=out['classes_unchanged'])))
