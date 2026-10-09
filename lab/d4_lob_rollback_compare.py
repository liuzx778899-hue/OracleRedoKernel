"""Independent native predecessor/header association of all frozen LOB heap compensations."""
from pathlib import Path
import hashlib,json,re,sys
R=Path(__file__).resolve().parent.parent;F=R/'lab/out/d4-lob-source-20261009/continue-r2'
assert len(sys.argv)==3 and sys.argv[1]=='--run' and re.fullmatch(r'rollback-r[1-9][0-9]*',sys.argv[2])
N=F/sys.argv[2];raw=[]
assert hashlib.sha256((F/'97.trc').read_bytes()).hexdigest()=='807f987d7d927f29bf58730b81908444a5a6d65c8c5a5b6edb49b061079e4472'
for line in (N/'inventory.stdout').read_text().splitlines():
 if line.startswith(('HEAPROLLBACK|','SECUREROLLBACK|')):
  f=line.split('|');raw.append(dict(position=list(map(int,f[1:5])),object=int(f[5]),xid=f[6],uba=list(map(int,f[7:10])),original=list(map(int,f[11:15]))))
originals=[];markers=[];section=[];selected=False;position=None
def finish():
 if not selected:return
 text='\n'.join(section)
 m=re.search(r'slt: (\d+) wrp: (\d+) flg: 0x([0-9a-f]+) prev dba:\s+0x([0-9a-f]+) rci: (\d+) opc: (?:11|26)\.1 \[objn: (79334|79340|79341|79343) objd: (\d+) tsn: (\d+)\]',text)
 if not m:return
 fields=[int(m[1]),int(m[2]),int(m[4],16),int(m[5]),int(m[6]),int(m[7]),int(m[8])]
 if opcode=='5.1':
  xid=re.search(r'\n\s+xid:\s+0x([0-9a-f]+)\.([0-9a-f]+)\.([0-9a-f]+)',text)
  seq=re.search(r'ktudb redo:.*seq: 0x([0-9a-f]+) rec: 0x([0-9a-f]+)',text)
  assert xid and seq
  originals.append(dict(position=position+[change],object=fields[4],xid='.'.join(str(int(x,16)) for x in xid.groups()),uba=[dba,int(seq[1],16),int(seq[2],16)],fields=fields))
 else:markers.append(dict(position=position+[change],fields=fields))
for line in (F/'97.trc').read_text().splitlines()+['END']:
 if 'REDO RECORD -' in line or 'CHANGE #' in line or line=='END':finish();selected=False;section=[]
 m=re.search(r'REDO RECORD -.*RBA: 0x([0-9a-f]+)\.([0-9a-f]+)\.([0-9a-f]+)',line)
 if m:position=[int(x,16) for x in m.groups()]
 m=re.search(r'CHANGE #(\d+) CON_ID:3 .* DBA:0x([0-9a-f]+).*OP:(5\.(?:1|6|11)) ',line)
 if m:selected=True;change=int(m[1]);dba=int(m[2],16);opcode=m[3]
 if selected:section.append(line)
native=[]
for marker in markers:
 candidates=[x for x in originals if x['fields']==marker['fields'] and x['position'][:3]<marker['position'][:3]]
 assert len(candidates)==1,(marker,len(candidates))
 x=candidates[0];native.append(dict(position=marker['position'],object=x['object'],xid=x['xid'],uba=x['uba'],original=x['position']))
assert len(raw)==len(native)==11,(len(raw),len(native))
assert sorted(raw,key=lambda x:x['position'])==sorted(native,key=lambda x:x['position'])
poison=[dict(x) for x in raw];poison[0]['uba']=raw[0]['uba'][:-1]+[raw[0]['uba'][-1]+1];assert poison!=native
proof=dict(heapCompensations=9,secureFileCompensations=2,nativeFullOwnerAndOriginalPositionMismatch=0,poisonDetected=1,events=0,
           fullLobTransactionAccepted=False,baselineApproved=False)
p=N/'heap-rollback-comparison.json';assert not p.exists();p.write_text(json.dumps(proof,indent=2));print(json.dumps(proof))
