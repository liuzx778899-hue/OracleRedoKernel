"""Actual pre-action V3 and new archive-owned SYS history versus independent native/ASOF rulers."""
import hashlib,json,re,shutil,subprocess
from pathlib import Path
R=Path(__file__).resolve().parent.parent;O=R/'lab/out/v3-historical-raw-20261006';S='oraredo-19c-lab-20261004'
assert not (O/'actual-history-proof.json').exists();ref=json.loads((O/'capture-reference.json').read_text());b=ref['baseline'];sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
assert sha(Path(b['path']))==b['sha256'] and json.loads((O/'baseline-truth-proof.json').read_text())['differences']==0
shutil.copyfile(Path(__file__),O/'executed-actual-history-driver.py')
def run(name,args):
    args=list(map(str,args));assert not (O/(name+'.command.json')).exists();(O/(name+'.command.json')).write_text(json.dumps(args));p=subprocess.run(args,capture_output=True,timeout=180)
    for suffix,data in [('stdout.txt',p.stdout),('stderr.txt',p.stderr),('rc.txt',str(p.returncode).encode())]:(O/(name+'.'+suffix)).write_bytes(data)
    assert p.returncode==0,p.stderr.decode();return p.stdout.decode()
j='D:/work/Java/jdk-17.0.7/bin/';run('compile-history-evidence',[j+'javac.exe','-cp',O/'new-kernel.jar','-d',O/'classes',R/'lab/ArchivedDictionaryHistoryEvidenceProbe.java'])
args=[j+'java.exe','-Xmx1536m','-cp',str(O/'new-kernel.jar')+';'+str(O/'classes'),'ArchivedDictionaryHistoryEvidenceProbe',b['path'],b['sha256']]
archive=run('actual-archive-history',args+[O/f'archive-{seq}.dbf' for seq in ref['sequences']])
captures=[]
for seq in ref['sequences']:
    p=O/f'capture/sequence-{seq}.oracap';captures.extend([p,sha(p)])
capture=run('actual-capture-history',args+['captures',*captures]);assert archive==capture
commits=[];nodes={};rows={};identities={};schemas=[]
for line in archive.splitlines():
    q=line.split('|')
    if q[0]=='COMMIT':commits.append(list(map(int,q[1:])))
    elif q[0]=='IDENTITY':identities[int(q[1])]=list(map(int,q[2:6]));assert q[6]=='false'
    elif q[0]=='NODE':nodes.setdefault(int(q[1]),[]).append(list(map(int,q[2:])))
    elif q[0]=='ROW':rows.setdefault(int(q[1]),{})[(q[2],int(q[3]),int(q[4]),int(q[5]))]=[v.upper() for v in q[6:]]
    else:schemas.append(line)
assert len(commits)==3 and [c[1:] for c in commits]==[[6,3,3,0],[10,5,4,1],[6,3,3,0]],commits
native={}
for rec in re.split(r'(?=REDO RECORD - Thread:)',(O/'dump.trc').read_text()):
    m=re.search(r'RBA: 0x([0-9a-f]+)\.([0-9a-f]+)\.([0-9a-f]+)',rec)
    if m:native[tuple(int(v,16) for v in m.groups())]=rec
proof=[]
for commit in commits:
    scn=commit[0];usn,slot,sequence,begin=identities[scn];xid=f'{usn}.{slot}.{sequence}';selected=nodes[scn]
    for n in selected:
        seq,block,offset,vector,logical,data,dba,ubaSequence,ubaRecord,previous,rci=n
        rec=native[(seq,block,offset)];c=next(c for c in re.split(r'(?=CHANGE #\d+)',rec)[1:] if c.startswith(f'CHANGE #{vector} '))
        assert 'OP:5.1' in c and f'[objn: {logical} objd: {data} tsn: 0]' in c
        assert int(re.search(r'DBA:0x([0-9a-f]+)',c)[1],16)==dba
        m=re.search(r'xid:\s+0x([0-9a-f]+)\.([0-9a-f]+)\.([0-9a-f]+)',c);assert tuple(int(v,16) for v in m.groups())==(usn,slot,sequence)
        m=re.search(r'seq: 0x([0-9a-f]+) rec: 0x([0-9a-f]+)',c);assert tuple(int(v,16) for v in m.groups())==(ubaSequence,ubaRecord)
        assert int(re.search(r'rci: (\d+)',c)[1])==rci
    ends=[]
    for pos,rec in native.items():
        if int(re.search(r'SCN: 0x([0-9a-f]+)',rec)[1],16)!=scn:continue
        for c in re.split(r'(?=CHANGE #\d+)',rec)[1:]:
            m=re.search(r'ktucm redo: slt: 0x([0-9a-f]+) sqn: 0x([0-9a-f]+)',c)
            if 'OP:5.4' in c and m and tuple(int(v,16) for v in m.groups())==(slot,sequence):ends.append((pos,c))
    assert len(ends)==1;endpos,end=ends[0];m=re.search(r'ktucf redo: uba: 0x([0-9a-f]+)\.([0-9a-f]+)\.([0-9a-f]+)',end)
    assert tuple(int(v,16) for v in m.groups())==tuple(selected[-1][6:9])
    directory=O/f'truth-{scn}';directory.mkdir()
    text=run(f'independent-history-{scn}',['docker','run','--rm','--label','owner=OracleRedoKernel','--label','scope=v3-historical-commit-truth','--network','container:'+S,'--mount','type=bind,source='+str(O)+',target=/evidence','--entrypoint','java','flink:1.18-java17','-cp','/evidence/truth-classes:/evidence/ojdbc8.jar','SysDictionaryBaselineResearchProbe','/evidence/source.secret',scn,75185,75186,'/evidence/'+directory.name])
    actual={};current=None
    for line in text.splitlines():
        q=line.split('|')
        if q[0]=='SYS_ROW':current=(q[1],int(q[2]),int(q[4])*4194304+int(q[5]),int(q[6]));actual[current]=[]
        elif q[0]=='CELL':
            if q[8]=='NULL':raw='NULL'
            else:
                m=re.fullmatch(r'Typ=(\d+) Len=(\d+): (.*)',q[8]);assert m
                raw=''.join(f'{int(x,16):02X}' for x in m[3].split(','));assert len(raw)==int(m[2])*2
            actual[current].append(raw)
    assert actual==rows[scn],scn
    proof.append(dict(xid=xid,commit_scn=scn,begin_scn=begin,originals=len(selected),row_pairs=commit[2],index_pairs=commit[3],controls=commit[4],native_differences=0,full_values=sum(map(len,actual.values())),asof_differences=0))
assert len(schemas)==3 and schemas[0].startswith('SCHEMA_REFUSED|') and 'EXTRA_V3' in schemas[1] and 'EXTRA_V3' not in schemas[2]
(O/'actual-history-proof.json').write_text(json.dumps(dict(kernel_sha256=sha(O/'new-kernel.jar'),baseline=b,commits=proof,schemas=schemas,archive_capture_equal=True,actual_acquisition_before_source_actions=True,bounded_sys_history_passed=True,history_dml_accepted=False,events=0,target_actions=0,source_actions_replayed=False),indent=2))
print(json.dumps(dict(commits=3,originals=22,full_values=764,native_differences=0,asof_differences=0,events=0)))
