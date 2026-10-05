"""Read-only raw/native control comparison for the frozen SYS begin5.7 gap. No graph authorization."""
import hashlib,json,re,subprocess,sys,traceback
from pathlib import Path

R=Path(__file__).resolve().parent.parent
H=R/'lab/out/v2-historical-product-20261006'
O=R/'lab/out/sys-begin-control-r2-20261006'
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
resume=sys.argv[1:]==['--resume-comparison']
O.mkdir(exist_ok=resume)
def preserve_failure(kind,value,tb):
    index=1+len(list(O.glob('driver-failure-r*.txt')))
    (O/f'driver-failure-r{index}.txt').write_text(''.join(traceback.format_exception(kind,value,tb)))
    sys.__excepthook__(kind,value,tb)
sys.excepthook=preserve_failure
iteration=1+len(list(O.glob('comparison-driver-r*.py')))
(O/(f'comparison-driver-r{iteration}.py' if resume else 'executed-driver.py')).write_bytes(Path(__file__).read_bytes())
classes=O/'classes';classes.mkdir(exist_ok=resume)
probe=R/'lab/SysControlResearchProbe.java';kernel=H/'kernel.jar';archive=H/'archive-66.dbf'
assert sha(kernel)=='672f01bc0c4a78856ce40af4de778bdca3fe881d3c46eb5de3fb069ff433302e'
pins={str(p.relative_to(R)):sha(p) for p in [probe,Path(__file__),kernel,archive,H/'dump.trc']}
(O/(f'comparison-pin-r{iteration}.json' if resume else 'execution-pin.json')).write_text(json.dumps(pins,indent=2))
def run(name,args):
    args=list(map(str,args))
    if resume:
        assert json.loads((O/(name+'.command.json')).read_text())==args
        assert (O/(name+'.rc.txt')).read_text()=='0'
        return (O/(name+'.stdout.txt')).read_bytes().decode()
    (O/(name+'.command.json')).write_text(json.dumps(args))
    p=subprocess.run(args,capture_output=True,timeout=180)
    (O/(name+'.stdout.txt')).write_bytes(p.stdout);(O/(name+'.stderr.txt')).write_bytes(p.stderr)
    (O/(name+'.rc.txt')).write_text(str(p.returncode));assert p.returncode==0,name
    return p.stdout.decode()
run('compile',['D:/work/Java/jdk-17.0.7/bin/javac.exe','-encoding','UTF-8','-cp',kernel,'-d',classes,probe])
raw=run('raw',['D:/work/Java/jdk-17.0.7/bin/java.exe','-Xmx1536m','-cp',str(kernel)+';'+str(classes),'SysControlResearchProbe',archive,'3.30.705'])
records=[]
for line in raw.splitlines():
    p=line.split('|')
    if p[0]=='RECORD':
        record=dict(position=list(map(int,p[1:4])),scn=int(p[4]),subscn=int(p[5]),uid=int(p[6]),vectors=[]);records.append(record)
    elif p[0]=='VECTOR':
        vector=dict(index=int(p[1]),opcode=p[2],dba=int(p[3]),scn=int(p[4]),cid=int(p[5]),fields=[]);record['vectors'].append(vector)
    elif p[0]=='FIELD':
        assert int(p[1])==len(vector['fields']);vector['fields'].append(p[2])
native={}
for rec in re.split(r'(?=REDO RECORD - Thread:)',(H/'dump.trc').read_text()):
    if not rec.startswith('REDO RECORD'):continue
    pos=re.search(r'RBA: 0x([0-9a-f]+)\.([0-9a-f]+)\.([0-9a-f]+)',rec)
    if pos:native[tuple(int(x,16) for x in pos.groups())]=rec
