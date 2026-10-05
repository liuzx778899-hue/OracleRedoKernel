"""Frozen three-SYS history/native parity and checksum-valid negatives, without source actions."""
import hashlib,json,re,subprocess,sys,traceback
from pathlib import Path
R=Path(__file__).resolve().parent.parent;H=R/'lab/out/v2-historical-product-20261006';O=R/'lab/out/sys-control-candidate-20261006'
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
def failed(kind,value,tb):
    (O/'driver-failure-r1.txt').write_text(''.join(traceback.format_exception(kind,value,tb)));sys.__excepthook__(kind,value,tb)
sys.excepthook=failed
assert not (O/'execution-pin.json').exists()
(O/'executed-driver.py').write_bytes(Path(__file__).read_bytes())
kernel=O/'kernel.jar';assert sha(kernel)=='5fb55de7f1d75cfdab71307a7734508cc5c49873ac543ee51add0ae9517c6af6'
cp=str(kernel)+';'+str(O/'classes')
pins={str(p.relative_to(R)):sha(p) for p in [kernel,R/'lab/SysUndoHistoryProbe.java',R/'lab/UndoInventoryProbe.java',Path(__file__),H/'dump.trc',H/'archive-66.dbf',H/'archive-67.dbf']}
(O/'execution-pin.json').write_text(json.dumps(pins,indent=2))
def run(name,main,path,xid):
    args=['D:/work/Java/jdk-17.0.7/bin/java.exe','-Xmx1536m','-cp',cp,main,str(path),xid]
    assert not (O/(name+'.command.json')).exists();(O/(name+'.command.json')).write_text(json.dumps(args))
    p=subprocess.run(args,capture_output=True,timeout=180)
    (O/(name+'.stdout.txt')).write_bytes(p.stdout);(O/(name+'.stderr.txt')).write_bytes(p.stderr);(O/(name+'.rc.txt')).write_text(str(p.returncode));return p
native={}
for rec in re.split(r'(?=REDO RECORD - Thread:)',(H/'dump.trc').read_text()):
    pos=re.search(r'RBA: 0x([0-9a-f]+)\.([0-9a-f]+)\.([0-9a-f]+)',rec)
    if not pos:continue
    at=tuple(int(x,16) for x in pos.groups())
    for c in re.split(r'(?=CHANGE #\d+)',rec)[1:]:
        h=re.search(r'^CHANGE #(\d+) CON_ID:3 [^\n]*DBA:0x([0-9a-f]+)[^\n]*OP:5\.1\b',c)
        x=re.search(r'\n\s*xid:\s*0x([0-9a-f]+)\.([0-9a-f]+)\.([0-9a-f]+)',c)
        if not h or not x:continue
        xid='.'.join(str(int(v,16)) for v in x.groups())
        u=re.search(r'ktudb redo:[^\n]*seq: 0x([0-9a-f]+) rec: 0x([0-9a-f]+)',c)
        m=re.search(r'prev dba:\s*0x([0-9a-f]+) rci:\s*(\d+) opc:\s*(\d+\.\d+)',c)
        if not u or not m:continue
        uba=tuple(int(v,16) for v in [h[2],u[1],u[2]])
        native.setdefault((at[0],xid),[]).append(dict(position=at[1:]+(int(h[1]),),uba=uba,previous=int(m[1],16),rci=int(m[2]),opcode=m[3]))
