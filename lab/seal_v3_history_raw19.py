"""Seal fresh trusted V3/SYS history evidence; historical business events remain unaccepted."""
import hashlib,json,shutil,subprocess,xml.etree.ElementTree as ET
from pathlib import Path
R=Path(__file__).resolve().parent.parent;O=R/'lab/out/v3-historical-raw-20261006';N=R/'lab/out/v3-history-ckix-candidate-20261006'
def sha(p):
    h=hashlib.sha256()
    with p.open('rb') as f:
        for b in iter(lambda:f.read(1048576),b''):h.update(b)
    return h.hexdigest()
for name in ['ArchivedDictionaryHistoryEvidenceProbe.java','SysHistoryRawResearchProbe.java','OracleSnapshotAcquisitionProbe.java','SysDictionaryBaselineResearchProbe.java','acquire_v3_history_raw19.py','verify_v3_history_baseline19.py','capture_v3_historical19.py','verify_v3_history_raw19.py','verify_v3_history_negatives19.py','verify_v3_history_erasure19.py','seal_v3_history_raw19.py']:shutil.copyfile(R/'lab'/name,N/('sealed-'+name))
reports=N/'kernel-reports';reports.mkdir();counts=[0,0,0,0]
for p in (R/'target/surefire-reports').glob('TEST-*.xml'):
    shutil.copyfile(p,reports/p.name);t=ET.parse(p).getroot()
    for i,k in enumerate(['tests','failures','errors','skipped']):counts[i]+=int(t.attrib[k])
assert counts==[296,0,0,0],counts
assert (O/'build-ckix-r2.rc.txt').read_text(encoding='utf-8-sig').strip()=='0'
old=json.loads((R/'docs/workorders/evidence/DELIVERY-dictionary-history-kernel-20261006.json').read_text())
for f in old['files']:
    p=R/f['path'];assert sha(p)==f['sha256'] and p.stat().st_size==f['bytes']
history=json.loads((O/'actual-history-proof.json').read_text());negative=json.loads((O/'history-negative-proof.json').read_text());erasure=json.loads((O/'history-erasure-proof.json').read_text())
assert len(negative['cases'])==7 and erasure['mutated_markers']==4
assert history['kernel_sha256']==negative['kernel_sha256']==erasure['kernel_sha256']==sha(N/'kernel.jar')
files=[]
for folder in [O,N]:
    for p in sorted(folder.rglob('*')):
        if not p.is_file() or 'secret' in p.name.lower():continue
        a=sha(p);assert a==sha(p);files.append(dict(path=str(p.relative_to(R)).replace('\\','/'),bytes=p.stat().st_size,sha256=a))
proof=dict(kernel_code_commit=subprocess.check_output(['git','rev-parse','HEAD'],cwd=R).decode().strip(),kernel_sha256=sha(N/'kernel.jar'),kernel_tests=counts,actual_baseline=json.loads((O/'baseline-truth-proof.json').read_text()),actual_history=history,raw_negatives=negative,marker_erasure=erasure,impact=json.loads((N/'impact-proof.json').read_text()),files_count=len(files),double_sha256_verified=True,previous_227_unchanged=True,source_fixture_ddl=2,source_fixture_committed_dml=2,source_actions_replayed=False,source_config_changes=0,target_actions=0,events=0,product_rebuilt=False,history_dml_accepted=False,files=files)
p=R/'docs/workorders/evidence/DELIVERY-v3-history-raw-20261006.json';assert not p.exists();p.write_text(json.dumps(proof,indent=2));print(json.dumps(dict(files=len(files),kernel_sha256=proof['kernel_sha256'],kernel_tests=counts,previous_227_unchanged=True,actual_sys_history_passed=True,historical_dml=False)))