u16=lambda b,o:int.from_bytes(b[o:o+2],'little')
u32=lambda b,o:int.from_bytes(b[o:o+4],'little')
hex3=lambda m:tuple(int(x,16) for x in m.groups())
nodes=[];controls=[];extract=[]
for r in records:
    n=native[tuple(r['position'])];extract.append(n)
    header=n.splitlines()[0]
    ns=re.search(r'SCN: 0x([0-9a-f]+)',n)
    assert ns and int(ns[1],16)==r['scn'],header
    changes={int(re.search(r'^CHANGE #(\d+)',c)[1]):c for c in re.split(r'(?=CHANGE #\d+)',n)[1:]}
    for v in r['vectors']:
        c=changes[v['index']]
        if v['opcode'] not in ['5.1','5.2','5.4']:continue
        nh=re.search(r'^CHANGE #\d+ CON_ID:(\d+) [^\n]*DBA:0x([0-9a-f]+)[^\n]*SCN:0x([0-9a-f]+)[^\n]*OP:(\d+\.\d+)',c)
        assert nh and (int(nh[1]),int(nh[2],16),int(nh[3],16),nh[4])==(v['cid'],v['dba'],v['scn'],v['opcode']),(r['position'],v,c[:500])
        fields=[bytes.fromhex(f) for f in v['fields']];b=fields[0]
        at=r['position']+[v['index']]
        if v['opcode']=='5.1':
            xid=(u16(b,8),u16(b,10),u32(b,12))
            if xid!=(3,30,705):continue
            m=fields[1];uba=(v['dba'],u16(b,16),b[18])
            nx=re.search(r'\n\s*xid:\s*0x([0-9a-f]+)\.([0-9a-f]+)\.([0-9a-f]+)',c)
            nu=re.search(r'ktudb redo:[^\n]*seq: 0x([0-9a-f]+) rec: 0x([0-9a-f]+)',c)
            nm=re.search(r'slt:\s*(\d+) wrp:\s*(\d+) flg:\s*0x([0-9a-f]+)[^\n]*prev dba:\s*0x([0-9a-f]+) rci:\s*(\d+) opc:\s*(\d+)\.(\d+)',c)
            obj=re.search(r'\[objn:\s*(\d+) objd:\s*(\d+) tsn:\s*(\d+)\]',c)
            assert nx and nu and nm and obj
            assert hex3(nx)==xid and tuple(int(x,16) for x in nu.groups())==uba[1:]
            assert tuple(map(int,obj.groups()))==(u32(m,0),u32(m,4),u32(m,8))
            assert (int(nm[1]),int(nm[2]),int(nm[3],16),int(nm[4],16),int(nm[5]),int(nm[6]),int(nm[7]))==(m[18],u16(m,22),u16(m,20),u32(m,12),m[19],m[16],m[17])
            nodes.append(dict(position=at,xid=xid,uba=uba,logical=u32(m,0),data=u32(m,4),opcode=f'{m[16]}.{m[17]}',previous_dba=u32(m,12),rci=m[19],begin=len(m)==76))
        elif v['opcode']=='5.2':
            ctl=re.search(r'slt: 0x([0-9a-f]+) sqn: 0x([0-9a-f]+) flg: 0x([0-9a-f]+) siz: (\d+) fbi: (\d+)',c)
            uba=re.search(r'uba: 0x([0-9a-f]+)\.([0-9a-f]+)\.([0-9a-f]+)',c)
            assert ctl and uba
            decoded=(u16(b,0),u32(b,4),u16(b,16),u16(b,18),b[20])
            assert (int(ctl[1],16),int(ctl[2],16),int(ctl[3],16),int(ctl[4]),int(ctl[5]))==decoded
            assert hex3(uba)==(u32(b,8),u16(b,12),b[14])
            controls.append(dict(position=at,opcode=v['opcode'],segment=v['dba'],slot=decoded[0],sequence=decoded[1],flags=decoded[2],size=decoded[3],fbi=decoded[4],uba=hex3(uba),fields=v['fields']))
        else:
            end=re.search(r'ktucm redo: slt: 0x([0-9a-f]+) sqn: 0x([0-9a-f]+) srt: (\d+) sta: (\d+) flg: 0x([0-9a-f]+)',c)
            tail=re.search(r'ktucf redo: uba: 0x([0-9a-f]+)\.([0-9a-f]+)\.([0-9a-f]+) ext: (\d+) spc: (\d+) fbi: (\d+)',c)
            assert end and tail and len(fields)==3
            assert (int(end[1],16),int(end[2],16),int(end[3]),int(end[4]),int(end[5],16))==(u16(b,0),u32(b,4),u32(b,8),u32(b,12),u32(b,16))
            t=fields[1];uba=(u32(t,0),u16(t,4),t[6])
            assert tuple(int(tail[i],16) for i in [1,2,3])==uba
            assert tuple(int(tail[i]) for i in [4,5,6])==(u16(t,8),u16(t,10),t[12])
            controls.append(dict(position=at,opcode=v['opcode'],segment=v['dba'],slot=u16(b,0),sequence=u32(b,4),flags=u32(b,16),uba=uba,fields=v['fields']))
