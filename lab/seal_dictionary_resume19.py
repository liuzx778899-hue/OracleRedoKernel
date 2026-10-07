"""Additional source/baseline counterexamples, then immutable evidence sealing."""
import hashlib,json,subprocess,re,shutil,zipfile
from pathlib import Path
R=Path(__file__).resolve().parent.parent;N=R/'lab/out/cross-window-dictionary-20261007'
D=R/'docs/workorders/evidence/DELIVERY-dictionary-resume-20261007.json';assert not D.exists()
def sha(p):
    with p.open('rb') as f:return hashlib.file_digest(f,'sha256').hexdigest()
def reject(name,args,reason):
    assert not (N/(name+'.command.json')).exists();(N/(name+'.command.json')).write_text(json.dumps(args))
    p=subprocess.run(args,capture_output=True,timeout=300,cwd=R)
    for suffix,value in [('stdout.txt',p.stdout),('stderr.txt',p.stderr),('rc.txt',str(p.returncode).encode())]:(N/(name+'.'+suffix)).write_bytes(value)
    assert p.returncode==1 and not p.stdout and reason.encode() in p.stderr,(name,p.returncode)
args=json.loads((N/'historical-complete-resume.command.json').read_text());original=(N/'historical.state').read_bytes()
for name,raw,reason in [
 ('missing-commit',b'\n'.join(original.split(b'\n')[:-2])+b'\n','DICTIONARY_CHECKPOINT_STATE_CHANGED'),
 ('missing-first-source',b'\n'.join(original.split(b'\n')[:2]+original.split(b'\n')[3:]),'DICTIONARY_CHECKPOINT_SOURCE_PREFIX_CHANGED')]:
    path=N/(name+'.state');path.write_bytes(raw);candidate=list(args);candidate[6]=str(path);candidate[7]=sha(path);reject(name,candidate,reason)
candidate=list(args);candidate[8]=str(R/'lab/out/cross-window-active-20261007/current-v3.snapshot');candidate[9]='3619a433adcbbb336a67877fdaeba9c8e46fd69a841e3c955d28affb4627d79a'
reject('wrong-baseline',candidate,'DICTIONARY_CHECKPOINT_BASELINE')
proof=json.loads((N/'proof.json').read_text());assert re.search('Tests run: 322, Failures: 0, Errors: 0, Skipped: 0',(N/'full-build.stdout.txt').read_text())
for case in proof['cases']:
    output=(N/(case['name']+'-complete-resume.stdout.txt')).read_bytes();assert output==(R/case['byte_equal_reference']).read_bytes() and output.count(b'EVENT|')==2
with zipfile.ZipFile(R/'lab/out/cross-window-undo-20261007/kernel.jar') as old,zipfile.ZipFile(N/'kernel.jar') as new:
    before={p:old.read(p) for p in old.namelist() if p.endswith('.class')};after={p:new.read(p) for p in new.namelist() if p.endswith('.class')}
    changed=sorted(p for p in before if before[p]!=after[p]);assert set(before)==set(after)
    assert all(p.startswith('com/streamhouse/oracle/ArchivedDictionaryHistory') or p.startswith('com/streamhouse/oracle/CapturedHistoricalHeapBatch') for p in changed)
previous=[]
for name in ['DELIVERY-undo-graph-resume-20261007.json','DELIVERY-transaction-identity-resume-20261007.json','DELIVERY-cross-window-range-20261007.json','RESEARCH-cross-window-active-20261007.json']:
    manifest=json.loads((D.parent/name).read_text())
    for item in manifest['files']:
        path=R/item['path'];assert sha(path)==item['sha256'] and path.stat().st_size==item['bytes']
    previous.append(dict(manifest=name,files=len(manifest['files']),unchanged=True))
shutil.copyfile(Path(__file__),N/Path(__file__).name)
files=[]
for path in sorted(N.rglob('*')):
    if not path.is_file() or 'secret' in path.name.lower():continue
    digest=sha(path);assert sha(path)==digest;files.append(dict(path=path.relative_to(R).as_posix(),bytes=path.stat().st_size,sha256=digest))
out=dict(proof=proof,tests=[322,0,0,0],classes_changed=changed,classes_unchanged=len(before)-len(changed),previous_capsules=previous,files_count=len(files),double_sha256_verified=True,files=files,incremental_pending_sys_recovery_verified=False,full_publication_recovery_verified=False,production_deployed=False,site_accepted=False,released=False)
D.write_text(json.dumps(out,indent=2))
for item in files:
    path=R/item['path'];assert sha(path)==item['sha256'] and path.stat().st_size==item['bytes']
print(json.dumps(dict(files=len(files),kernel=proof['kernel_sha256'],changed=changed,unchanged=out['classes_unchanged'])))
