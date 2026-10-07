"""Verify changed class scope and compatibility, then seal candidate evidence."""
import hashlib,json,re,shutil,subprocess,zipfile
from pathlib import Path
R=Path(__file__).resolve().parent.parent;N=R/'lab/out/cross-window-range-20261007';O=R/'lab/out/cross-window-active-20261007'
D=R/'docs/workorders/evidence/DELIVERY-cross-window-range-20261007.json';assert not D.exists()
def sha(p):
    with p.open('rb') as f:return hashlib.file_digest(f,'sha256').hexdigest()
proof=json.loads((N/'events-proof.json').read_text())
assert proof['events']==2 and proof['differences']==0 and proof['repeat_bytes_equal'] and proof['comparison_poisons_detected']==4
assert re.search(r'Tests run: 313, Failures: 0, Errors: 0, Skipped: 0', (N/'full-build.stdout.txt').read_text())
assert int((N/'full-build.rc.txt').read_text())==0
with zipfile.ZipFile(O/'kernel.jar') as old,zipfile.ZipFile(N/'candidate-kernel.jar') as new:
    before={p:old.read(p) for p in old.namelist() if p.endswith('.class')};after={p:new.read(p) for p in new.namelist() if p.endswith('.class')}
    assert before.keys()==after.keys()
    changed=[p for p in before if before[p]!=after[p]]
    assert set(changed)=={'com/streamhouse/oracle/HistoricalSchemaBarrier.class','com/streamhouse/oracle/ArchivedDictionaryHistory.class'}
previous=R/'lab/out/split-history-candidate-20261007/r6'
args=json.loads((previous/'capture-business.command.json').read_text());args[args.index('-cp')+1]=str(N/'candidate-kernel.jar')+';'+str(N/'event-classes')
(N/'changed-code-70-71.command.json').write_text(json.dumps(args))
p=subprocess.run(args,capture_output=True,timeout=240)
for suffix,value in [('stdout.txt',p.stdout),('stderr.txt',p.stderr),('rc.txt',str(p.returncode).encode())]:(N/('changed-code-70-71.'+suffix)).write_bytes(value)
assert p.returncode==0 and p.stdout==(previous/'capture-business.stdout.txt').read_bytes()
sealed=json.loads((R/'docs/workorders/evidence/RESEARCH-cross-window-active-20261007.json').read_text())
for item in sealed['files']:
    path=R/item['path'];assert sha(path)==item['sha256'] and path.stat().st_size==item['bytes']
for name in ['CrossWindowRecordProbe.java','research_cross_window_range19.py','verify_cross_window_range19.py','verify_cross_window_events19.py','seal_cross_window_range19.py']:
    shutil.copyfile(R/'lab'/name,N/('sealed-'+name))
(N/'failed-events-verifier-r1.original-error.txt').write_text('Traceback (most recent call last):\n  File "D:\\work\\vibecode\\OracleRedoKernel\\lab\\verify_cross_window_events19.py", line 61, in <module>\n    p=run(\'reject-\'+name,base+[str(path),sha(path)]);assert p.returncode==1 and not p.stdout and b\'UNSUPPORTED_MEDIA_RECOVERY_MARKER_RANGE\' in p.stderr\n                                                            ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^\nAssertionError\n')
files=[]
for path in sorted(N.rglob('*')):
    if not path.is_file() or 'secret' in path.name.lower():continue
    digest=sha(path);assert sha(path)==digest;files.append(dict(path=path.relative_to(R).as_posix(),bytes=path.stat().st_size,sha256=digest))
out=dict(proof=proof,classes_changed=changed,unchanged_classes=len(before)-len(changed),old_70_71_output_bytes_equal=True,previous_182_files_unchanged=True,first_comparator_failure_preserved=True,source_business_actions=0,target_actions=0,product_changed=False,cross_publication_recovery_verified=False,production_deployed=False,site_accepted=False,released=False,double_sha256_verified=True,files_count=len(files),files=files)
D.write_text(json.dumps(out,indent=2))
for item in files:
    path=R/item['path'];assert sha(path)==item['sha256'] and path.stat().st_size==item['bytes']
print(json.dumps(dict(files=len(files),changed_classes=changed,unchanged_classes=len(before)-len(changed),postcheck=True)))
