"""Checksum-valid fresh-history counterexamples; source transactions never replayed."""
import hashlib,json,re,struct,subprocess
from pathlib import Path
R=Path(__file__).resolve().parent.parent;O=R/'lab/out/v3-historical-raw-20261006';K=O/'new-kernel.jar';sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
assert not (O/'history-negative-proof.json').exists();round=1+len(list(O.glob('executed-history-negative-driver-r*.py')));(O/f'executed-history-negative-driver-r{round}.py').write_bytes(Path(__file__).read_bytes());ref=json.loads((O/'capture-reference.json').read_text());b=ref['baseline'];records=json.loads((O/'new-raw-records.json').read_text());objects=json.loads((O/'new-obj-shapes.json').read_text())
def run(name,args,rc=0):
    args=list(map(str,args));assert not (O/(name+'.command.json')).exists();(O/(name+'.command.json')).write_text(json.dumps(args));p=subprocess.run(args,capture_output=True,timeout=180)
    for suffix,data in [('stdout.txt',p.stdout),('stderr.txt',p.stderr),('rc.txt',str(p.returncode).encode())]:(O/(name+'.'+suffix)).write_bytes(data)
    assert p.returncode==rc,p.stderr.decode();return p
j='D:/work/Java/jdk-17.0.7/bin/';args=[j+'java.exe','-Xmx1536m','-cp',str(K)+';'+str(O/'classes'),'ArchivedDictionaryHistoryEvidenceProbe',b['path'],b['sha256']]
original=(O/'archive-68.dbf').read_bytes();payload=b''.join(original[i+16:i+512] for i in range(0,len(original),512));physical=lambda at:at//496*512+16+at%496
def locate(field,position):
    start=position[1]*496+position[2]-16;raw=bytes.fromhex(field);at=payload.find(raw,start,start+2048);assert at>=0
    assert payload.find(raw,at+1,start+2048)<0;return at
obj=objects[0];forward=obj['forward'][0];undo=obj['undo'];kf=locate(forward['fields'][1],obj['position']);ki=locate(undo['fields'][3],obj['position']);ktb=locate(forward['fields'][0],obj['position']);meta=locate(undo['fields'][1],obj['position'])
cases=[('ckix-unknown',[(kf+18,238),(ki+18,238)]),('ckix-pair-mismatch',[(kf+18,0)]),('wrong-current-full-XID',[(ktb+12,payload[ktb+12]^1)]),('wrong-generation',[(meta+4,19)])]
# The first changed inverse value is OBJ#; poison a valid NUMBER without altering the binding metadata.
before=payload.find(bytes.fromhex(undo['fields'][5]),ki+len(bytes.fromhex(undo['fields'][3])),ktb);assert before>=0
cases.append(('wrong-complete-before-parent',[(before+3,payload[before+3]^1)]))
idx=next(r for r in records if any(v['opcode']=='5.1' and struct.unpack_from('<I',bytes.fromhex(v['fields'][1]))[0]==48 for v in r['vectors']))
v=next(v for v in idx['vectors'] if v['opcode']=='10.2');key=bytes.fromhex(v['fields'][2]);start=idx['position'][1]*496+idx['position'][2]-16
keyPositions=[];at=start
while True:
    at=payload.find(key,at,start+512)
    if at<0:break
    keyPositions.append(at);at+=len(key)
assert len(keyPositions)==2;cases.append(('wrong-index-parent',[(at+4,payload[at+4]+1) for at in keyPositions]))
first=records[0]['position'];recordStart=first[1]*496+first[2]-16;header=68 if payload[recordStart+4]&4 else 24
assert payload[recordStart+header:recordStart+header+2]==b'\x05\x02';cases.append(('missing-begin-control',[(recordStart+header,0),(recordStart+header+1,0)]))
proof=[]
for name,mutations in cases:
    raw=bytearray(original);blocks=set()
    for at,value in mutations:
        pos=physical(at);assert raw[pos]!=value;raw[pos]=value;blocks.add(pos//512*512)
    for block in blocks:
        raw[block+14:block+16]=b'\0\0';checksum=0
        for i in range(block,block+512,2):checksum^=int.from_bytes(raw[i:i+2],'little')
        raw[block+14:block+16]=checksum.to_bytes(2,'little')
    path=O/('DERIVED-history-'+name+'.dbf');path.write_bytes(raw)
    run(name+'-scan',[j+'java.exe','-cp',K,'com.streamhouse.oracle.RedoCli','scan',path])
    p=run(name+'-history',args+[path,O/'archive-69.dbf'],1);assert not p.stdout
    proof.append(dict(name=name,structure_rc=0,history_rc=1,events=0,reason=p.stderr.decode().splitlines()[0],mutations=[dict(raw_offset=physical(at),old=payload[at],new=value) for at,value in mutations]))
(O/'history-negative-proof.json').write_text(json.dumps(dict(kernel_sha256=sha(K),baseline_sha256=b['sha256'],cases=proof,source_actions=0,target_actions=0),indent=2));print(json.dumps(dict(semantic_refusals=len(proof),events=0)))
