"""Capture graph/semantic whole-batch refusals from frozen raw bytes. No source actions."""
import hashlib,json,struct,subprocess,sys
from pathlib import Path

ROOT=Path(__file__).resolve().parent.parent;OLD=ROOT/'lab/out/closed-follow-20261005';GOOD=ROOT/'lab/out/capture-rows-r2-20261005';OUT=ROOT/'lab/out/capture-row-faults-20261005'
JAVA='D:/work/Java/jdk-17.0.7/bin/java.exe';continuing=sys.argv[1:]==['--continue-after-reason']
if not continuing:OUT.mkdir(parents=True,exist_ok=False)
java=[JAVA,'-cp',str(GOOD/'kernel.jar')+';'+str(GOOD/'classes'),'CapturedRowsProbe'];snapshot=OLD/'baseline/snapshot.stdout.txt';digest=json.loads((OLD/'baseline/receipt.json').read_text())['snapshot_sha256'];leaf='0x0300019b'
items=json.loads((OLD/'capture/receipt.json').read_text())['captured'];pairs=[[str(OLD/'capture'/item['journal']),item['sha256']] for item in items]

def run(name,args,expected=0,reason=None,empty=True):
    (OUT/(name+'.command.json')).write_text(json.dumps(list(map(str,args))))
    result=subprocess.run(list(map(str,args)),capture_output=True,timeout=240)
    for ext,data in [('stdout',result.stdout),('stderr',result.stderr)]:(OUT/(name+'.'+ext+'.txt')).write_bytes(data)
    (OUT/(name+'.rc.txt')).write_text(str(result.returncode))
    if result.returncode!=expected or expected and empty and result.stdout or reason and reason.encode() not in result.stderr:raise RuntimeError(name+': unexpected result; preserve')
    return result

def args(mode,selected):return java+[mode,str(snapshot),digest,leaf]+[value for pair in selected for value in pair]
if not continuing:
    for name,selection in [('missing-sequence',[pairs[0],pairs[2]]),('reordered-sequences',[pairs[2],pairs[0],pairs[1]]),('duplicate-sequence',[pairs[0],pairs[1],pairs[1],pairs[2]])]:
        run(name,args('rows',selection),1,'ARCHIVE_SEQUENCE_OR_SCN_GAP')
    bad=args('rows',pairs);bad[6]='0'*64;run('untrusted-baseline',bad,1,'SNAPSHOT_ACQUISITION_DIGEST_MISMATCH')
else:
    assert (OUT/'untrusted-baseline.rc.txt').read_text()=='1' and not (OUT/'untrusted-baseline.stdout.txt').read_bytes()
    assert 'SNAPSHOT_ACQUISITION_DIGEST_MISMATCH' in (OUT/'untrusted-baseline.stderr.txt').read_text()

original=Path(pairs[2][0]).read_bytes();frames=[];raw=[];offset=0
while offset<len(original):
    m,n=struct.unpack_from('<II',original,offset+8);metadata=json.loads(original[offset+16:offset+16+m]);payload=bytearray(original[offset+16+m:offset+16+m+n]);frames.append(metadata);raw.append(payload);offset+=16+m+n+32

def reseal(name,blocks):
    data=bytearray()
    for metadata,payload in zip(frames,blocks):
        struct.pack_into('<H',payload,14,0);checksum=0
        for word in struct.unpack('<256H',payload):checksum^=word
        struct.pack_into('<H',payload,14,checksum);metadata=dict(metadata);metadata['sha256']=hashlib.sha256(payload).hexdigest();encoded=json.dumps(metadata,sort_keys=True,separators=(',',':')).encode()
        data+=b'ORACAP01'+struct.pack('<II',len(encoded),512)+encoded+payload+hashlib.sha256(encoded+payload).digest()
    path=OUT/(name+'.oracap');path.write_bytes(data);return [str(path),hashlib.sha256(data).hexdigest()]

changed=[bytearray(block) for block in raw];matches=[(i,block.find(b'capture-follow-b')) for i,block in enumerate(changed) if b'capture-follow-b' in block];assert len(matches)==1
block,offset=matches[0];changed[block][offset]=ord('x');poison=reseal('late-before',changed);selected=pairs[:2]+[poison]
run('late-before-physical',args('physical',selected))
run('late-before-rows',args('rows',selected),1,'ROW_REPLAY_BEFORE_VALUE_MISMATCH')

# Locate vector2 of the first actual seq42 record across raw block headers, then remove only 5.2 opcode.
record=[line.split('|') for line in (ROOT/'lab/out/capture-records-20261005/capture-records-42.stdout.txt').read_text().splitlines()][0]
block,offset,length=map(int,record[:3]);virtual=b''.join(bytes(b[16:]) for b in raw[2:]);start=(block-2)*496+offset-16
body=virtual[start:start+length];position=68 if body[4]&4 else 24;assert body[position]==11 and body[position+1]==5
table=struct.unpack_from('<H',body,position+32)[0];position+=32+(table+3)//4*4+sum((struct.unpack_from('<H',body,f)[0]+3)//4*4 for f in range(position+34,position+32+table,2))
assert body[position]==5 and body[position+1]==2
index=start+position+1;changed=[bytearray(b) for b in raw];changed[2+index//496][16+index%496]=0;poison=reseal('missing-begin-control',changed)
run('missing-begin-control',args('rows',pairs[:2]+[poison]),1,'COMPENSATION_INVENTORY_BEGIN_CONTROL_COUNT')

dumps=[ROOT/'lab/out/capture-records-20261005'/('dump-'+str(n)+'.trc') for n in (40,41,42)]
compare=[sys.executable,ROOT/'lab/compare_capture_rows.py',snapshot,GOOD/'capture-rows.stdout.txt',GOOD/'capture-state.stdout.txt',OLD]+dumps
run('native-rows',compare)
rows=(GOOD/'capture-rows.stdout.txt').read_text().splitlines();state=(GOOD/'capture-state.stdout.txt').read_text()
wrong=rows.copy();p=wrong[-1].split('|');p[-1]=p[-1].replace('VALUE:626c6f636b696e672d636c6f736564','VALUE:786c6f636b696e672d636c6f736564');wrong[-1]='|'.join(p)
for name,output in [('missing-row',rows[:-1]),('reordered-rows',list(reversed(rows))),('wrong-after',wrong)]:
    path=OUT/(name+'.rows.txt');path.write_text('\n'.join(output)+'\n');command=compare.copy();command[3]=path;run(name,command,1,empty=False)
path=OUT/'wrong-state.txt';path.write_text(state.replace('VALUE:626c6f636b696e672d636c6f736564','VALUE:786c6f636b696e672d636c6f736564'));command=compare.copy();command[4]=path;run('wrong-state',command,1,empty=False)
print(json.dumps({'capture_graph_row_refusals':6,'late_before_physical_passes_rows_refuse':True,'independent_row_comparator_refusals':4,'cdc_events':0,'consumer_ack':False}))
