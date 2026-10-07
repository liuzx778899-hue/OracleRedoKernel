"""Seal pending graph continuation evidence, retaining full-recovery gates as unverified."""
import hashlib,json,re,shutil,zipfile
from pathlib import Path
R=Path(__file__).resolve().parent.parent;N=R/'lab/out/cross-window-undo-20261007'
D=R/'docs/workorders/evidence/DELIVERY-undo-graph-resume-20261007.json';assert not D.exists()
def sha(path):
    with path.open('rb') as f:return hashlib.file_digest(f,'sha256').hexdigest()
proof=json.loads((N/'proof.json').read_text());assert proof['halt_rc']==23 and proof['separate_jvm_resume_rc']==0 and proof['graph_poison_rejected']
assert re.search('Tests run: 322, Failures: 0, Errors: 0, Skipped: 0',(N/'full-build.stdout.txt').read_text())
assert int((N/'full-build.rc.txt').read_text())==0
with zipfile.ZipFile(R/'lab/out/cross-window-identities-20261007/kernel.jar') as old,zipfile.ZipFile(N/'kernel.jar') as new:
    before={p:old.read(p) for p in old.namelist() if p.endswith('.class')};after={p:new.read(p) for p in new.namelist() if p.endswith('.class')}
    added=sorted(after.keys()-before.keys());removed=sorted(before.keys()-after.keys());changed=sorted(p for p in before if before[p]!=after[p])
    assert not removed and added==['com/streamhouse/oracle/UndoHistory$Action.class']
    assert set(changed)<= {'com/streamhouse/oracle/UndoHistory.class','com/streamhouse/oracle/UndoHistory$Result.class'} and 'com/streamhouse/oracle/UndoHistory.class' in changed
previous=[]
for name in ['DELIVERY-transaction-identity-resume-20261007.json','DELIVERY-cross-window-range-20261007.json','RESEARCH-cross-window-active-20261007.json']:
    manifest=json.loads((D.parent/name).read_text())
    for item in manifest['files']:
        path=R/item['path'];assert sha(path)==item['sha256'] and path.stat().st_size==item['bytes']
    previous.append(dict(manifest=name,files=len(manifest['files']),unchanged=True))
native=(R/'lab/out/cross-window-range-20261007/native-range.trc').read_text()
assert 'uba: 0x024007e2.0136.0a' in native and 'rci: 10 opc: 11.1 [objn: 75185 objd: 75185 tsn: 5]' in native
assert 'slt: 0x000e sqn: 0x00000b35' in native and 'uba: 0x024007e2.0136.0b ext: 9' in native
for name in ['UndoGraphResumeProbe.java','verify_undo_graph_resume19.py','seal_undo_graph_resume19.py']:shutil.copyfile(R/'lab'/name,N/('sealed-'+name))
files=[]
for path in sorted(N.rglob('*')):
    if not path.is_file() or 'secret' in path.name.lower():continue
    digest=sha(path);assert sha(path)==digest;files.append(dict(path=path.relative_to(R).as_posix(),bytes=path.stat().st_size,sha256=digest))
out=dict(proof=proof,tests=[322,0,0,0],classes_added=added,classes_changed=changed,classes_unchanged=len(before)-len(changed),previous_capsules=previous,production_deployed=False,site_accepted=False,released=False,product_changed=False,full_dictionary_event_publication_recovery_verified=False,double_sha256_verified=True,files_count=len(files),files=files)
D.write_text(json.dumps(out,indent=2))
for item in files:
    path=R/item['path'];assert sha(path)==item['sha256'] and path.stat().st_size==item['bytes']
print(json.dumps(dict(files=len(files),kernel=proof['kernel_sha256'],added=added,changed=changed,unchanged=out['classes_unchanged'],postcheck=True)))