results=[]
for seq,xid in [(66,'10.18.2272'),(66,'3.30.705'),(67,'3.26.705')]:
    archive=H/f'archive-{seq}.dbf';expected=native[(seq,xid)]
    p=run(f'inventory-{seq}-{xid}','UndoInventoryProbe',archive,xid);assert p.returncode==0,p.stderr.decode()
    lines=['NODE|'+'|'.join(map(str,(seq,)+n['position']))+'|'+':'.join(map(str,n['uba']))+'|'+str(n['previous'])+'|'+str(n['rci'])+'|'+n['opcode'] for n in expected]
    assert p.stdout.decode().splitlines()==lines
    p=run(f'history-{seq}-{xid}','SysUndoHistoryProbe',archive,xid);assert p.returncode==0,p.stderr.decode()
    actual=[s.split('|') for s in p.stdout.decode().splitlines() if s.startswith('NODE|')]
    assert len(actual)==len(expected)
    def uba(t):return 'Uba[dba='+str(t[0])+', sequence='+str(t[1])+', record='+str(t[2])+']'
    for a,n in zip(actual,expected):
        assert list(map(int,a[1:5]))==[seq,*n['position']] and a[5]==uba(n['uba'])
        # Native rci0 is an explicit chain root, never an inferred link to allocation begin.
        assert n['previous']==0
        predecessor='null' if n['rci']==0 else uba((n['uba'][0],n['uba'][1],n['rci']))
        assert a[6]==predecessor,(xid,a,n)
    end=[s.split('|') for s in p.stdout.decode().splitlines() if s.startswith('END|')];assert len(end)==1
    control_only=sum(n['opcode']=='5.7' for n in expected)
    assert int(end[0][5])==len(expected) and int(end[0][6])==len(expected)-control_only
    results.append(dict(sequence=seq,xid=xid,originals=len(expected),allocation_controls=control_only,live_row_nodes=len(expected)-control_only,native_differences=0))
original=(H/'archive-66.dbf').read_bytes();block=6712*512
# Offsets are frozen record/vector field locations already matched against native; every mutation is labelled DERIVED.
mutations=[('missing-begin',block+160,b'\0\0'),('missing-root-control',block+320,b'\0\0'),
    ('wrong-control-UBA',block+364,b'?'),('wrong-root-wrap',block+474,b'\xc0'),
    ('wrong-control-slot',block+356,b'\x1f'),('wrong-root-container',block+412,b'\x04'),
    ('zero-root-object',block+452,b'\0\0\0\0'),('wrong-root-rci',block+471,b'\x20'),
    ('missing-middle-original',6714*512+64+24,b'\0\0')]
faults=[]
for name,offset,value in mutations:
    b=bytearray(original);assert b[offset:offset+len(value)]!=value
    b[offset:offset+len(value)]=value;base=offset//512*512;b[base+14:base+16]=b'\0\0'
    checksum=0
    for i in range(base,base+512,2):checksum^=int.from_bytes(b[i:i+2],'little')
    b[base+14:base+16]=checksum.to_bytes(2,'little');path=O/f'DERIVED-{name}.dbf';path.write_bytes(b)
    # Structural scan is separate from semantic history refusal.
    args=['D:/work/Java/jdk-17.0.7/bin/java.exe','-cp',str(kernel),'com.streamhouse.oracle.RedoCli','scan',str(path)]
    (O/(name+'-scan.command.json')).write_text(json.dumps(args));p=subprocess.run(args,capture_output=True,timeout=180)
    for suffix,data in [('stdout.txt',p.stdout),('stderr.txt',p.stderr),('rc.txt',str(p.returncode).encode())]:(O/(name+'-scan.'+suffix)).write_bytes(data)
    assert p.returncode==0,p.stderr.decode()
    p=run(name+'-history','SysUndoHistoryProbe',path,'3.30.705');assert p.returncode!=0 and not p.stdout,name
    faults.append(dict(name=name,structure_rc=0,history_rc=p.returncode,events=0,reason=p.stderr.decode().splitlines()[0]))
assert pins=={p:sha(R/p) for p in pins}
proof=dict(kernel_sha256=sha(kernel),results=results,faults=faults,source_actions=0,events=0,SYS_row_values_verified=False,historical_dictionary_verified=False,product_rebuilt=False)
(O/'proof.json').write_text(json.dumps(proof,indent=2));print(json.dumps(dict(results=results,negative_cases=len(faults),history_events=0)))
