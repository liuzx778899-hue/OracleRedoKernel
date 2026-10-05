"""Seal the history component candidate and its failures; no historical DML acceptance."""
import hashlib,json,shutil,subprocess,xml.etree.ElementTree as ET
from pathlib import Path
R=Path(__file__).resolve().parent.parent;O=R/'lab/out/sys-history-kernel-candidate-20261006'
def sha(p):
    h=hashlib.sha256()
    with p.open('rb') as f:
        for b in iter(lambda:f.read(1048576),b''):h.update(b)
    return h.hexdigest()
for name in ['ArchivedDictionaryHistoryProbe.java','verify_dictionary_history_kernel19.py','verify_dictionary_index_impact19.py','seal_dictionary_history_kernel19.py']:shutil.copyfile(R/'lab'/name,O/('sealed-'+name))
counts=[0,0,0,0];reports=O/'kernel-reports';reports.mkdir()
for p in (R/'target/surefire-reports').glob('TEST-*.xml'):
    shutil.copyfile(p,reports/p.name);t=ET.parse(p).getroot()
    for i,k in enumerate(['tests','failures','errors','skipped']):counts[i]+=int(t.attrib[k])
assert counts==[295,0,0,0],counts
assert (O/'build-r4.rc.txt').read_text(encoding='utf-8-sig').strip()=='0'
history=json.loads((O/'history-proof-r3.json').read_text());assert history['kernel_sha256']==sha(O/'kernel.jar')
old=json.loads((R/'docs/workorders/evidence/DELIVERY-sys-full-history-20261006.json').read_text())
for f in old['files']:
    p=R/f['path'];assert sha(p)==f['sha256'] and p.stat().st_size==f['bytes']
files=[]
for p in sorted(O.rglob('*')):
    if not p.is_file() or 'secret' in p.name.lower():continue
    a=sha(p);assert a==sha(p);files.append(dict(path=str(p.relative_to(R)).replace('\\','/'),bytes=p.stat().st_size,sha256=a))
proof=dict(kernel_code_commit=subprocess.check_output(['git','rev-parse','HEAD'],cwd=R).decode().strip(),kernel_sha256=sha(O/'kernel.jar'),kernel_tests=counts,history=history,impact=json.loads((O/'impact-proof.json').read_text()),files_count=len(files),double_sha256_verified=True,previous_220_unchanged=True,source_actions=0,target_actions=0,events=0,product_rebuilt=False,history_dml_accepted=False,files=files)
p=R/'docs/workorders/evidence/DELIVERY-dictionary-history-kernel-20261006.json';assert not p.exists();p.write_text(json.dumps(proof,indent=2))
print(json.dumps(dict(kernel_sha256=proof['kernel_sha256'],kernel_tests=counts,files=len(files),previous_220_unchanged=True)))
