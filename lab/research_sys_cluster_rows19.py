"""Frozen raw SYS row shape inventory; native columns are only an independent ruler."""
import hashlib,json,re,subprocess,sys,traceback
from pathlib import Path
R=Path(__file__).resolve().parent.parent;H=R/'lab/out/v2-historical-product-20261006';O=R/'lab/out/sys-cluster-row-research-20261006'
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
O.mkdir(exist_ok=False);(O/'executed-driver.py').write_bytes(Path(__file__).read_bytes());classes=O/'classes';classes.mkdir()
kernel=R/'lab/out/sys-control-candidate-20261006/kernel.jar';probe=R/'lab/SysControlResearchProbe.java'
assert sha(kernel)=='5fb55de7f1d75cfdab71307a7734508cc5c49873ac543ee51add0ae9517c6af6'
def failure(kind,value,tb):
    (O/'driver-failure.txt').write_text(''.join(traceback.format_exception(kind,value,tb)));sys.__excepthook__(kind,value,tb)
sys.excepthook=failure
pins={str(p.relative_to(R)):sha(p) for p in [Path(__file__),probe,kernel,H/'dump.trc',H/'archive-66.dbf',H/'archive-67.dbf']}
(O/'execution-pin.json').write_text(json.dumps(pins,indent=2))
def run(name,args):
    args=list(map(str,args));(O/(name+'.command.json')).write_text(json.dumps(args));p=subprocess.run(args,capture_output=True,timeout=180)
    for suffix,data in [('stdout.txt',p.stdout),('stderr.txt',p.stderr),('rc.txt',str(p.returncode).encode())]:(O/(name+'.'+suffix)).write_bytes(data)
    assert p.returncode==0,p.stderr.decode();return p.stdout.decode()
run('compile',['D:/work/Java/jdk-17.0.7/bin/javac.exe','-encoding','UTF-8','-cp',kernel,'-d',classes,probe])
native={}
for rec in re.split(r'(?=REDO RECORD - Thread:)',(H/'dump.trc').read_text()):
    pos=re.search(r'RBA: 0x([0-9a-f]+)\.([0-9a-f]+)\.([0-9a-f]+)',rec)
    if pos:native[tuple(int(x,16) for x in pos.groups())]=rec
records=[]
for seq,xid in [(66,'10.18.2272'),(66,'3.30.705'),(67,'3.26.705')]:
    raw=run(f'raw-{seq}-{xid}',['D:/work/Java/jdk-17.0.7/bin/java.exe','-Xmx1536m','-cp',str(kernel)+';'+str(classes),'SysControlResearchProbe',H/f'archive-{seq}.dbf',xid])
    for line in raw.splitlines():
        p=line.split('|')
        if p[0]=='RECORD':record=dict(xid=xid,position=list(map(int,p[1:4])),scn=int(p[4]),vectors=[]);records.append(record)
        elif p[0]=='VECTOR':v=dict(index=int(p[1]),opcode=p[2],dba=int(p[3]),cid=int(p[5]),fields=[]);record['vectors'].append(v)
        elif p[0]=='FIELD':assert int(p[1])==len(v['fields']);v['fields'].append(p[2])
u16=lambda b,o:int.from_bytes(b[o:o+2],'little');u32=lambda b,o:int.from_bytes(b[o:o+4],'little')
rows=[];extract=[]
for rec in records:
    original=[v for v in rec['vectors'] if v['opcode']=='5.1' and len(v['fields'])>=4]
    for inverse in original:
        m=bytes.fromhex(inverse['fields'][1]);logical=u32(m,0);data=u32(m,4)
        if (m[16],m[17])!=(11,1) or logical not in [4,21]:continue
        k=bytes.fromhex(inverse['fields'][3]);dba=u32(k,0)
        forward=[v for v in rec['vectors'] if v['opcode'].startswith('11.') and v['dba']==dba];assert len(forward)==1
        n=native[tuple(rec['position'])];extract.append(n)
        changes={int(re.search(r'^CHANGE #(\d+)',c)[1]):c for c in re.split(r'(?=CHANGE #\d+)',n)[1:]}
        for v,start in [(inverse,2),(forward[0],0)]:
            c=changes[v['index']];fields=[bytes.fromhex(f) for f in v['fields'][start:]];k=fields[1];op=k[10]
            assert u32(k,0)==dba and op in [2,3,4,5]
            row=re.search(r'tabn: (\d+) slot: (\d+)',c);assert row,c[:500]
            tab=int(row[1]);slot=int(row[2]);assert tab in [1,5]
            stored=k[18] if op==2 else k[22] if op==5 else None
            changed=k[23] if op==5 else stored
            values=[]
            if op==5:
                assert len(fields[2])==changed*2 and len(fields)==changed+3
                values=[dict(column=u16(fields[2],2*i),hex=fields[i+3].hex()) for i in range(changed)]
            elif op==2:
                # Cluster insertion has additional key/control fields; preserve rather than assume heap count+2.
                values=[dict(field=i,hex=f.hex()) for i,f in enumerate(fields[2:],2)]
            rows.append(dict(xid=rec['xid'],position=rec['position']+[v['index']],record_scn=rec['scn'],logical=logical,data=data,
                side='inverse' if start==2 else 'forward',opcode=v['opcode'],kdo_op=op,kdo_bytes=k.hex(),field_lengths=[len(f) for f in fields],
                cluster_tab=tab,slot=slot,stored_columns=stored,changed_columns=changed,values=values,native_columns_text=c))
(O/'raw-records.json').write_text(json.dumps(records,indent=2));(O/'row-shapes.json').write_text(json.dumps(rows,indent=2))
(O/'native-selected-records.txt').write_text(''.join(dict.fromkeys(extract)))
assert pins=={p:sha(R/p) for p in pins}
print(json.dumps(dict(row_sides=len(rows),shapes=[dict(logical=r['logical'],op=r['kdo_op'],tab=r['cluster_tab'],lengths=r['field_lengths'],stored=r['stored_columns']) for r in rows],events=0)))