assert len(nodes)==10 and len({tuple(n['uba']) for n in nodes})==10
assert nodes[0]['begin'] and nodes[0]['logical']==0 and nodes[0]['opcode']=='5.7'
assert not nodes[1]['begin'] and nodes[1]['logical']==21 and nodes[1]['rci']==0 and nodes[1]['previous_dba']==0
assert [n['rci'] for n in nodes[2:]]==[n['uba'][2] for n in nodes[1:-1]]
assert [c['flags'] for c in controls if c['opcode']=='5.2']==[0x51,2]
assert controls[-1]['opcode']=='5.4' and controls[-1]['uba']==nodes[-1]['uba']
first_row=records[1]
inverse=next(v for v in first_row['vectors'] if v['opcode']=='5.1')
forward=next(v for v in first_row['vectors'] if v['opcode']=='11.4')
lock_context=[]
for v,ktb_index,kdo_index in [(inverse,2,3),(forward,0,1)]:
    c=re.split(r'(?=CHANGE #\d+)',native[tuple(first_row['position'])])[v['index']]
    c=c.split('KTB Redo',1)[1]
    k=bytes.fromhex(v['fields'][ktb_index]);d=bytes.fromhex(v['fields'][kdo_index])
    x=re.search(r'xid:\s*0x([0-9a-f]+)\.([0-9a-f]+)\.([0-9a-f]+)',c)
    u=re.search(r'uba: 0x([0-9a-f]+)\.([0-9a-f]+)\.([0-9a-f]+)',c)
    row=re.search(r'tabn: (\d+) slot: (\d+) to: (\d+)',c)
    assert x and u and row and 'KDO Op code: LKR' in c
    assert hex3(x)==(u16(k,8),u16(k,10),u32(k,12))
    assert hex3(u)==(u32(k,16),u16(k,20),k[22])
    assert tuple(map(int,row.groups()))==(d[18],u16(d,16),d[19])
    lock_context.append(dict(opcode=v['opcode'],xid=hex3(x),uba=hex3(u),tabn=d[18],slot=u16(d,16),to=d[19]))
assert lock_context[0]['xid']==(10,18,2272) and lock_context[1]['xid']==(3,30,705)
assert lock_context[1]['uba']==nodes[1]['uba']
(O/'native-selected-records.txt').write_text(''.join(extract))
(O/'raw-records.json').write_text(json.dumps(records,indent=2))
proof=dict(nodes=nodes,controls=controls,first_lock_context=lock_context,native_field_differences=0,source_actions=0,kernel_changed=False,events=0,
    control_first_row_predecessor_proven=False,full_SYS_history_authorized=False,
    finding='Non-row begin5.7 and later flags2 first-row head assignment are separate observations; rci0 does not establish an edge to begin UBA.')
(O/'proof.json').write_text(json.dumps(proof,indent=2))
assert pins=={p:sha(R/p) for p in pins}
print(json.dumps(dict(raw_native_nodes=len(nodes),controls=len(controls),native_field_differences=0,full_history=False)))
