"""Independent frozen-window history and complete-row checks. ASOF reads are rulers, never acquisitions."""
import hashlib,json,re,shutil,subprocess,sys
from pathlib import Path
R=Path(__file__).resolve().parent.parent;H=R/'lab/out/v3-product-history-20261006';N=R/'lab/out/split-history-candidate-20261007';A=N/sys.argv[1]
V=N/'validation';V.mkdir();(V/'executed-positive-driver.py').write_bytes(Path(__file__).read_bytes())
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
def run(name,args,required=True):
    args=list(map(str,args));assert not (V/(name+'.command.json')).exists();(V/(name+'.command.json')).write_text(json.dumps(args))
    p=subprocess.run(args,capture_output=True,timeout=900)
    for ext,b in [('stdout.txt',p.stdout),('stderr.txt',p.stderr),('rc.txt',str(p.returncode).encode())]:(V/(name+'.'+ext)).write_bytes(b)
    print(name,p.returncode,flush=True)
    if required:assert p.returncode==0,(name,p.stderr[-1000:])
    return p
summary=json.loads((A/'build-summary.json').read_text());assert summary['tests']==[311,0,0,0]
history=(A/'archive-history.stdout.txt').read_bytes();assert history==(A/'capture-history.stdout.txt').read_bytes()
assert (A/'archive-history.rc.txt').read_text()==(A/'capture-history.rc.txt').read_text()==(A/'capture-business.rc.txt').read_text()=='0'
commits=[];nodes={};rows={};identities={}
for line in history.decode().splitlines():
    p=line.split('|')
    if p[0]=='COMMIT':commits.append(list(map(int,p[1:])))
    elif p[0]=='IDENTITY':identities[int(p[1])]=list(map(int,p[2:6]));assert p[6]=='false'
    elif p[0]=='NODE':nodes.setdefault(int(p[1]),[]).append(list(map(int,p[2:])))
    elif p[0]=='ROW':rows.setdefault(int(p[1]),{})[(p[2],int(p[3]),int(p[4]),int(p[5]))]=[v.upper() for v in p[6:]]
assert [c[1:] for c in commits]==[[6,3,3,0],[10,5,4,1],[6,3,3,0]] and [c[0] for c in commits]==[2567428,2567435,2567462]
wanted={tuple(e[:3]) for entries in nodes.values() for e in entries};wanted.update([(70,126642,16),(71,16,16),(70,126628,476),(71,8,16)])
native={};lines=[];at=None;keep=False
def save():
    if keep and at is not None:
        value=''.join(lines);native[at]=value;(V/('native-'+'-'.join(map(str,at))+'.txt')).write_text(value)
with (H/'dump.trc').open(errors='replace') as stream:
    for line in stream:
        if line.startswith('REDO RECORD - Thread:'):
            save();m=re.search(r'RBA: 0x([0-9a-f]+)\.([0-9a-f]+)\.([0-9a-f]+)',line)
            at=tuple(int(x,16) for x in m.groups()) if m else None;lines=[];keep=at in wanted
        if line.startswith('SCN:') and int(re.search(r'0x([0-9a-f]+)',line)[1],16) in [c[0] for c in commits]:keep=True
        if keep or line.startswith('REDO RECORD - Thread:'):lines.append(line)
