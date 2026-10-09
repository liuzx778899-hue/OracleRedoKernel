"""Independent native-versus-raw comparison of the frozen SecureFile fill records."""
from pathlib import Path
import hashlib,json,re
R=Path(__file__).resolve().parent.parent
F=R/'lab/out/d4-lob-source-20261009/continue-r2';N=F/'inventory-r3'
raw=[]
for line in (N/'inventory.stdout').read_text().splitlines():
 if line.startswith('FILL|'):
  f=line.split('|');raw.append(dict(position=list(map(int,f[1:5])),dba=int(f[5]),lobId=f[6],undo=f[7]=='true',offset=int(f[8]),highWater=int(f[9]),sha256=f[10],payload=f[11]))
native=[];position=None;selected=False;section=[]
def finish():
 if not selected:return
 text='\n'.join(section)
 dba=re.search(r'\n  dba\s+0x([0-9a-f]+)',text);lob=re.search(r'\n  (?:lid\s+|lobid )([0-9a-f]{20})',text)
 offset=re.search(r'\n  foff\s+0x([0-9a-f]+)',text);size=re.search(r'\n  fsiz\s+(\d+)',text);length=re.search(r'\n  flen\s+(\d+)',text)
 high=re.search(r'\n  hwm\s+(\d+)',text);direction=re.search(r'\n  op\s+0x0([01]) \[(REDO|UNDO)\]',text)
 assert all([dba,lob,offset,size,length,direction])
 data=text.split('\n  data\n',1)[1];payload=[]
 for line in data.splitlines():
  if not re.fullmatch(r'\s*(?:[0-9a-f]{2}\s+)+[0-9a-f]{2}\s*',line):break
  payload.extend(line.split())
 payload=bytes.fromhex(''.join(payload));assert len(payload)==int(length[1])==int(size[1])
 native.append(dict(position=position+[change],dba=int(dba[1],16),lobId=lob[1],undo=direction[1]=='1',offset=int(offset[1],16),highWater=int(high[1]) if high else -1,sha256=hashlib.sha256(payload).hexdigest(),payload=payload.hex()))
for line in (F/'97.trc').read_text().splitlines()+['END']:
 if 'REDO RECORD -' in line or 'CHANGE #' in line or line=='END':
  finish();selected=False;section=[]
 m=re.search(r'RBA: 0x([0-9a-f]+)\.([0-9a-f]+)\.([0-9a-f]+)',line)
 if m:position=[int(v,16) for v in m.groups()]
 m=re.search(r'CHANGE #(\d+).*CON_ID:3 .*OP:26\.2 ',line)
 if m:selected=True;change=int(m[1])
 if selected:section.append(line)
assert len(raw)==len(native)==12,(len(raw),len(native))
assert raw==native,[(i,{k:(a[k],b[k]) for k in a if k!='payload' and a[k]!=b[k]}) for i,(a,b) in enumerate(zip(raw,native)) if a!=b]
for key,value in [('offset',101),('payload','00')]:
 poison=[dict(x) for x in raw];poison[0][key]=value;assert poison!=native
proof=dict(fills=12,undoFills=sum(x['undo'] for x in raw),payloadBytes=sum(len(x['payload'])//2 for x in raw),mismatches=0,poisonDetected=2,events=0,role='STRUCTURAL_ONLY')
(N/'fill-comparison.json').write_text(json.dumps(proof,indent=2));print(json.dumps(proof))
