"""Read-only real nullable-tail gates; no source operations or target writes."""
import hashlib,json,struct,subprocess,sys,zipfile
from pathlib import Path
R=Path(__file__).resolve().parent.parent;O=R/'lab/out/nullable-tail-20261005';F=R/'lab/out/null-dml-capture-20261005';J=O/'kernel.jar'
JAVA='D:/work/Java/jdk-17.0.7/bin/java.exe';JAVAC='D:/work/Java/jdk-17.0.7/bin/javac.exe'
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
def run(n,args,rc=0,reason=None):
    assert not (O/(n+'.rc.txt')).exists(),n
    p=subprocess.run(list(map(str,args)),capture_output=True,timeout=240)
    for ext,b in [('stdout',p.stdout),('stderr',p.stderr)]: (O/(n+'.'+ext+'.txt')).write_bytes(b)
    (O/(n+'.command.json')).write_text(json.dumps(list(map(str,args))));(O/(n+'.rc.txt')).write_text(str(p.returncode))
    assert p.returncode==rc,(n,p.stderr)
    if rc and reason:assert not p.stdout and reason.encode() in p.stderr,n
    return p.stdout
a=json.loads((O/'actual.command.json').read_text());snapshot=Path(a[4]);digest=a[5];leaf=a[6];captures=a[7:]
classes=O/'classes';classes.mkdir();probes=['PhysicalTransactionProbe.java','VerifiedHeapRowsProbe.java','CapturedRowsProbe.java','PlainBaselineRowsProbe.java']
run('compile-probes',[JAVAC,'-encoding','UTF-8','-cp',J,'-d',classes]+[R/'lab'/n for n in probes]);cp=str(J)+';'+str(classes)
run('plain-baseline',[JAVA,'-cp',cp,'PlainBaselineRowsProbe',snapshot,digest,leaf,F/'archive-46.dbf',F/'archive-47.dbf'],1,'ROW_REPLAY_INCOMPLETE_INSERT')
for name,before,after in [('nonnullable-tail','3|3|NOTE|VARCHAR2|Y','3|3|NOTE|VARCHAR2|N'),('changed-receipt','SNAPSHOT_SCN|2423979','SNAPSHOT_SCN|2423980')]:
    p=O/(name+'.snapshot');p.write_text(snapshot.read_text().replace(before,after));bad=a.copy();bad[4]=str(p)
    if name=='nonnullable-tail':bad[5]=sha(p)
    run(name,bad,1,'ROW_REPLAY_INCOMPLETE_INSERT' if name=='nonnullable-tail' else 'SNAPSHOT_ACQUISITION_DIGEST_MISMATCH')
# Only the heap inverse value changes. Re-seal block and journal checksums, retaining all frame metadata.
original=(F/'capture/sequence-47.oracap').read_bytes();frames=[];blocks=[];offset=0
while offset<len(original):
    m,n=struct.unpack_from('<II',original,offset+8);frames.append(json.loads(original[offset+16:offset+16+m]));blocks.append(bytearray(original[offset+16+m:offset+16+m+n]));offset+=16+m+n+32
virtual=b''.join(bytes(b[16:]) for b in blocks[2:]);start=148-16;length=struct.unpack_from('<I',virtual,start)[0];record=virtual[start:start+length];pos=68 if record[4]&4 else 24;vectors=[]
while pos<len(record):
    table=struct.unpack_from('<H',record,pos+32)[0];data=pos+32+((table+3)//4)*4;fields=[]
    for at in range(pos+34,pos+32+table,2):
        n=struct.unpack_from('<H',record,at)[0];fields.append((data,n));data+=((n+3)//4)*4
    vectors.append((tuple(record[pos:pos+2]),fields));pos=data
assert vectors[4][0]==(5,1);value=[p for p,n in vectors[4][1] if n==2 and record[p:p+n]==b'\xc1\x0c'];assert len(value)==1
index=start+value[0]+1;blocks[2+index//496][16+index%496]=13
data=bytearray()
for metadata,payload in zip(frames,blocks):
    struct.pack_into('<H',payload,14,0);checksum=0
    for word in struct.unpack('<256H',payload):checksum^=word
    struct.pack_into('<H',payload,14,checksum);metadata=dict(metadata);metadata['sha256']=hashlib.sha256(payload).hexdigest();encoded=json.dumps(metadata,sort_keys=True,separators=(',',':')).encode()
    data+=b'ORACAP01'+struct.pack('<II',len(encoded),512)+encoded+payload+hashlib.sha256(encoded+payload).digest()
p=O/'late-delete-before.oracap';p.write_bytes(data);bad=a.copy();bad[-2:]=[str(p),sha(p)]
run('late-delete-before',bad,1,'ROW_REPLAY_BEFORE_VALUE_MISMATCH')
state=run('state',[JAVA,'-cp',cp,'CapturedRowsProbe','state',snapshot,digest,leaf]+captures)
assert state.decode().strip()=='STATE|73254|50333103|0|VALUE:80,VALUE:6e756c6c2d73656564,VALUE:616e63686f72'
archive=run('archive-parity',[JAVA,'-cp',cp,'VerifiedHeapRowsProbe',F/'archive-46.dbf',snapshot,digest,leaf,F/'archive-47.dbf'])
rows=b'\n'.join(l for l in (O/'actual.stdout.txt').read_bytes().splitlines() if l.startswith(b'ROW|'))+b'\n'
assert archive.replace(b'\r\n',b'\n')==rows
for name,old in [('old-nonnull',R/'lab/out/baseline-physical-scope-20261005/actual-r2.command.json'),('old-update',R/'lab/out/captured-batch-20261005/actual.command.json')]:
    args=json.loads(old.read_text());args[2]=a[2];assert run(name,args)==old.with_name(old.name.replace('command.json','stdout.txt')).read_bytes()
with zipfile.ZipFile(J) as new,zipfile.ZipFile(R/'lab/out/unreferenced-commit-20261005/kernel.jar') as old:
    for n in ['HeapRowPatch','UndoRowPatch','HeapUndoPair']:assert new.read('com/streamhouse/oracle/'+n+'.class')==old.read('com/streamhouse/oracle/'+n+'.class')
proof={'complete_null_events':2,'archive_capture_equal':True,'final_seed_equal':True,'physical_parser_classes_unchanged':True,'old_batches_output_byte_equal':True,'plain_baseline_nonnullable_changed_receipt_late_before_refused':True,'new_source_actions':0,'full_dml_matrix_accepted':False}
(O/'proof.json').write_text(json.dumps(proof,indent=2));print(json.dumps(proof))
