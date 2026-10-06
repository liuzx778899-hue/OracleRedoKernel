"""Seal typed leaf rows and independent native verification without asserting split closure."""
import hashlib,json,shutil,sys,xml.etree.ElementTree as ET
from pathlib import Path
R=Path(__file__).resolve().parent.parent;N=R/'lab/out/index-leaf-rows-candidate-20261006'
D=R/'docs/workorders/evidence/DELIVERY-dictionary-leaf-rows-20261006.json'
if len(sys.argv)>1:
    assert sys.argv[1:]==['r2'];N=R/'lab/out/index-leaf-rows-r2-20261006';D=R/'docs/workorders/evidence/DELIVERY-dictionary-leaf-rows-r2-20261006.json'
assert not D.exists()
def sha(path):
    h=hashlib.sha256()
    with path.open('rb') as stream:
        for b in iter(lambda:stream.read(1048576),b''):h.update(b)
    return h.hexdigest()
prior=[]
names=['DELIVERY-dictionary-maintenance-pairs-20261006.json','DELIVERY-index-maintenance-begin-20261006.json','RESEARCH-v3-product-index-maintenance-20261006.json','DELIVERY-v3-product-acquisition-20261006.json']
if len(sys.argv)>1:names.insert(0,'DELIVERY-dictionary-leaf-rows-20261006.json')
for name in names:
    p=R/'docs/workorders/evidence'/name;m=json.loads(p.read_text())
    for f in m['files']:assert sha(R/f['path'])==f['sha256'] and (R/f['path']).stat().st_size==f['bytes'],f['path']
    prior.append(dict(path=p.relative_to(R).as_posix(),files=len(m['files']),unchanged=True))
tests=[0,0,0,0]
for p in (N/'kernel-reports').glob('TEST-*.xml'):
    root=ET.parse(p).getroot()
    for i,k in enumerate(['tests','failures','errors','skipped']):tests[i]+=int(root.attrib[k])
assert tests==[309,0,0,0]
proof=json.loads((N/'native-leaf-proof.json').read_text());assert proof['rows_compared']==891 and proof['native_byte_and_scalar_differences']==0 and len(proof['negative_detected'])==4
assert (N/'real-leaves.rc.txt').read_text()=='0'
if len(sys.argv)>1:assert 'IMAGE|OLD|4215085|null|4215087|226' in (N/'real-leaves.stdout.txt').read_text()
compat=json.loads((N/'class-compatibility.json').read_text());assert compat['previous_classes']==117 and compat['unchanged'] and len(compat['added'])==2
for p in [R/'src/main/java/com/streamhouse/oracle/DictionaryIndexLeafRows.java',R/'src/test/java/com/streamhouse/oracle/DictionaryIndexLeafRowsTest.java',R/'lab/DictionaryLeafRowsProbe.java',R/'lab/build_dictionary_leaf_rows19.py',R/'lab/verify_dictionary_leaf_rows19.py',Path(__file__)]:shutil.copyfile(p,N/('sealed-'+p.name))
files=[]
for p in sorted(N.rglob('*')):
    if not p.is_file() or 'secret' in p.name.lower():continue
    digest=sha(p);assert digest==sha(p);files.append(dict(path=p.relative_to(R).as_posix(),bytes=p.stat().st_size,sha256=digest))
out=dict(tests=tests,kernel_sha256=sha(N/'kernel.jar'),native_leaf_proof=proof,class_compatibility=compat,complete_split=False,events=0,source_actions=0,product_build=False,target_actions=0,checkpoint_ack=False,site_accepted=False,release_accepted=False,previous=prior,double_sha256_verified=True,files_count=len(files),files=files)
D.write_text(json.dumps(out,indent=2))
for f in files:assert sha(R/f['path'])==f['sha256']
print(json.dumps(dict(files=len(files),tests=tests,kernel=out['kernel_sha256'],native_differences=0,events=0)))
