"""Frozen eight row pairs and nine checksum-valid semantic negatives; parent binding remains pending."""
import hashlib,json,subprocess,sys,traceback,zipfile
from pathlib import Path
R=Path(__file__).resolve().parent.parent;H=R/'lab/out/v2-historical-product-20261006';S=R/'lab/out/sys-cluster-row-research-20261006';O=R/'lab/out/sys-cluster-pair-20261006'
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
def fail(kind,value,tb):
    (O/'driver-failure-r1.txt').write_text(''.join(traceback.format_exception(kind,value,tb)));sys.__excepthook__(kind,value,tb)
sys.excepthook=fail
assert not (O/'execution-pin.json').exists();(O/'executed-driver.py').write_bytes(Path(__file__).read_bytes())
kernel=O/'kernel.jar';assert sha(kernel)=='cca2546568837c9af03a2ff19aa85eec21d53c81bc5168d06decf2287924d6d0'
with zipfile.ZipFile(R/'lab/out/sys-cluster-row-candidate-20261006/kernel.jar') as a,zipfile.ZipFile(kernel) as b:
    unchanged=[n for n in a.namelist() if n.endswith('.class')];assert all(a.read(n)==b.read(n) for n in unchanged)
cp=str(kernel)+';'+str(O/'classes');baseline=H/'snapshots/100644812901937152.snapshot';receipt='e16e6275651894614dc9a79e42381338ddbf0771aa68c971df66f35abd5c0e27';assert sha(baseline)==receipt
pins={str(p.relative_to(R)):sha(p) for p in [kernel,baseline,R/'lab/ClusterPairsProbe.java',Path(__file__),S/'row-shapes.json',S/'raw-records.json',H/'archive-66.dbf',H/'archive-67.dbf']}
(O/'execution-pin.json').write_text(json.dumps(pins,indent=2))
def run(name,args):
    args=list(map(str,args));(O/(name+'.command.json')).write_text(json.dumps(args));p=subprocess.run(args,capture_output=True,timeout=180)
    for suffix,data in [('stdout.txt',p.stdout),('stderr.txt',p.stderr),('rc.txt',str(p.returncode).encode())]:(O/(name+'.'+suffix)).write_bytes(data)
    return p
p=run('compile',['D:/work/Java/jdk-17.0.7/bin/javac.exe','-encoding','UTF-8','-cp',kernel,'-d',O/'classes',R/'lab/ClusterPairsProbe.java']);assert p.returncode==0,p.stderr.decode()
def args(path,xid):return ['D:/work/Java/jdk-17.0.7/bin/java.exe','-Xmx1536m','-cp',cp,'ClusterPairsProbe',baseline,receipt,path,xid]
expected=json.loads((S/'row-shapes.json').read_text());records=json.loads((S/'raw-records.json').read_text());pairs=[]
for seq,xid in [(66,'10.18.2272'),(66,'3.30.705'),(67,'3.26.705')]:
    p=run(f'pair-{seq}-{xid}',args(H/f'archive-{seq}.dbf',xid));assert p.returncode==0,p.stderr.decode()
    selected=[r for r in expected if r['xid']==xid and r['position'][0]==seq]
    lines=p.stdout.decode().splitlines();assert len(lines)*2==len(selected)
    for line,e,f in zip(lines,selected[::2],selected[1::2]):
        q=line.split('|');assert q[0]=='PAIR';assert list(map(int,q[1:5]))==e['position'] and int(q[5])==f['position'][3]
        assert (q[6],q[9],q[10],int(q[11]),int(q[12]),int(q[13]))==('TAB$' if e['logical']==4 else 'COL$',{2:'INSERT',3:'DELETE',4:'LOCK',5:'UPDATE'}[f['kdo_op']],{2:'INSERT',3:'DELETE',4:'LOCK',5:'UPDATE'}[e['kdo_op']],int.from_bytes(bytes.fromhex(f['kdo_bytes'])[:4],'little'),e['slot'],e['cluster_tab'])
        rec=next(r for r in records if r['xid']==xid and r['position']==e['position'][:3]);v=next(v for v in rec['vectors'] if v['index']==e['position'][3]);b=bytes.fromhex(v['fields'][0])
        u=(v['dba'],int.from_bytes(b[16:18],'little'),b[18]);x=tuple(map(int,xid.split('.')))
        assert q[7]==f'Xid[usn={x[0]}, slot={x[1]}, sequence={x[2]}]' and q[8]==f'Uba[dba={u[0]}, sequence={u[1]}, record={u[2]}]'
        pairs.append(dict(position=e['position'],xid=xid,role=q[6],slot=e['slot'],current_uba=u,native_differences=0))
original=(H/'archive-66.dbf').read_bytes();b12=6712*512;b13=6713*512
mutations=[('wrong-forward-UBA',b13+88,b'?'),('wrong-forward-slot',b13+160,b'2'),('wrong-forward-tabn',b13+162,b'\1'),
    ('wrong-forward-container',b13+56,b'\4'),('wrong-forward-full-XID',b13+86,b'\1'),('wrong-undo-logical',b12+452,b'\4'),
    ('wrong-undo-generation',b12+456,b'\3'),('wrong-lock-transition',b13+163,b'\1'),('wrong-undo-wrap',b12+474,b'\xc0')]
faults=[]
for name,offset,value in mutations:
    raw=bytearray(original);assert raw[offset:offset+len(value)]!=value;raw[offset:offset+len(value)]=value;base=offset//512*512
    raw[base+14:base+16]=b'\0\0';checksum=0
    for i in range(base,base+512,2):checksum^=int.from_bytes(raw[i:i+2],'little')
    raw[base+14:base+16]=checksum.to_bytes(2,'little');path=O/f'DERIVED-{name}.dbf';path.write_bytes(raw)
    p=run(name+'-scan',['D:/work/Java/jdk-17.0.7/bin/java.exe','-cp',kernel,'com.streamhouse.oracle.RedoCli','scan',path]);assert p.returncode==0,p.stderr.decode()
    p=run(name+'-pair',args(path,'3.30.705'));assert p.returncode!=0 and not p.stdout,name
    faults.append(dict(name=name,structure_rc=0,pair_rc=p.returncode,events=0,reason=p.stderr.decode().splitlines()[0]))
assert len(pairs)==8 and pins=={p:sha(R/p) for p in pins}
(O/'proof.json').write_text(json.dumps(dict(kernel_sha256=sha(kernel),old_classes_unchanged=len(unchanged),pairs=pairs,faults=faults,source_actions=0,events=0,parent_object_authorized=False,historical_dictionary_authorized=False,product_rebuilt=False),indent=2))
print(json.dumps(dict(raw_pairs=len(pairs),semantic_negatives=len(faults),old_classes_unchanged=len(unchanged),events=0)))
