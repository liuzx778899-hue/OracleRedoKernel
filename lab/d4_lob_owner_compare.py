"""Independent full native XID/UBA/object comparison of SecureFile original bindings."""
from pathlib import Path
import json,re
R=Path(__file__).resolve().parent.parent;F=R/'lab/out/d4-lob-source-20261009/continue-r2';N=F/'inventory-r8'
raw=[]
for line in (N/'inventory.stdout').read_text().splitlines():
 if line.startswith('PAIR|'):
  f=line.split('|');raw.append(dict(position=list(map(int,f[1:4])),object=int(f[5]),xid=f[6],uba=list(map(int,f[7:10]))))
native=[];section=[];selected=False;position=None
def finish():
 if not selected:return
 text='\n'.join(section)
 obj=re.search(r'opc: 26\.1 \[objn: (79341|79343) objd: (\d+) tsn: 5\]',text)
 if not obj:return
 xid=re.search(r'\n\s+xid:\s+0x([0-9a-f]+)\.([0-9a-f]+)\.([0-9a-f]+)',text)
 seq=re.search(r'ktudb redo:.*seq: 0x([0-9a-f]+) rec: 0x([0-9a-f]+)',text)
 assert xid and seq and obj[1]==obj[2]
 native.append(dict(position=position,object=int(obj[1]),xid='.'.join(str(int(x,16)) for x in xid.groups()),uba=[dba,int(seq[1],16),int(seq[2],16)]))
for line in (F/'97.trc').read_text().splitlines()+['END']:
 if 'REDO RECORD -' in line or 'CHANGE #' in line or line=='END':finish();selected=False;section=[]
 m=re.search(r'RBA: 0x([0-9a-f]+)\.([0-9a-f]+)\.([0-9a-f]+)',line)
 if m:position=[int(x,16) for x in m.groups()]
 m=re.search(r'CHANGE #\d+ CON_ID:3 .* DBA:0x([0-9a-f]+).*OP:5\.1 ',line)
 if m:selected=True;dba=int(m[1],16)
 if selected:section.append(line)
key=lambda x:(x['position'],x['uba'])
assert len(raw)==len(native)==10,(len(raw),len(native))
assert sorted(raw,key=key)==sorted(native,key=key)
poison=[dict(x) for x in raw];poison[0]['xid']='10.13.4261';assert sorted(poison,key=key)!=sorted(native,key=key)
proof=dict(originalPairs=10,ownerXids=sorted({x['xid'] for x in raw}),fullNativeOwnerMismatch=0,poisonDetected=1,events=0,commitAuthorized=False,rollbackRoutingAccepted=False)
(N/'owner-comparison.json').write_text(json.dumps(proof,indent=2));print(json.dumps(proof))
