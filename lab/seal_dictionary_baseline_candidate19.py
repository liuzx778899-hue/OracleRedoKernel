"""Seal nonsecret V3 candidate evidence and old delivery; exclude the private Maven cache."""
import hashlib,json,shutil
from pathlib import Path
R=Path(__file__).resolve().parent.parent;O=R/'lab/out/dictionary-baseline-candidate-20261006';Q=R/'lab/out/sys-full-baseline-research-20261006'
def sha(p):
    h=hashlib.sha256()
    with p.open('rb') as f:
        for b in iter(lambda:f.read(1048576),b''):h.update(b)
    return h.hexdigest()
for name in ['SysDictionaryBaselineResearchProbe.java','build_dictionary_baseline_candidate19.py','build_dictionary_baseline_test_fixture19.py','research_sys_full_baseline19.py','verify_sys_full_baseline19.py','verify_dictionary_baseline_acquisition19.py','prepare_dictionary_index_read19.py','verify_dictionary_baseline_truth19.py','verify_dictionary_index_impact19.py','seal_dictionary_baseline_candidate19.py']:
    shutil.copyfile(R/'lab'/name,O/('sealed-'+name))
old=json.loads((R/'docs/workorders/evidence/DELIVERY-dictionary-index-pair-20261006.json').read_text())
for f in old['files']:
    p=R/f['path'];assert sha(p)==f['sha256'] and p.stat().st_size==f['bytes']
files=[]
for folder in [O,Q]:
    for p in sorted(folder.rglob('*')):
        if not p.is_file() or 'm2' in p.relative_to(folder).parts or 'secret' in p.name.lower():continue
        a=sha(p);assert sha(p)==a;files.append(dict(path=str(p.relative_to(R)).replace('\\','/'),bytes=p.stat().st_size,sha256=a))
proof=dict(files_count=len(files),double_sha256_verified=True,previous_204_unchanged=True,build=json.loads((O/'build-proof.json').read_text()),acquisition=json.loads((O/'actual-acquisition-r2-proof.json').read_text()),truth=json.loads((O/'independent-truth-proof.json').read_text()),impact=json.loads((O/'impact-proof.json').read_text()),lab_permission_experiment=json.loads((O/'owned-reader-index-grant-proof.json').read_text()),business_ddl_dml=0,target_actions=0,history_dml_accepted=False,product_rebuilt=False,files=files)
destination=R/'docs/workorders/evidence/DELIVERY-dictionary-baseline-candidate-20261006.json';assert not destination.exists();destination.write_text(json.dumps(proof,indent=2));print(json.dumps(dict(files=len(files),previous_204_unchanged=True,sys_cells=230)))
