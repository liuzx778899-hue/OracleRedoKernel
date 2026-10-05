"""Frozen composite index pair/native proofs; no current/late SQL is promoted to production authority."""
import hashlib,json,subprocess,sys,traceback,zipfile
from pathlib import Path
R=Path(__file__).resolve().parent.parent;H=R/'lab/out/v2-historical-product-20261006';S=R/'lab/out/sys-cluster-row-research-20261006';P=R/'lab/out/sys-cluster-pair-20261006';O=R/'lab/out/dictionary-index-pair-20261006'
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
def fail(kind,value,tb):
    (O/'driver-failure-r1.txt').write_text(''.join(traceback.format_exception(kind,value,tb)));sys.__excepthook__(kind,value,tb)
sys.excepthook=fail
assert not (O/'execution-pin.json').exists();(O/'executed-driver.py').write_bytes(Path(__file__).read_bytes());(O/'classes').mkdir()
kernel=O/'kernel.jar';assert sha(kernel)=='c8586fcb25ff60296f020b5d627cec58ee522182feead245469386b38e1f3292'
scope=O/'independent-index-address.stdout.txt';assert (O/'independent-index-address.rc.txt').read_text()=='0'
baseline=H/'snapshots/100644812901937152.snapshot';receipt='e16e6275651894614dc9a79e42381338ddbf0771aa68c971df66f35abd5c0e27';assert sha(baseline)==receipt
pins={str(p.relative_to(R)):sha(p) for p in [kernel,scope,baseline,Path(__file__),R/'lab/DictionaryIndexPairsProbe.java',S/'raw-records.json',P/'parent-key-research.json',P/'native-index-key-proof.json',H/'archive-66.dbf',H/'archive-67.dbf']}
(O/'execution-pin.json').write_text(json.dumps(pins,indent=2));cp=str(kernel)+';'+str(O/'classes')
def run(name,args):
    args=list(map(str,args));(O/(name+'.command.json')).write_text(json.dumps(args));p=subprocess.run(args,capture_output=True,timeout=180)
    for suffix,data in [('stdout.txt',p.stdout),('stderr.txt',p.stderr),('rc.txt',str(p.returncode).encode())]:(O/(name+'.'+suffix)).write_bytes(data)
    return p
p=run('compile',['D:/work/Java/jdk-17.0.7/bin/javac.exe','-encoding','UTF-8','-cp',kernel,'-d',O/'classes',R/'lab/DictionaryIndexPairsProbe.java']);assert p.returncode==0,p.stderr.decode()
def args(path,xid):return ['D:/work/Java/jdk-17.0.7/bin/java.exe','-Xmx1536m','-cp',cp,'DictionaryIndexPairsProbe',baseline,receipt,path,xid,scope]
keys=json.loads((P/'parent-key-research.json').read_text())['keys'];records=json.loads((S/'raw-records.json').read_text());pairs=[]
def number(b):
    if b==bytes([128]):return 0
    assert b[0]>=193
    value=0
    for digit in b[1:]:assert 1<=digit<=100;value=value*100+digit-1
    exponent=b[0]-193-(len(b)-2);assert exponent>=0
    return value*100**exponent
for seq,xid in [(66,'10.18.2272'),(66,'3.30.705'),(67,'3.26.705')]:
    p=run(f'pair-{seq}-{xid}',args(H/f'archive-{seq}.dbf',xid));assert p.returncode==0,p.stderr.decode()
    expected=[k for k in keys if k['xid']==xid and k['position'][0]==seq];lines=p.stdout.decode().splitlines();assert len(lines)==len(expected)
    for line,k in zip(lines,expected):
        q=line.split('|');assert q[0]=='INDEX' and list(map(int,q[1:5]))==k['position'] and q[6]==k['index']
        rec=next(r for r in records if r['position']==k['position'][:3]);u=next(v for v in rec['vectors'] if v['index']==k['position'][3]);f=next(v for v in rec['vectors'] if v['opcode'] in ['10.2','10.4'])
        ub=bytes.fromhex(u['fields'][0]);leaf=bytes.fromhex(u['fields'][3]);x=tuple(map(int,xid.split('.')));uba=(u['dba'],int.from_bytes(ub[16:18],'little'),ub[18]);components=[bytes.fromhex(c) for c in k['components']]
        assert int(q[5])==f['index'] and q[7]==f'Xid[usn={x[0]}, slot={x[1]}, sequence={x[2]}]' and q[8]==f'Uba[dba={uba[0]}, sequence={uba[1]}, record={uba[2]}]'
        assert q[9]==('INSERT' if f['opcode']=='10.2' else 'DELETE') and int(q[10])==int.from_bytes(leaf[4:8],'little') and int(q[11])==f['dba'] and int(q[12])==leaf[1]
        assert int(q[15])==number(components[0])==75185 and int(q[18])==k['physical_dba'] and int(q[19])==k['physical_slot']==51
        if k['index']=='I_COL1':assert q[16]==components[1].decode('utf8') and q[17]=='null'
        else:assert q[16]=='null' and int(q[17])==number(components[1])
        pairs.append(dict(position=k['position'],xid=xid,index=k['index'],parent_object=int(q[15]),row_dba=int(q[18]),row_slot=int(q[19]),native_key_rowid_differences=0))
original=(H/'archive-66.dbf').read_bytes();base=6708*512;off=104
mutations=[('mismatched-key',base+off+168,b'W'),('bad-component',base+off+272,b'\xff'),('short-rowid',base+off+236,b'\5'),
    ('wrong-current-UBA',base+off+256,b'f'),('wrong-current-full-XID',base+off+254,b'\1'),('wrong-logical',base+off+88,b'1'),
    ('wrong-generation',base+off+92,b'1'),('wrong-index-address',base+off+148,b'\xb8'),('wrong-row-size',base+off+268,b')'),
    ('wrong-container',base+off+220,b'\4'),('wrong-wrap',base+off+110,b'\xe1'),('missing-forward',base+off+196,b'\0\0')]
faults=[]
for name,offset,value in mutations:
    raw=bytearray(original);assert raw[offset:offset+len(value)]!=value;raw[offset:offset+len(value)]=value;block=offset//512*512
    raw[block+14:block+16]=b'\0\0';checksum=0
    for i in range(block,block+512,2):checksum^=int.from_bytes(raw[i:i+2],'little')
    raw[block+14:block+16]=checksum.to_bytes(2,'little');path=O/f'DERIVED-{name}.dbf';path.write_bytes(raw)
    p=run(name+'-scan',['D:/work/Java/jdk-17.0.7/bin/java.exe','-cp',kernel,'com.streamhouse.oracle.RedoCli','scan',path]);assert p.returncode==0,p.stderr.decode()
    p=run(name+'-pair',args(path,'10.18.2272'));assert p.returncode!=0 and not p.stdout,name
    faults.append(dict(name=name,structure_rc=0,pair_rc=p.returncode,events=0,reason=p.stderr.decode().splitlines()[0]))
assert pins=={p:sha(R/p) for p in pins}
(O/'proof.json').write_text(json.dumps(dict(kernel_sha256=sha(kernel),pairs=pairs,faults=faults,source_actions=0,events=0,index_scope_is_research_only=True,parent_history_authority=False,V3_acquisition=False,product_rebuilt=False),indent=2))
print(json.dumps(dict(pairs=len(pairs),semantic_negatives=len(faults),parent_history_authority=False,events=0)))
