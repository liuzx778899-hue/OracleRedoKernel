"""Frozen real flags0 commit graph audit and resealed counterexamples; no source actions."""
import hashlib,json,struct,subprocess,sys
from pathlib import Path
R=Path(__file__).resolve().parent.parent;O=R/'lab/out/unreferenced-commit-20261005';F=R/'lab/out/null-dml-capture-20261005'
J=O/'kernel.jar';JAVA='D:/work/Java/jdk-17.0.7/bin/java.exe'
def run(name,args,rc=0,reason=None):
    assert not (O/(name+'.rc.txt')).exists(),name
    (O/(name+'.command.json')).write_text(json.dumps(list(map(str,args))))
    p=subprocess.run(list(map(str,args)),capture_output=True,timeout=240)
    for ext,b in [('stdout',p.stdout),('stderr',p.stderr)]: (O/(name+'.'+ext+'.txt')).write_bytes(b)
    (O/(name+'.rc.txt')).write_text(str(p.returncode))
    assert p.returncode==rc,(name,p.stderr)
    if rc: assert not p.stdout and (reason is None or reason.encode() in p.stderr),name
    return p.stdout
original=json.loads((F/'raw-index-leaf.command.json').read_text());snapshot=Path(original[4]);digest=original[5];leaf=(F/'raw-index-leaf.stdout.txt').read_text().strip().split('|')[1]
cp=str(J)+';'+str(R/'lab/out/baseline-physical-scope-20261005/classes')
physical=run('capture-physical',[JAVA,'-cp',cp,'CapturedRowsProbe','physical',snapshot,digest,leaf]+original[6:])
run('native-physical',[sys.executable,R/'lab/compare_typed_physical.py',snapshot,O/'capture-physical.stdout.txt',F/'dump.trc'])
batch=json.loads((F/'raw-complete-batch.command.json').read_text());batch[2]=str(J)+';'+str(R/'lab/out/captured-batch-20261005/classes')
run('complete-null-rows-still-refused',batch,1,'ROW_REPLAY_INCOMPLETE_INSERT')
raw=(F/'archive-47.dbf').read_bytes();mapping=[];block=2;offset=148;length=struct.unpack_from('<I',raw,block*512+offset)[0]
while len(mapping)<length:
    n=min(length-len(mapping),512-offset);mapping.extend(range(block*512+offset,block*512+offset+n));block+=1;offset=16
record=bytes(raw[i] for i in mapping);pos=68 if record[4]&4 else 24;vectors=[]
while pos<len(record):
    table=struct.unpack_from('<H',record,pos+32)[0];data=pos+32+((table+3)//4)*4;fields=[]
    for at in range(pos+34,pos+32+table,2):
        n=struct.unpack_from('<H',record,at)[0];fields.append(mapping[data:data+n]);data+=((n+3)//4)*4
    vectors.append((tuple(record[pos:pos+2]),mapping[pos:pos+32],fields));pos=data
assert [v[0] for v in vectors]==[(11,3),(5,2),(10,4),(5,4),(5,1),(5,1)]
start,end,heap,index=vectors[1],vectors[3],vectors[4],vectors[5]
cases={'referenced-without-tail':[(end[2][0][16],2)],'missing-start':[(start[1][1],0)],
       'missing-heap-undo':[(heap[1][1],0)],'wrong-slot':[(end[2][0][0],1)],
       'unknown-end-flags':[(end[2][0][16],9)],'wrong-predecessor':[(index[2][1][19],0)]}
for name,changes in cases.items():
    bad=bytearray(raw)
    for at,value in changes: assert bad[at]!=value;bad[at]=value
    for block in {at//512 for at,_ in changes}:
        base=block*512;struct.pack_into('<H',bad,base+14,0);checksum=0
        for word in struct.unpack('<256H',bad[base:base+512]):checksum^=word
        struct.pack_into('<H',bad,base+14,checksum)
    p=O/(name+'.dbf');p.write_bytes(bad)
    run(name,[JAVA,'-cp',cp,'PhysicalTransactionProbe','--snapshot',snapshot,digest,leaf,F/'archive-46.dbf',p],1,
        'TRANSACTION_END_HAS_NO_UNDO_TAIL_REFERENCE' if name=='referenced-without-tail' else None)
proof={'physical_transactions':3,'original_nodes':6,'compensated_nodes':0,'live_nodes':6,
       'native_physical_zero_differences':True,'raw_refusals':len(cases),'null_complete_rows_accepted':False,
       'new_source_actions':0,'kernel_sha256':hashlib.sha256(J.read_bytes()).hexdigest()}
(O/'proof.json').write_text(json.dumps(proof,indent=2));print(json.dumps(proof))
