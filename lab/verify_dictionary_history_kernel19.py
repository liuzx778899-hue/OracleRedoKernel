"""Archive-owned history candidate against frozen independent full SYS rulers; no event authority."""
import hashlib,json,re,shutil,subprocess
from pathlib import Path
R=Path(__file__).resolve().parent.parent;O=R/'lab/out/sys-history-kernel-candidate-20261006';H=R/'lab/out/v2-historical-product-20261006';Q=R/'lab/out/sys-full-history-research-20261006'
attempt=1+len(list(O.glob('executed-history-driver-r*.py')))
shutil.copyfile(Path(__file__),O/f'executed-history-driver-r{attempt}.py');shutil.copyfile(R/'target/oracle-redo-kernel-0.1.0-SNAPSHOT.jar',O/'kernel.jar')
baseline=O/'DIAGNOSTIC-NOT-ACQUIRED-v3.snapshot';shutil.copyfile(R/'src/test/resources/dictionary-baseline-lab-v3.txt',baseline)
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
(O/'classes').mkdir(exist_ok=True)
def run(name,args):
    name=name+f'-attempt{attempt}';args=list(map(str,args));(O/(name+'.command.json')).write_text(json.dumps(args));p=subprocess.run(args,capture_output=True,timeout=180)
    for suffix,data in [('stdout.txt',p.stdout),('stderr.txt',p.stderr),('rc.txt',str(p.returncode).encode())]:(O/(name+'.'+suffix)).write_bytes(data)
    return p
j='D:/work/Java/jdk-17.0.7/bin/'
p=run('probe-compile',[j+'javac.exe','-cp',O/'kernel.jar','-d',O/'classes',R/'lab/ArchivedDictionaryHistoryProbe.java']);assert p.returncode==0,p.stderr.decode()
def command(b,paths):return [j+'java.exe','-Xmx1536m','-cp',str(O/'kernel.jar')+';'+str(O/'classes'),'ArchivedDictionaryHistoryProbe',b,sha(b),*paths]
paths=[H/'archive-66.dbf',H/'archive-67.dbf']
p=run('history-original',command(baseline,paths));assert p.returncode==0,p.stderr.decode();original_output=p.stdout
for stem,prefix in [('actual-v2-batch','captures'),('erased-v2-batch','erased')]:
    old=json.loads((H/(stem+'.command.json')).read_text());args=command(baseline,[])+['captures']+old[8:]
    p=run('history-'+prefix,args);assert p.returncode==0 and p.stdout==original_output,p.stderr.decode()
def truth(text):
    out={};current=None
    for line in text.splitlines():
        q=line.split('|')
        if q[0]=='SYS_ROW':current=(q[1],int(q[2]),int(q[4])*4194304+int(q[5]),int(q[6]));out[current]=[]
        elif q[0]=='CELL':
            if q[8]=='NULL':raw='NULL'
            else:
                m=re.fullmatch(r'Typ=(\d+) Len=(\d+): (.*)',q[8]);assert m
                raw=''.join(f'{int(x,16):02X}' for x in m[3].split(','));assert len(raw)==int(m[2])*2
            out[current].append(raw)
    return out
rows={};commits=[];schemas=[]
for line in original_output.decode().splitlines():
    q=line.split('|')
    if q[0]=='COMMIT':commits.append(list(map(int,q[1:])))
    elif q[0]=='ROW':rows.setdefault(int(q[1]),{})[(q[2],int(q[3]),int(q[4]),int(q[5]))]=[v.upper() for v in q[6:]]
    else:schemas.append(line)
assert commits==[[2522904,6,3,3,0],[2522911,10,5,4,1],[2522937,6,3,3,0]],commits
for scn,r in rows.items():assert r==truth((Q/f'full-sys-{scn}.stdout.txt').read_text()),scn
assert len(schemas)==3 and schemas[0].startswith('SCHEMA_REFUSED|2522904|') and 'EXTRA' in schemas[1] and 'EXTRA' not in schemas[2],schemas
negatives=[]
original=paths[0].read_bytes();payload=b''.join(original[i+16:i+512] for i in range(0,len(original),512))
shape=next(r for r in json.loads((R/'lab/out/sys-cluster-row-research-20261006/row-shapes.json').read_text()) if r.get('kdo_op')==2 and r['side']=='forward')
kdo=bytes.fromhex(shape['kdo_bytes']);flat=payload.find(kdo);assert flat>=0 and payload.find(kdo,flat+1)<0
for name,delta in [('cluster-null-bitmap',46),('cluster-row-size',40)]:
    at=(flat+delta)//496*512+16+(flat+delta)%496;raw=bytearray(original);raw[at]^=1;block=at//512*512;raw[block+14:block+16]=b'\0\0';checksum=0
    for i in range(block,block+512,2):checksum^=int.from_bytes(raw[i:i+2],'little')
    raw[block+14:block+16]=checksum.to_bytes(2,'little');path=O/('DERIVED-'+name+'.dbf');path.write_bytes(raw)
    scan=run(name+'-scan',[j+'java.exe','-cp',O/'kernel.jar','com.streamhouse.oracle.RedoCli','scan',path]);assert scan.returncode==0
    p=run(name,command(baseline,[path,paths[1]]));assert p.returncode==1 and not p.stdout
    negatives.append(dict(name=name,structure_rc=0,rc=1,events=0,reason=p.stderr.decode().splitlines()[0]))
for raw in sorted(Q.glob('DERIVED-object-*.dbf')):
    p=run('whole-history-'+raw.stem,command(baseline,[paths[0],raw]));assert p.returncode==1 and not p.stdout,p.stdout.decode()
    negatives.append(dict(name=raw.stem,rc=1,events=0,reason=p.stderr.decode().splitlines()[0]))
for name,b in [('late-actual-v3',R/'lab/out/dictionary-baseline-candidate-20261006/new-v3-r2.snapshot'),('actual-v2',H/'snapshots/100644812901937152.snapshot')]:
    p=run(name,command(b,paths));assert p.returncode==1 and not p.stdout
    negatives.append(dict(name=name,rc=1,events=0,reason=p.stderr.decode().splitlines()[0]))
(O/f'history-proof-r{attempt}.json').write_text(json.dumps(dict(kernel_sha256=sha(O/'kernel.jar'),commits=commits,complete_values=764,independent_differences=0,schemas=schemas,archive_capture_erased_equal=True,negative_cases=negatives,baseline_acquired=False,events=0,source_actions=0,target_actions=0),indent=2))
print(json.dumps(dict(commits=3,values=764,differences=0,negative_cases=len(negatives),authority=False)))
