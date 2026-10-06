"""Seal three typed pairs, unchanged prior classes, and the preserved first diagnostic failure."""
import hashlib,json,re,shutil,xml.etree.ElementTree as ET
from pathlib import Path
R=Path(__file__).resolve().parent.parent;N=R/'lab/out/index-maintenance-pairs-20261006';H=R/'lab/out/v3-product-history-20261006'
D=R/'docs/workorders/evidence/DELIVERY-dictionary-maintenance-pairs-20261006.json';assert not D.exists()
def sha(path):
    h=hashlib.sha256()
    with path.open('rb') as stream:
        for b in iter(lambda:stream.read(1048576),b''):h.update(b)
    return h.hexdigest()
prior=[]
for name in ['DELIVERY-index-maintenance-begin-20261006.json','RESEARCH-v3-product-index-maintenance-20261006.json','DELIVERY-v3-product-acquisition-20261006.json']:
    p=R/'docs/workorders/evidence'/name;m=json.loads(p.read_text())
    for f in m['files']:assert sha(R/f['path'])==f['sha256'] and (R/f['path']).stat().st_size==f['bytes'],f['path']
    prior.append(dict(path=p.relative_to(R).as_posix(),files=len(m['files']),unchanged=True))
tests=[0,0,0,0]
for p in (N/'kernel-reports').glob('TEST-*.xml'):
    root=ET.parse(p).getroot()
    for i,k in enumerate(['tests','failures','errors','skipped']):tests[i]+=int(root.attrib[k])
assert tests==[305,0,0,0]
lines=(N/'real-pairs-r2.stdout.txt').read_text().splitlines();assert len(lines)==3
expected=[('LOCK',126592,380,4215085,1,0,0,0,0),('PREVIOUS_LINK',126619,224,4215086,1,0,4215085,4215087,0),('BRANCH_INSERT',126619,460,4194753,1,299,0,4215087,75281)]
for line,(kind,block,offset,dba,itl,slot,old,new,parent) in zip(lines,expected):
    assert line.startswith(f'70:{block}:{offset}|') and f'kind={kind},' in line
    assert 'xid=Xid[usn=9, slot=9, sequence=660]' in line and 'objectId=50, dataObjectId=50, tablespace=0' in line
    assert f'indexDba=4194752, block={dba}, itl={itl}, slot={slot}, previousLink={old}, nextLink={new}, separatorParent={parent}' in line
    native=(H/f'native-maintenance-70-{block}-{offset}.txt').read_text()
    assert f'DBA:0x{dba:08x}' in native and 'seghdr dba: 0x004001c0' in native and 'xid:  0x0009.009.00000294' in native
    if kind=='LOCK':assert 'lock itl 1' in native and 'operation = 0x5, pre-split' in native
    elif kind=='PREVIOUS_LINK':assert 'set kdxleprv=0x40512f, itl=1' in native and '01 01 00 00 2d 51 40 00' in native
    else:assert 'insert into slot 299, child dba 0x40512f' in native and '04 c3 08 35 52 fe' in native
assert (N/'real-pairs-r2.rc.txt').read_text()=='0'
assert (N/'real-pairs.rc.txt').read_text()=='1' and (N/'real-pairs.stdout.txt').stat().st_size==0
assert 'UNSUPPORTED_UNDO_HEADER_FLAGS' in (N/'real-pairs.stderr.txt').read_text()
compat=json.loads((N/'class-compatibility.json').read_text());assert compat['previous_classes']==115 and compat['unchanged'] and len(compat['added'])==2
for p in [R/'src/main/java/com/streamhouse/oracle/DictionaryIndexMaintenancePair.java',R/'src/test/java/com/streamhouse/oracle/DictionaryIndexMaintenancePairTest.java',R/'lab/DictionaryMaintenancePairProbe.java',R/'lab/build_dictionary_maintenance_pairs19.py',Path(__file__)]:shutil.copyfile(p,N/('sealed-'+p.name))
files=[]
for p in sorted(N.rglob('*')):
    if not p.is_file() or 'secret' in p.name.lower():continue
    digest=sha(p);assert digest==sha(p);files.append(dict(path=p.relative_to(R).as_posix(),bytes=p.stat().st_size,sha256=digest))
out=dict(tests=tests,kernel_sha256=sha(N/'kernel.jar'),native_pair_differences=0,typed_pairs=3,complete_split=False,events=0,class_compatibility=compat,first_failure='diagnostic scope decoded foreign undo; UNSUPPORTED_UNDO_HEADER_FLAGS, rc1/stdout0',source_actions=0,product_build=False,target_actions=0,checkpoint_ack=False,site_accepted=False,release_accepted=False,previous=prior,double_sha256_verified=True,files_count=len(files),files=files)
D.write_text(json.dumps(out,indent=2))
for f in files:assert sha(R/f['path'])==f['sha256']
print(json.dumps(dict(files=len(files),tests=tests,kernel=out['kernel_sha256'],native_differences=0,events=0)))
