"""Double streamed SHA seal of real empty capture, failed rounds, new release and target evidence."""
import hashlib,json,shutil,subprocess
from pathlib import Path
R=Path(__file__).resolve().parent.parent;F=R/'lab/out/empty-dml-capture-20261005';O=R/'lab/out/oracle-empty-target-20261005';B=R/'lab/out/release-empty-foreign-begin-r2-20261005';OLD=R/'lab/out/release-empty-foreign-begin-20261005'
def sha(p):
    h=hashlib.sha256()
    with p.open('rb') as f:
        for b in iter(lambda:f.read(1024*1024),b''):h.update(b)
    return h.hexdigest()
proof=json.loads((O/'proof.json').read_text());assert proof['target_rows_applied_members_commits']==[0,0,0,1]
assert sha(F/'executed-driver.py')==(F/'driver-sha256.txt').read_text()
cmd=['D:/work/Java/jdk-17.0.7/bin/java.exe','-cp',';'.join(str(O/(n+'.jar')) for n in ['kernel','adapter','spi'])+';'+str(O/'classes'),'OracleProjectionProbe','public',str(O/'sequence-51.oracap')]
assert not (O/'public-new-kernel.rc.txt').exists();(O/'public-new-kernel.command.json').write_text(json.dumps(cmd));p=subprocess.run(cmd,capture_output=True,timeout=60)
for ext,v in [('stdout',p.stdout),('stderr',p.stderr)]:(O/('public-new-kernel.'+ext+'.txt')).write_bytes(v)
(O/'public-new-kernel.rc.txt').write_text(str(p.returncode));assert p.returncode!=0 and not p.stdout
sources=['src/main/java/com/streamhouse/oracle/ArchivedPhysicalTransactions.java','src/test/java/com/streamhouse/oracle/ArchivedPhysicalTransactionsTest.java']+['lab/'+n for n in ['run_empty_dml_capture19.py','RawDmlControlProbe.java','build_empty_foreign_begin.py','compare_control_native.py','recheck_empty_dml_capture.py','verify_empty_dml_target19.py','verify_empty_capture_refusals.py','verify_empty_kernel_regressions.py','compare_empty_target.py','verify_empty_target_comparator.py','seal_empty_capture_target.py']]
seals=[];dest=O/'sealed-current-sources';dest.mkdir()
for name in sources:
    p=R/name;out=dest/name;out.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(p,out)
    blob=subprocess.run(['git','show','HEAD:'+name],cwd=R,capture_output=True)
    seals.append({'path':name,'actual_source_sha256':sha(p),'prior_head_blob_sha256':hashlib.sha256(blob.stdout).hexdigest() if blob.returncode==0 else None,'stage':'current post-execution source; executed source driver and failed test/target drivers retained separately'})
files=[];commands=[]
for root in [F,O,B,OLD]:
    for p in sorted(root.rglob('*')):
        if not p.is_file() or 'm2' in p.relative_to(root).parts:continue
        assert 'secret' not in p.name.lower();a=sha(p);assert a==sha(p)
        files.append({'path':p.relative_to(R).as_posix(),'bytes':p.stat().st_size,'sha256':a})
    for p in sorted(root.glob('*.rc.txt')):
        n=p.name[:-7];commands.append({'path':p.relative_to(R).as_posix(),'rc':int(p.read_text().strip()),'stdout_bytes':(root/(n+'.stdout.txt')).stat().st_size,'stderr_bytes':(root/(n+'.stderr.txt')).stat().st_size})
product=Path('D:/work/vibecode/StreamHouse');product_paths=['server/streamhouse-spi/src/main/java/com/streamhouse/spi/EventEnvelope.java','server/streamhouse-pipeline/src/main/java/com/streamhouse/pipeline/service/PublishGateService.java','agent/src/main.cpp']
manifest={'source_driver_commit':'f658a9a','source_proof':json.loads((F/'proof.json').read_text()),'source_initial_batch_failed_preserved':True,'current_release':json.loads((B/'proof.json').read_text()),'target':proof,'regression':json.loads((O/'regression-proof.json').read_text()),'comparator':json.loads((O/'comparator-proof.json').read_text()),'raw_refusals':json.loads((O/'raw-refusals.json').read_text()),'sha256_checked_twice':True,'source_seal':seals,'commands':commands,'files':files,'product_read_only_snapshot':{'head':subprocess.check_output(['git','rev-parse','HEAD'],cwd=product).decode().strip(),'files':[{'path':name,'actual_source_sha256':sha(product/name)} for name in product_paths],'checkout_changed_by_this_work':False},'full_product_site_release_accepted':False}
(R/'docs/workorders/evidence/DELIVERY-empty-capture-target-20261005.json').write_text(json.dumps(manifest,indent=2)+'\n',encoding='utf-8');print(json.dumps({'files':len(files),'commands':len(commands),'double_sha_equal':True}))