save();assert wanted<=native.keys()
proof=[]
for commit in commits:
    scn=commit[0];usn,slot,sequence,begin=identities[scn];selected=nodes[scn]
    for e in selected:
        seq,block,offset,vector,logical,data,dba,ubaSequence,ubaRecord,previous,rci=e
        c=next(c for c in re.split(r'(?=CHANGE #\d+)',native[(seq,block,offset)])[1:] if c.startswith(f'CHANGE #{vector} '))
        assert 'OP:5.1' in c and f'[objn: {logical} objd: {data} tsn: 0]' in c
        assert int(re.search(r'DBA:0x([0-9a-f]+)',c)[1],16)==dba
        m=re.search(r'xid:\s+0x([0-9a-f]+)\.([0-9a-f]+)\.([0-9a-f]+)',c);assert tuple(int(x,16) for x in m.groups())==(usn,slot,sequence)
        m=re.search(r'seq: 0x([0-9a-f]+) rec: 0x([0-9a-f]+)',c);assert tuple(int(x,16) for x in m.groups())==(ubaSequence,ubaRecord)
        assert int(re.search(r'rci: (\d+)',c)[1])==rci and int(re.search(r'prev dba:\s+0x([0-9a-f]+)',c)[1],16)==previous
    ends=[]
    for pos,record in native.items():
        if int(re.search(r'^SCN: 0x([0-9a-f]+)',record,re.M)[1],16)!=scn:continue
        for c in re.split(r'(?=CHANGE #\d+)',record)[1:]:
            m=re.search(r'ktucm redo: slt: 0x([0-9a-f]+) sqn: 0x([0-9a-f]+)',c)
            if 'OP:5.4' in c and m and tuple(int(x,16) for x in m.groups())==(slot,sequence):ends.append(c)
    assert len(ends)==1;m=re.search(r'ktucf redo: uba: 0x([0-9a-f]+)\.([0-9a-f]+)\.([0-9a-f]+)',ends[0])
    assert tuple(int(x,16) for x in m.groups())==tuple(selected[-1][6:9])
    proof.append(dict(xid=f'{usn}.{slot}.{sequence}',commit_scn=scn,originals=len(selected),native_differences=0))
j='D:/work/Java/jdk-17.0.7/bin/';cp=str(A/'kernel.jar')+';'+str(A/'classes');baseline=[H/'actual-product-v3.snapshot','323fd35a8ceee43545c28a9fb32f55cedd41409a3e1b5fb63e08606380cd24a2'];base=[j+'java.exe','-Xmx1536m','-cp',cp]
run('compile-parity',[j+'javac.exe','-cp',cp,'-d',A/'classes',R/'lab/HistoricalRowsParityProbe.java',R/'lab/CapturedRecordsProbe.java'])
captures=[]
for seq in [70,71]:
    p=H/f'capture/sequence-{seq}.oracap';captures.extend([p,sha(p)])
archive=run('archive-row-parity',base+['com.streamhouse.oracle.HistoricalRowsParityProbe',*baseline,'archives','50342539',H/'archive-70.dbf',H/'archive-71.dbf'])
capture=run('capture-row-parity',base+['com.streamhouse.oracle.HistoricalRowsParityProbe',*baseline,'captures','50342539',*captures]);assert archive.stdout==capture.stdout and len(archive.stdout.splitlines())==2
business=(A/'capture-business.stdout.txt').read_bytes();assert run('business-repeat',base+['CapturedHistoricalBatchProbe',*baseline,'50342539',*captures]).stdout==business
def compare_business(value):
    lines=value.decode().splitlines();assert len(lines)==7;out=[]
    for i,(seq,block,offset,scn,xid,before,after,columns,phase) in enumerate([(70,126642,16,2567441,(10,15,2348),'history-v3-restored','history-product-added',4,'ADD'),(71,16,16,2567465,(10,12,2321),'history-product-added','history-product-restored',3,'DROP')]):
        schema,event,row=lines[1+i*3:4+i*3];at=f'{seq}:{block}:{offset}:4'
        assert schema.startswith('SCHEMA|'+at+'|') and schema.count('PhysicalColumn[')==columns and 'name=NOTE, type=VARCHAR2, nullable=true, hidden=true' in schema
        assert ('name=EXTRA_HP' in schema)==(columns==4)
        assert event.split('|')[3:]==[at,f'Xid[usn={xid[0]}, slot={xid[1]}, sequence={xid[2]}]',str(scn),str(scn)]
        tail=',VALUE:616e63686f72'+(',NULL:' if columns==4 else '')
        assert row==f'ROW|{at}|75185|50342532|0|UPDATE|VALUE:80,VALUE:{before.encode().hex()}{tail}|VALUE:80,VALUE:{after.encode().hex()}{tail}'
        record=native[(seq,block,offset)];assert int(re.search(r'^SCN: 0x([0-9a-f]+)',record,re.M)[1],16)==scn
        assert f'xid:  0x{xid[0]:04x}.{xid[1]:03x}.{xid[2]:08x}' in record
        parts=re.split(r'CHANGE #\d+ ',record)[1:]
        for opcode,wanted in [('OP:11.5 ',after),('OP:5.1 ',before)]:
            c=next(c for c in parts if opcode in c.splitlines()[0]);m=re.search(r'col\s+1:\s+\[(\d+)\]\s+((?:[0-9a-f]{2}\s*)+)',c)
            assert m and int(m[1])==len(wanted) and bytes.fromhex(m[2])==wanted.encode()
        truth=(H/(phase+'-once.stdout.txt')).read_text();assert f'XID|{xid[0]}.{xid[1]}.{xid[2]}' in truth
        assert f'TRUTH|{phase}|0|{after}|anchor'+('|<NULL>' if columns==4 else '') in truth and truth.count('COLUMN|')==columns
        assert int(re.search(r'TRUTH_SCN\|(\d+)',truth)[1])>=scn
        out.append(dict(position=at,commit_scn=scn,xid=xid,columns=columns,native_and_phase_sql_differences=0))
    return out
