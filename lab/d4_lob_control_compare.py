"""Independent native comparison of the complete fixture undo inventory and control pairs."""
from pathlib import Path
from collections import Counter
import hashlib,json,re,sys
R=Path(__file__).resolve().parent.parent;F=R/'lab/out/d4-lob-source-20261009/continue-r2'
assert len(sys.argv)==3 and sys.argv[1]=='--run' and re.fullmatch(r'rollback-r[1-9][0-9]*',sys.argv[2]);N=F/sys.argv[2]
assert hashlib.sha256((F/'97.trc').read_bytes()).hexdigest()=='807f987d7d927f29bf58730b81908444a5a6d65c8c5a5b6edb49b061079e4472'
lines=(N/'inventory.stdout').read_text().splitlines();xids=set();raw=[];pairs=[]
for line in lines:
 f=line.split('|')
 if f[0]=='OWNEDORIGINAL':
  raw.append(dict(position=list(map(int,f[1:5])),object=int(f[5]),xid=f[6],uba=list(map(int,f[7:10])),opcode=f[10]));xids.add(f[6])
 if f[0] in ['PKPAIR','CONTROLPAIR']:pairs.append(f)
vectors={};section=[];position=None;current=None;recordScn=None
def finish():
 if current is not None:
  assert current not in vectors
  vectors[current]=dict(text='\n'.join(section),recordScn=recordScn)
for line in (F/'97.trc').read_text().splitlines()+['END']:
 if line.startswith('REDO RECORD -') or 'CHANGE #' in line or line=='END':finish();current=None;section=[]
 if line.startswith('REDO RECORD -'):
  m=re.search(r'RBA: 0x([0-9a-f]+)\.([0-9a-f]+)\.([0-9a-f]+)',line);assert m;position=tuple(int(x,16) for x in m.groups());recordScn=None
 elif line.startswith('SCN:') and recordScn is None:recordScn=int(re.search(r'SCN: 0x([0-9a-f]+)',line)[1],16)
 m=re.search(r'CHANGE #(\d+) CON_ID:3 ',line)
 if m:current=position+(int(m[1]),)
 if current is not None:section.append(line)
native=[]
for at,v in vectors.items():
 t=v['text']
 if not re.search(r'OP:5\.1 ',t.splitlines()[0]):continue
 m=re.search(r'\n\s+xid:\s+0x([0-9a-f]+)\.([0-9a-f]+)\.([0-9a-f]+)',t)
 assert m
 xid='.'.join(str(int(x,16)) for x in m.groups())
 if xid not in xids:continue
 meta=re.search(r'opc: (\d+\.\d+) \[objn: (\d+) objd: (\d+) tsn: (\d+)\]',t);assert meta and meta[2]==meta[3]
 seq=re.search(r'ktudb redo:.*seq: 0x([0-9a-f]+) rec: 0x([0-9a-f]+)',t);assert seq
 dba=int(re.search(r'DBA:0x([0-9a-f]+)',t)[1],16)
 native.append(dict(position=list(at),object=int(meta[2]),xid=xid,uba=[dba,int(seq[1],16),int(seq[2],16)],opcode=meta[1]))
key=lambda x:x['position']
assert len(raw)==129 and len(native)==125,(len(raw),len(native))
printed={tuple(x['position']) for x in native}
unprinted=[x for x in raw if tuple(x['position']) not in printed]
assert [(x['position'],x['object'],x['xid']) for x in unprinted]==[
 ([97,15860,156,2],79334,'5.7.664'),([97,15861,144,1],79339,'5.7.664'),
 ([97,16063,360,2],79340,'10.11.4237'),([97,16064,340,1],79345,'10.11.4237')],unprinted
covered=[x for x in raw if tuple(x['position']) in printed]
assert sorted(covered,key=key)==sorted(native,key=key)
counts=Counter(x['opcode'] for x in raw)
assert counts=={'11.1':59,'26.1':10,'13.23':32,'10.22':20,'13.54':4,'13.52':2,'13.50':2},counts
for f in pairs:
 at=tuple(map(int,f[1:5]))
 if at not in printed:
  assert f[0]=='PKPAIR' and at in [tuple(x['position']) for x in unprinted];continue
 v=vectors[at];t=v['text']
 if f[0]=='PKPAIR':
  block=int(f[11]);slot=int(f[12]);kind=f[10]
  assert int(re.search(r'indexid=0x[0-9a-f]+ block=0x([0-9a-f]+)',t)[1],16)==block
  forward=[z['text'] for a,z in vectors.items() if a[:3]==at[:3] and re.search(r'DBA:0x'+format(block,'08x')+r' OBJ:'+f[5]+r' .*OP:10\.'+('2' if kind=='INSERT' else '4')+r' ',z['text'].splitlines()[0])]
  assert len(forward)==1 and int(re.search(r'sno: (\d+)',forward[0])[1])==slot
 else:
  minor=int(f[10]);forwardAt=(at[0],)+tuple(map(int,f[11:14]));fw=vectors[forwardAt];s=fw['text']
  block,segment,chunk,length=map(int,f[14:18])
  assert int(re.search(r'bdba: 0x([0-9a-f]+)',t)[1],16)==block
  assert re.search(r'DBA:0x'+format(block,'08x')+r' OBJ:'+f[5]+r' .*OP:13\.'+str(minor-1)+r' ',s.splitlines()[0])
  if minor==54:
   assert int(re.search(r'mark chunk free: 0x([0-9a-f]+)',t)[1],16)==chunk
   m=re.search(r'Splitting chunk in PUAChunk: 0x([0-9a-f]+): Split length (\d+)',s);assert m
   assert (int(m[1],16),int(m[2]))==(chunk,length)
   assert fw['recordScn']==v['recordScn']
  else:
   assert int(re.search(r'segdba: 0x([0-9a-f]+)',t)[1],16)==segment
   m=re.search(r'\[Chunk 0\] DBA: 0x([0-9a-f]+) Len: (\d+) Below: (\d+)',t);assert m
   assert (int(m[1],16),int(m[2]),int(m[3]))==(chunk,length,1)
   m=re.search(r'\[Chunk 0\] DBA: 0x([0-9a-f]+) Len:(\d+) Xid: 0x([0-9a-f]+)\.([0-9a-f]+)\.([0-9a-f]+)',s);assert m
   assert (int(m[1],16),int(m[2]))==(chunk,length)
   assert '.'.join(str(int(x,16)) for x in m.groups()[2:])==f[6]
assert Counter(f[0] for f in pairs)=={'PKPAIR':6,'CONTROLPAIR':8}
poison=[dict(x) for x in covered];poison[0]['uba']=covered[0]['uba'][:-1]+[covered[0]['uba'][-1]+1]
assert sorted(poison,key=key)!=sorted(native,key=key)
proof=dict(originals=129,nativeComparedOriginals=125,earlySeedOriginalsNotInNativeDump=4,nativeOwnerUbaPositionOpcodeMismatch=0,
           primaryIndexPairs=6,nativeComparedPrimaryIndexPairs=4,earlySeedPrimaryIndexPairsNotInNativeDump=2,secureFileControlPairs=8,
           nativeControlPrintedFieldMismatch=0,pubSegmentAndForwardXidNotPrinted=4,ctimeOpaque=True,poisonDetected=1,
           fullLobTransactionAccepted=False,baselineApproved=False,events=0)
p=N/'control-comparison.json';assert not p.exists();p.write_text(json.dumps(proof,indent=2));print(json.dumps(proof))
