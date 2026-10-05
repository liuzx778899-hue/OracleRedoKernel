"""Seal frozen composite index evidence and verify prior delivery without replay."""
import hashlib,json,shutil,xml.etree.ElementTree as ET
from pathlib import Path
R=Path(__file__).resolve().parent.parent;O=R/'lab/out/dictionary-index-pair-20261006'
def sha(p):
    h=hashlib.sha256()
    with p.open('rb') as f:
        for b in iter(lambda:f.read(1048576),b''):h.update(b)
    return h.hexdigest()
old=json.loads((R/'docs/workorders/evidence/DELIVERY-cluster-pair-20261006.json').read_text())
print(list(old.keys()))
for name in ['DictionaryIndexPairsProbe.java','verify_dictionary_index_pairs19.py','verify_dictionary_index_impact19.py','seal_dictionary_index_pair19.py']:
    shutil.copyfile(R/'lab'/name,O/('sealed-'+name))
for p in (R/'target/surefire-reports').glob('TEST-*.xml'):shutil.copyfile(p,O/p.name)
tests=[0,0,0,0]
for p in O.glob('TEST-*.xml'):
    t=ET.parse(p).getroot()
    for i,k in enumerate(['tests','failures','errors','skipped']):tests[i]+=int(t.attrib[k])
assert tests==[286,0,0,0],tests
files=[]
for p in sorted(O.rglob('*')):
    if not p.is_file():continue
    assert 'secret' not in p.name.lower()
    a=sha(p);assert a==sha(p)
    files.append(dict(path=str(p.relative_to(R)).replace('\\','/'),bytes=p.stat().st_size,sha256=a))
for f in old['files']:
    p=R/f['path'];assert sha(p)==f['sha256']
    assert p.stat().st_size==f.get('bytes',f.get('size'))
result=dict(files_count=len(files),double_sha256_verified=True,previous_169_unchanged=True,kernel_code_commit='646841d',tests=tests,proof=json.loads((O/'proof.json').read_text()),impact=json.loads((O/'impact-proof.json').read_text()),source_actions=0,target_actions=0,product_rebuilt=False,files=files)
out=R/'docs/workorders/evidence/DELIVERY-dictionary-index-pair-20261006.json'
assert not out.exists();out.write_text(json.dumps(result,indent=2))
print(json.dumps(dict(files=len(files),tests=tests,previous_169_unchanged=True)))