business_proof=compare_business(business);negative=[]
for name,old,new in [('rowid','50342532','50342533'),('hidden-note','616e63686f72','616e63686f73'),('operation','UPDATE','INSERT'),('schema','name=EXTRA_HP','name=EXTRA_HQ')]:
    changed=business.replace(old.encode(),new.encode(),1)
    try:compare_business(changed)
    except AssertionError:negative.append(name)
    else:raise AssertionError('comparer accepted '+name)
shutil.copyfile(R/'lab/out/v3-product-runtime-20261006/source.secret',V/'source.secret')
shutil.copytree(R/'lab/out/v3-product-runtime-20261006/truth-classes',V/'truth-classes');shutil.copyfile(H/'ojdbc8.jar',V/'ojdbc8.jar')
for item in proof:
    scn=item['commit_scn'];T=V/f'truth-{scn}';T.mkdir()
    p=run(f'asof-{scn}',['docker','run','--rm','--label','owner=OracleRedoKernel','--label','scope=split-history-read-only-truth','--network','container:oraredo-19c-lab-20261004','--mount','type=bind,source='+str(V)+',target=/evidence','--entrypoint','java','flink:1.18-java17','-cp','/evidence/truth-classes:/evidence/ojdbc8.jar','SysDictionaryBaselineResearchProbe','/evidence/source.secret',scn,75185,75186,'/evidence/'+T.name],False)
    if p.returncode:
        item.update(asof_verified=False,reason=p.stderr.decode().splitlines()[0]);continue
    actual={};key=None
    for line in p.stdout.decode().splitlines():
        q=line.split('|')
        if q[0]=='SYS_ROW':key=(q[1],int(q[2]),int(q[4])*4194304+int(q[5]),int(q[6]));actual[key]=[]
        elif q[0]=='CELL':
            if q[8]=='NULL':raw='NULL'
            else:
                m=re.fullmatch(r'Typ=(\d+) Len=(\d+): (.*)',q[8]);assert m;raw=''.join(f'{int(x,16):02X}' for x in m[3].split(','));assert len(raw)==int(m[2])*2
            actual[key].append(raw)
    assert actual==rows[scn],scn;item.update(asof_verified=True,full_values=sum(map(len,actual.values())),asof_differences=0)
(V/'positive-proof.json').write_text(json.dumps(dict(kernel_sha256=sha(A/'kernel.jar'),tests=summary['tests'],commits=proof,business=business_proof,archive_capture_history_equal=True,archive_capture_rows_equal=True,repeat_equal=True,comparer_negatives=negative,events=2,source_actions=0,target_actions=0,product_http=False,checkpoint_ack=False),indent=2))
print('COMPLETE_HISTORY_AND_ROWS|2',flush=True)
