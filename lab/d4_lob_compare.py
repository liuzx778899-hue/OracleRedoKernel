"""Compare typed raw BasicFile pages with independently frozen native dump, never produce events."""
from pathlib import Path
import hashlib,json,re
R=Path(__file__).resolve().parent.parent
F=R/'lab/out/d4-lob-source-20261009/continue-r2';N=F/'inventory-r2'
raw=[]
for line in (N/'inventory.stdout').read_text().splitlines():
 if line.startswith('PAGE|'):
  f=line.split('|');raw.append(dict(position=list(map(int,f[1:5])),object=int(f[5]),lobId=f[6],version=int(f[7]),page=int(f[8]),parent=int(f[9]),sha256=f[10],payload=f[11]))
native=[];position=None;selected=False;item=None;data=[]
lines=(F/'97.trc').read_text().splitlines()
for line in lines+['END']:
 m=re.search(r'RBA: 0x([0-9a-f]+)\.([0-9a-f]+)\.([0-9a-f]+)',line)
 if m:position=[int(v,16) for v in m.groups()]
 m=re.search(r'CHANGE #(\d+).*CON_ID:3 .*OP:19\.1 ',line)
 if m:selected=True;change=int(m[1])
 if selected and line.startswith('Object Id'):item=dict(position=position+[change],object=int(line.split()[-1]));data=[]
 if selected and line.startswith('LobId:'):
  # BasicFile native prints two numeric ID components at widths 4+9, unlike the raw 4+6 bytes.
  m=re.fullmatch(r'LobId: ([0-9A-F]{4})([0-9A-F]{9}) PageNo\s+(\d+)\s*',line);assert m
  item.update(lobId=m[1].lower().zfill(8)+m[2].lower().zfill(12),page=int(m[3]))
 if selected and line.startswith('Version:'):
  m=re.fullmatch(r'Version: 0x([0-9a-f]+)\.([0-9a-f]+)\s+pdba: (\d+)\s*',line);assert m
  item.update(version=(int(m[1],16)<<32)|int(m[2],16),parent=int(m[3]))
 if selected and re.fullmatch(r'\s*(?:[0-9a-f]{2}\s+)+[0-9a-f]{2}\s*',line):data.extend(line.split())
 elif selected and data:
  payload=bytes.fromhex(''.join(data));item.update(sha256=hashlib.sha256(payload).hexdigest(),payload=payload.hex());native.append(item);selected=False;data=[]
assert len(raw)==len(native)==18,(len(raw),len(native))
for i,(actual,expected) in enumerate(zip(raw,native)):
 assert actual==expected,(i,{k:(actual[k],expected[k]) for k in actual if k!='payload' and actual[k]!=expected[k]},len(actual['payload']),len(expected['payload']))
# The comparator must detect a meaningful decoded identifier or payload mismatch.
poison=[dict(x) for x in raw];poison[0]['lobId']='00'*10;assert poison!=native
poison=[dict(x) for x in raw];poison[0]['payload']='ff'+poison[0]['payload'][2:];assert poison!=native
proof=dict(rawSha256=hashlib.sha256((F/'97.raw').read_bytes()).hexdigest(),nativeSha256=hashlib.sha256((F/'97.trc').read_bytes()).hexdigest(),pages=18,fullPayloadBytes=sum(len(x['payload'])//2 for x in raw),mismatches=0,poisonDetected=2,events=0,role='STRUCTURAL_ONLY')
(N/'page-comparison.json').write_text(json.dumps(proof,indent=2));print(json.dumps(proof))
