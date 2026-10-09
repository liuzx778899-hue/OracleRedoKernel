"""Compare every selected raw vector object ID with independent native vector headers."""
from pathlib import Path
import hashlib,json,re,sys
R=Path(__file__).resolve().parent.parent;F=R/'lab/out/d4-lob-source-20261009/continue-r2'
assert len(sys.argv)==3 and sys.argv[1]=='--run' and re.fullmatch(r'inventory-r[1-9][0-9]*',sys.argv[2]);N=F/sys.argv[2]
assert hashlib.sha256((F/'97.trc').read_bytes()).hexdigest()=='807f987d7d927f29bf58730b81908444a5a6d65c8c5a5b6edb49b061079e4472'
raw=[]
for line in (N/'inventory.stdout').read_text().splitlines():
 f=line.split('|')
 if f[0]=='RECORD':position=list(map(int,f[1:4]))
 if f[0]=='VECTOR':raw.append(dict(position=position+[int(f[1])+1],opcode=f[2],dba=int(f[3]),object=int(f[6])))
native=[];unprinted=[];selected=False;awaitScn=False;cutoff=json.loads((F/'source-frozen-proof.json').read_text())['firstScn']
for line in (F/'97.trc').read_text().splitlines():
 if line.startswith('REDO RECORD -'):
  m=re.search(r'RBA: 0x([0-9a-f]+)\.([0-9a-f]+)\.([0-9a-f]+).*CON_UID: (\d+)',line)
  assert m;position=[int(x,16) for x in m.groups()[:3]];selected=int(m[4])==677737149;awaitScn=True
 elif line.startswith('SCN:') and awaitScn:
  m=re.search(r'SCN: 0x([0-9a-f]+)',line);assert m;selected=selected and int(m[1],16)>cutoff;awaitScn=False
 elif 'CHANGE #' in line and selected:
  if 'MEDIA RECOVERY MARKER' in line:
   m=re.search(r'CHANGE #(\d+).*OP:(\d+\.\d+) ',line);assert m
   unprinted.append(dict(position=position+[int(m[1])],opcode=m[2]));continue
  m=re.search(r'CHANGE #(\d+) CON_ID:\d+ .* DBA:0x([0-9a-f]+) OBJ:(\d+).* OP:(\d+\.\d+) ',line)
  assert m,line;native.append(dict(position=position+[int(m[1])],opcode=m[4],dba=int(m[2],16),object=int(m[3])))
key=lambda x:x['position']
hidden={tuple(x['position']) for x in unprinted}
assert sorted([dict(position=x['position'],opcode=x['opcode']) for x in raw if tuple(x['position']) in hidden],key=key)==sorted(unprinted,key=key)
raw=[x for x in raw if tuple(x['position']) not in hidden]
assert raw and len(raw)==len(native),(len(raw),len(native))
assert sorted(raw,key=key)==sorted(native,key=key)
poison=[dict(x) for x in raw];poison[0]['object']^=65536;assert sorted(poison,key=key)!=sorted(native,key=key)
proof=dict(vectors=len(raw),nativeObjectNotPrintedMediaMarkers=len(unprinted),fullNativeObjectMismatch=0,highHalfPoisonDetected=1,events=0)
p=N/'vector-object-comparison.json';assert not p.exists();p.write_text(json.dumps(proof,indent=2));print(json.dumps(proof))
