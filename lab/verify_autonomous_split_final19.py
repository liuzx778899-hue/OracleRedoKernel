"""Verify the final allocation-aware gate against all preserved raw counterexamples."""
import hashlib,json,struct,subprocess
from pathlib import Path
R=Path(__file__).resolve().parent.parent;N=R/'lab/out/dictionary-split-candidate-20261006';H=R/'lab/out/v3-product-history-20261006'
assert not (N/'r3-split-proof.json').exists();(N/'executed-final-split-verifier.py').write_bytes(Path(__file__).read_bytes())
j='D:/work/Java/jdk-17.0.7/bin/';cp=str(N/'kernel-r3.jar')+';'+str(N/'classes-r3');receipt='323fd35a8ceee43545c28a9fb32f55cedd41409a3e1b5fb63e08606380cd24a2'
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
def run(name,args,rc):
    args=list(map(str,args));assert not (N/(name+'.command.json')).exists();(N/(name+'.command.json')).write_text(json.dumps(args));p=subprocess.run(args,capture_output=True,timeout=900)
    for ext,value in [('stdout.txt',p.stdout),('stderr.txt',p.stderr),('rc.txt',str(p.returncode).encode())]:(N/(name+'.'+ext)).write_bytes(value)
    assert p.returncode==rc,(name,p.returncode,p.stderr[-300:]);print(name,rc,flush=True);return p
base=[j+'java.exe','-Xmx1536m','-cp',cp,'ArchivedDictionarySplitProbe',H/'actual-product-v3.snapshot',receipt]
positive=(N/'r3-split.stdout.txt').read_bytes();assert positive==(N/'r3-captured-split.stdout.txt').read_bytes()
assert run('r3-repeat',base+[H/'archive-70.dbf',H/'archive-71.dbf'],0).stdout==positive
old=json.loads((N/'split-proof.json').read_text());cases=[]
for case in old['negative']:
    path=N/('DERIVED-split-'+case['name']+'.dbf');assert sha(path)==case['sha256'] and (N/(case['name']+'-scan.rc.txt')).read_text()=='0';cases.append((case['name'],path,case))
original=(H/'archive-70.dbf').read_bytes();payload=b''.join(original[i+16:i+512] for i in range(0,len(original),512));start=126610*496+188-16
field=bytes.fromhex('0400000001000000000000000100000000000000470000002f51400031514000');at=payload.find(field,start,start+140);assert at>=0
bf=bytes.fromhex('010000000000000000000000');bt=payload.find(bf,at+32,start+140);assert bt>=0
for name,offset in [('wrong-freelist-head',at+24),('unknown-freelist-clear',bt),('missing-freelist-control',start+24+1)]:
    pos=offset//496*512+16+offset%496;raw=bytearray(original);raw[pos]^=1;block=pos//512*512;raw[block+14:block+16]=b'\0\0';checksum=0
    for word in struct.unpack('<256H',raw[block:block+512]):checksum^=word
    struct.pack_into('<H',raw,block+14,checksum);path=N/('DERIVED-split-'+name+'.dbf');path.write_bytes(raw)
    run('r3-'+name+'-scan',[j+'java.exe','-cp',N/'kernel-r3.jar','com.streamhouse.oracle.RedoCli','scan',path],0)
    cases.append((name,path,dict(name=name,structure_rc=0,raw_offset=pos,old=original[pos],new=raw[pos],sha256=sha(path))))
proof=[]
for name,path,case in cases:
    p=run('r3-'+name+'-split',base+[path,H/'archive-71.dbf'],1);assert not p.stdout
    proof.append(dict(case,split_rc=1,events=0,reason=p.stderr.decode().splitlines()[0]))
(N/'r3-split-proof.json').write_text(json.dumps(dict(kernel_sha256=sha(N/'kernel-r3.jar'),autonomous_splits=1,original_nodes=5,physical_control_records=1,end_rebuild_records=1,archive_capture_equal=True,repeat_equal=True,negative=proof,source_actions=0,target_actions=0,events=0),indent=2))
print('ALLOCATION_AWARE_SPLIT_GATE|negative='+str(len(proof)),flush=True)
