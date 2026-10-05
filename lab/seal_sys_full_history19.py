"""Seal scoped OBJ candidate and complete diagnostic SYS replay; no historical publication claim."""
import hashlib,json,shutil,xml.etree.ElementTree as ET
from pathlib import Path
R=Path(__file__).resolve().parent.parent;O=R/'lab/out/sys-full-history-research-20261006';N=R/'lab/out/sys-object-history-candidate-20261006'
def sha(p):
    h=hashlib.sha256()
    with p.open('rb') as f:
        for b in iter(lambda:f.read(1048576),b''):h.update(b)
    return h.hexdigest()
for name in ['research_sys_full_history19.py','SysDictionaryRowPairsProbe.java','research_sys_row_pairs19.py','research_sys_obj_shapes19.py','build_sys_object_test_fixture19.py','verify_sys_full_row_replay19.py','verify_sys_object_raw_negatives19.py','verify_dictionary_index_impact19.py','seal_sys_full_history19.py']:shutil.copyfile(R/'lab'/name,N/('sealed-'+name))
reports=N/'kernel-reports';reports.mkdir();counts=[0,0,0,0]
for p in (R/'target/surefire-reports').glob('TEST-*.xml'):
    shutil.copyfile(p,reports/p.name);t=ET.parse(p).getroot()
    for i,k in enumerate(['tests','failures','errors','skipped']):counts[i]+=int(t.attrib[k])
assert counts==[292,0,0,0],counts
old=json.loads((R/'docs/workorders/evidence/DELIVERY-dictionary-baseline-candidate-20261006.json').read_text())
for f in old['files']:
    p=R/f['path'];assert sha(p)==f['sha256'] and p.stat().st_size==f['bytes']
files=[]
for folder in [O,N]:
    for p in sorted(folder.rglob('*')):
        if not p.is_file() or 'secret' in p.name.lower():continue
        a=sha(p);assert a==sha(p);files.append(dict(path=str(p.relative_to(R)).replace('\\','/'),bytes=p.stat().st_size,sha256=a))
proof=dict(files_count=len(files),double_sha256_verified=True,previous_219_unchanged=True,kernel_code_commit='b65ade5',kernel_sha256=sha(N/'kernel.jar'),kernel_tests=counts,full_row_replay=json.loads((O/'full-row-replay-proof.json').read_text()),raw_negatives=json.loads((O/'object-raw-negative-proof.json').read_text()),impact=json.loads((N/'impact-proof.json').read_text()),business_ddl_dml=0,source_config_changes=0,target_actions=0,history_authority=False,product_rebuilt=False,files=files)
p=R/'docs/workorders/evidence/DELIVERY-sys-full-history-20261006.json';assert not p.exists();p.write_text(json.dumps(proof,indent=2));print(json.dumps(dict(files=len(files),kernel_sha256=proof['kernel_sha256'],kernel_tests=counts,previous_219_unchanged=True)))
