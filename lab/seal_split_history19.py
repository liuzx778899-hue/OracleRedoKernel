"""Seal tested raw history, full row rulers, refusals and failures; never include credential content."""
import hashlib,json,shutil,xml.etree.ElementTree as ET
from pathlib import Path
R=Path(__file__).resolve().parent.parent;N=R/'lab/out/split-history-candidate-20261007';A=N/'r6'
D=R/'docs/workorders/evidence/DELIVERY-split-history-20261007.json';assert not D.exists()
def sha(p):
    with p.open('rb') as stream:return hashlib.file_digest(stream,'sha256').hexdigest()
tests=[0,0,0,0]
for p in (A/'reports').glob('TEST-*.xml'):
    root=ET.parse(p).getroot()
    for i,k in enumerate(['tests','failures','errors','skipped']):tests[i]+=int(root.attrib[k])
assert tests==[311,0,0,0]
positive=json.loads((N/'validation/positive-proof.json').read_text());native=json.loads((N/'validation/native-full-row-proof.json').read_text())
negative=json.loads((N/'negative-r2/negative-proof.json').read_text());projection=json.loads((N/'projection-r2/projection-proof.json').read_text());impact=json.loads((N/'regression/regression-proof.json').read_text())
assert positive['events']==projection['events']==2 and native['full_values']==764 and len(negative['cases'])==26
assert all(c['structure_rc']==0 and c['complete_history_rc']==1 and c['events']==0 for c in negative['cases'])
assert projection['history_equal'] and projection['business_event_bytes_equal'] and projection['envelope_semantics_equal'] and projection['capture_input_changes_with_batch']
assert all(not c['asof_verified'] and 'ORA-01555' in c['reason'] for c in positive['commits'])
assert all(c['differences']==0 for c in native['commits']) and impact['unchanged_classes']==114
for p in sorted(A.glob('*.java')):
    source=R/('src/main/java/com/streamhouse/oracle/'+p.name) if (R/('src/main/java/com/streamhouse/oracle/'+p.name)).exists() else R/('src/test/java/com/streamhouse/oracle/'+p.name) if p.name.endswith('Test.java') else R/'lab'/p.name
    assert sha(source)==sha(p),p.name
assert sha(R/'src/test/resources/col-delete-begin-profile.txt')==sha(A/'col-delete-begin-profile.txt')
prior=json.loads((R/'docs/workorders/evidence/DELIVERY-autonomous-dictionary-split-20261006.json').read_text())
for f in prior['files']:
    p=R/f['path'];assert p.stat().st_size==f['bytes'] and sha(p)==f['sha256'],f['path']
assert (A/'capture-business.stdout.txt').read_bytes()==(N/'validation/business-repeat.stdout.txt').read_bytes()
for name in ['build_split_history19.py','verify_split_history19.py','verify_split_history_negatives19.py','verify_split_native_rows19.py','verify_split_projection19.py','verify_split_regressions19.py','seal_split_history19.py','HistoricalRowsParityProbe.java']:
    shutil.copyfile(R/'lab'/name,N/('sealed-'+name))
failures=dict(r1='OBJ$ six-column ckix144 unsupported; all three complete entry failures/stdout0 retained',r2='New test wrongly required existing generic heap to reject ckix144; 310/1/0/0 report and failed source retained; corrected scoped expectation',r3='DROP first-record flags004a size316 unsupported; all three complete entry failures/stdout0 retained',r5='New test wrongly expected start-only decoding to bind USN not present in its control bytes; 311/1/0/0 reports retained; full restore XID negative independently refuses',asof='Three read-only old SCNs ORA-01555; original SQL/stdout/stderr/partial results retained; no late acquisition or source replay',negative_driver='StopIteration: actual end rebuild and restore adjacent, no intervening real record; first frozen driver retained; r2 inserts observed13.6 vector and repacks original trailing records/checksums',native_driver='AssertionError: native [ 4] length spacing not recognized; first frozen driver retained, r2 handles spaces and proves764 values',erasure_driver='AssertionError: wrong old-window four-marker expectation; actual source window count is taken from structural records, accepted structure results reused',projection_driver='AssertionError: captureInput is bound to changed batch/journal SHA and must change after marker erasure; semantic event/schema/complete image equality and exact binding validated without rerunning passed JVM calls',class_driver='AssertionError: expected6 classes, actual8 including split outer/Context nested metadata; corrected class boundary audit, prior passed regression outputs reused')
(N/'failure-dispositions.json').write_text(json.dumps(failures,indent=2))
files=[]
for p in sorted(N.rglob('*')):
    if not p.is_file() or 'secret' in p.name.lower():continue
    digest=sha(p);assert sha(p)==digest;files.append(dict(path=p.relative_to(R).as_posix(),bytes=p.stat().st_size,sha256=digest))
cases=[{k:v for k,v in c.items() if k!='mutations'}|dict(mutation_count=len(c.get('mutations',[]))) for c in negative['cases']]
out=dict(kernel_sha256=sha(A/'kernel.jar'),tests=tests,source_window=[70,71],baseline_sha256=native['baseline_sha256'],baseline_scn=2566695,events=2,event_columns=[4,3],business_positions=['70:126642:16:4','71:16:16:4'],business_commit_scns=[2567441,2567465],sys_commit_scns=[2567428,2567435,2567462],native=native,positive=positive,negative=cases,projection={k:v for k,v in projection.items() if k!='markers'},erased_markers=sum(c['markers'] for c in projection['markers']),regression=impact,failure_dispositions=failures,prior_files_unchanged=len(prior['files']),source_actions=0,source_configuration_changes=0,target_actions=0,product_build=False,product_http=False,checkpoint_ack=False,site_accepted=False,release_accepted=False,files_count=len(files),double_sha256_verified=True,files=files)
D.write_text(json.dumps(out,indent=2))
for f in files:assert sha(R/f['path'])==f['sha256'] and (R/f['path']).stat().st_size==f['bytes']
print(json.dumps(dict(files=len(files),tests=tests,kernel=out['kernel_sha256'],events=2,negative=len(cases),markers=out['erased_markers'],native_values=764,prior_unchanged=len(prior['files']))),flush=True)
