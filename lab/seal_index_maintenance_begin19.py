"""Seal bounded begin/lifecycle support without claiming typed index maintenance or events."""
import hashlib,json,re,shutil,xml.etree.ElementTree as ET
from pathlib import Path
R=Path(__file__).resolve().parent.parent;N=R/'lab/out/index-maintenance-candidate-20261006';H=R/'lab/out/v3-product-history-20261006'
D=R/'docs/workorders/evidence/DELIVERY-index-maintenance-begin-20261006.json';assert not D.exists()
def sha(path):
    h=hashlib.sha256()
    with path.open('rb') as stream:
        for b in iter(lambda:stream.read(1048576),b''):h.update(b)
    return h.hexdigest()
prior=[]
for name in ['RESEARCH-v3-product-index-maintenance-20261006.json','DELIVERY-v3-product-acquisition-20261006.json']:
    p=R/'docs/workorders/evidence'/name;m=json.loads(p.read_text())
    for f in m['files']:assert sha(R/f['path'])==f['sha256'] and (R/f['path']).stat().st_size==f['bytes'],f['path']
    prior.append(dict(path=p.relative_to(R).as_posix(),files=len(m['files']),unchanged=True))
tests=[0,0,0,0]
for p in (N/'kernel-reports').glob('TEST-*.xml'):
    root=ET.parse(p).getroot()
    for i,key in enumerate(['tests','failures','errors','skipped']):tests[i]+=int(root.attrib[key])
assert tests==[301,0,0,0]
lines=(N/'maintenance-lifecycle.stdout.txt').read_text().splitlines();nodes=[s.split('|') for s in lines if s.startswith('NODE|')];assert len(nodes)==5
for node in nodes:
    seq,block,offset,vector=map(int,node[1:5]);native=(H/f'native-maintenance-{seq}-{block}-{offset}.txt').read_text()
    change=native.split('CHANGE #'+str(vector)+' ',1)[1].split('CHANGE #',1)[0]
    uba=re.search(r'Uba\[dba=(\d+), sequence=(\d+), record=(\d+)\]',node[5]);dba,sequence,record=map(int,uba.groups())
    assert f'DBA:0x{dba:08x}' in change and f'seq: 0x{sequence:04x} rec: 0x{record:02x}' in change
    assert '0x0009.009.00000294' in change
    previous=re.search(r'prev dba:  0x([0-9a-f]+) rci: (\d+)',change);pdba,rci=int(previous[1],16),int(previous[2])
    if node[6]=='null':assert pdba==rci==0
    else:
        previous_uba=re.search(r'Uba\[dba=(\d+), sequence=(\d+), record=(\d+)\]',node[6]);pdb,ps,pr=map(int,previous_uba.groups())
        assert (pdba==pdb and rci==0 or pdba==0 and pdb==dba and rci==pr) and ps==sequence
assert lines[-1]=='END|70|126620|204|2567427|5|5|Uba[dba=37760849, sequence=230, record=3]'
assert [s for s in lines if s.startswith('CONTROL|')]==['CONTROL|70|126592|380|5.2|1098','CONTROL|70|126593|240|5.2|10','CONTROL|70|126610|328|5.2|10']
assert (N/'maintenance-lifecycle.rc.txt').read_text()=='0'
assert (N/'full-history-refusal.rc.txt').read_text()=='1' and (N/'full-history-refusal.stdout.txt').stat().st_size==0
assert 'DICTIONARY_DISCOVERY_INDEX_COVERAGE' in (N/'full-history-refusal.stderr.txt').read_text()
for p in [R/'src/main/java/com/streamhouse/oracle/TransactionStart.java',R/'src/test/java/com/streamhouse/oracle/TransactionStartTest.java',R/'lab/SysUndoHistoryProbe.java',R/'lab/build_index_maintenance_candidate19.py',Path(__file__)]:shutil.copyfile(p,N/('sealed-'+p.name))
files=[]
for p in sorted(N.rglob('*')):
    if not p.is_file() or 'secret' in p.name.lower():continue
    digest=sha(p);assert digest==sha(p);files.append(dict(path=p.relative_to(R).as_posix(),bytes=p.stat().st_size,sha256=digest))
out=dict(tests=tests,kernel_sha256=sha(N/'kernel.jar'),native_original_identity_predecessor_differences=0,original_nodes=5,live_nodes=5,begin_flags=1098,opaque_control_word='0x3f98',lifecycle_closed=True,events=0,full_history='REFUSED:DICTIONARY_DISCOVERY_INDEX_COVERAGE',typed_branch_and_link_verified=False,source_actions=0,product_build=False,target_actions=0,checkpoint_ack=False,site_accepted=False,release_accepted=False,previous=prior,double_sha256_verified=True,files_count=len(files),files=files)
D.write_text(json.dumps(out,indent=2))
for f in files:assert sha(R/f['path'])==f['sha256']
print(json.dumps(dict(files=len(files),tests=tests,kernel=out['kernel_sha256'],native_differences=0,events=0)))
