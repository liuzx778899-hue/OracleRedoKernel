"""Seal accepted mixed capture evidence without rerunning any experiment."""
import hashlib,json,shutil,subprocess
from pathlib import Path
R=Path(__file__).resolve().parent.parent
O=R/'lab/out/mixed-dml-capture-20261005'
def sha(p):
    h=hashlib.sha256()
    with p.open('rb') as f:
        for b in iter(lambda:f.read(1024*1024),b''):h.update(b)
    return h.hexdigest()
sources=['compare_mixed_capture_rows.py','verify_mixed_capture_graph.py','RawDmlRowsResearchProbe.java','RawDmlEndProbe.java','RawDmlLeafProbe.java','CapturedBatchProbe.java','compare_capture_archive.py']
seals=[]
for name in sources:
    p=R/'lab'/name
    if not p.exists():continue
    dest=O/('sealed-current-'+name)
    assert not dest.exists()
    shutil.copyfile(p,dest)
    blob=subprocess.check_output(['git','show','HEAD:lab/'+name],cwd=R)
    seals.append({'path':p.relative_to(R).as_posix(),'actual_source_sha256':sha(p),'git_blob_sha256':hashlib.sha256(blob).hexdigest(),'lf_normalized_equal':p.read_bytes().replace(b'\r\n',b'\n')==blob.replace(b'\r\n',b'\n'),'seal_stage':'post-execution current source; command/source history retained, not an execution-time claim'})
files=[]
for p in sorted(O.rglob('*')):
    if not p.is_file():continue
    a=sha(p);b=sha(p);assert a==b
    files.append({'path':p.relative_to(R).as_posix(),'bytes':p.stat().st_size,'sha256':a})
commands=[]
for p in sorted(O.glob('*.rc.txt')):
    name=p.name[:-7]
    commands.append({'name':name,'rc':int(p.read_text()),'stdout_bytes':(O/(name+'.stdout.txt')).stat().st_size,'stderr_bytes':(O/(name+'.stderr.txt')).stat().st_size})
proof=json.loads((O/'delivery-proof.json').read_text())
assert sha(O/'executed-driver.py')==(O/'driver-sha256.txt').read_text().strip()
assert proof['mixed_commit_changes']==3 and proof['rollback_business_changes']==0
manifest={'runtime_kernel_commit':'4f796910909cd6937e1ccb8d8d2b697eda176599','source_driver_commit':'30e42f4','independent_verification_commit':subprocess.check_output(['git','rev-parse','HEAD'],cwd=R).decode().strip(),'kernel_sha256':proof['kernel_sha256'],'kernel_tests_previously_passed':251,'tests_rerun':False,'sha256_checked_twice':True,'proof':proof,'source_seal':seals,'commands':commands,'files':files,'full_product_accepted':False}
(R/'docs/workorders/evidence/DELIVERY-mixed-rollback-capture-20261005.json').write_text(json.dumps(manifest,indent=2)+'\n',encoding='utf-8')
print(json.dumps({'files':len(files),'commands':len(commands),'double_sha_equal':True,'source_actions':0}))
