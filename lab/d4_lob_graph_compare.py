"""Independent native full predecessor/LIFO cancellation and end comparison for frozen LOB graphs."""
from pathlib import Path
import hashlib,json,re,sys
R=Path(__file__).resolve().parent.parent;F=R/'lab/out/d4-lob-source-20261009/continue-r2'
assert len(sys.argv)==3 and sys.argv[1]=='--run' and re.fullmatch(r'rollback-r[1-9][0-9]*',sys.argv[2]);N=F/sys.argv[2]
assert hashlib.sha256((F/'97.trc').read_bytes()).hexdigest()=='807f987d7d927f29bf58730b81908444a5a6d65c8c5a5b6edb49b061079e4472'
raw=[]
uba=lambda s:list(map(int,re.fullmatch(r'Uba\[dba=(\d+), sequence=(\d+), record=(\d+)\]',s).groups())) if s!='null' else None
for line in (N/'inventory.stdout').read_text().splitlines():
 f=line.split('|')
 if f[0]=='GRAPHCLOSED':raw.append(dict(xid=f[1],begin=int(f[2]),end=int(f[3]),rollback=f[4]=='true',originals=int(f[5]),live=int(f[6]),
  cancelled=int(f[7]),tail=uba(f[8]),head=uba(f[9]),advance=int(f[10]),headUpdates=int(f[11]),segment=int(f[12]),endPosition=list(map(int,f[13:17])),flags=int(f[17])))
assert len(raw)==19 and len({x['xid'] for x in raw})==19
targets={x['xid'] for x in raw};entries=[];markers=[];ends={};section=[];position=None;current=None;recordScn=None
def finish():
 if current is None:return
 t='\n'.join(section);opcode=re.search(r'OP:(\d+\.\d+) ',section[0])[1];dba=int(re.search(r'DBA:0x([0-9a-f]+)',section[0])[1],16)
 if opcode=='5.4':
  m=re.search(r'ktucm redo: slt: 0x([0-9a-f]+) sqn: 0x([0-9a-f]+).*flg: 0x([0-9a-f]+)',t);assert m
  tail=re.search(r'ktucf redo: uba: 0x([0-9a-f]+)\.([0-9a-f]+)\.([0-9a-f]+)',t)
  ends[current]=dict(scn=recordScn,segment=dba,slot=int(m[1],16),sequence=int(m[2],16),flags=int(m[3],16),tail=[int(x,16) for x in tail.groups()] if tail else None)
 if opcode not in ['5.1','5.6','5.11']:return
 m=re.search(r'slt: (\d+) wrp: (\d+) flg: 0x([0-9a-f]+) prev dba:\s+0x([0-9a-f]+) rci: (\d+) opc: (\d+\.\d+) \[objn: (\d+) objd: (\d+) tsn: (\d+)\]',t)
 if not m:return
 fields=[int(m[1]),int(m[2]),int(m[4],16),int(m[5]),m[6],int(m[7]),int(m[8]),int(m[9])]
 if opcode=='5.1':
  xid=re.search(r'\n\s+xid:\s+0x([0-9a-f]+)\.([0-9a-f]+)\.([0-9a-f]+)',t);assert xid
  name='.'.join(str(int(x,16)) for x in xid.groups())
  if name not in targets:return
  seq=re.search(r'ktudb redo:.*seq: 0x([0-9a-f]+) rec: 0x([0-9a-f]+)',t);assert seq
  entries.append(dict(position=list(current),xid=name,scn=recordScn,fields=fields,begin=bool(int(m[3],16)&8),uba=[dba,int(seq[1],16),int(seq[2],16)]))
 else:markers.append(dict(position=list(current),fields=fields))
for line in (F/'97.trc').read_text().splitlines()+['END']:
 if line.startswith('REDO RECORD -') or 'CHANGE #' in line or line=='END':finish();current=None;section=[]
 if line.startswith('REDO RECORD -'):
  m=re.search(r'RBA: 0x([0-9a-f]+)\.([0-9a-f]+)\.([0-9a-f]+)',line);assert m;position=tuple(int(x,16) for x in m.groups());recordScn=None
 elif line.startswith('SCN:') and recordScn is None:recordScn=int(re.search(r'SCN: 0x([0-9a-f]+)',line)[1],16)
 m=re.search(r'CHANGE #(\d+) CON_ID:3 ',line)
 if m:current=position+(int(m[1]),)
 if current is not None:section.append(line)
assert len(entries)==125
compared=0;tailPrinted=0;nativeHeads={}
for graph in raw:
 own=[x for x in entries if x['xid']==graph['xid']]
 if graph['xid'] in ['5.7.664','10.11.4237']:
  assert not own and not graph['rollback'] and graph['originals']==graph['live']==2 and graph['cancelled']==0;continue
 assert len(own)==graph['originals'] and own[0]['begin'] and own[0]['scn']==graph['begin']
 stack=[];allocated=None;cancelled=0
 selected=[]
 for marker in markers:
  candidates=[x for x in own if x['fields']==marker['fields'] and x['position']<marker['position']]
  if candidates:
   assert len(candidates)==1;selected.append(dict(marker=marker,original=candidates[0]))
 actions=[(x['position'],'A',x) for x in own]+[(x['marker']['position'],'C',x['original']) for x in selected]
 for _,kind,x in sorted(actions):
  if kind=='A':
   if x['begin']:assert not stack
   else:
    assert stack
    previous=x['fields'][2];rci=x['fields'][3]
    assert ([x['uba'][0],x['uba'][1],rci]==stack[-1]['uba']) if previous==0 else (rci==0 and previous==stack[-1]['uba'][0])
   stack.append(x);allocated=x['uba']
  else:
   assert stack and stack[-1]==x;stack.pop();cancelled+=1
 end=ends[tuple(graph['endPosition'])];usn,slot,sequence=map(int,graph['xid'].split('.'))
 assert (end['scn'],end['segment'],end['slot'],end['sequence'],end['flags'])==(graph['end'],graph['segment'],slot,sequence,graph['flags'])
 assert (end['flags'] in [4,20])==graph['rollback']
 assert (len(stack),cancelled,allocated,stack[-1]['uba'] if stack else None)==(graph['live'],graph['cancelled'],graph['tail'],graph['head'])
 nativeHeads[graph['xid']]=stack[-1]['uba'] if stack else None
 if end['tail'] is not None:assert end['tail']==allocated;tailPrinted+=1
 if graph['rollback']:assert not stack
 compared+=1
assert compared==17 and sum(x['cancelled'] for x in raw)==17 and sum(x['rollback'] for x in raw)==3
poison=dict(raw[-1]);poison['head']=poison['tail'];assert poison['head']!=nativeHeads[poison['xid']]
proof=dict(rawGraphs=19,nativeComparedGraphs=17,earlySeedGraphsNotInNativeDump=2,rawCommits=16,rawRollbacks=3,originals=129,
           nativeComparedOriginals=125,compensated=17,rollbackLiveNodes=0,nativeGraphMismatch=0,nativeAllocationTailsPrinted=tailPrinted,
           undoBlockAdvances=sum(x['advance'] for x in raw),headUpdates=sum(x['headUpdates'] for x in raw),poisonDetected=1,
           fullLobTransactionAccepted=False,baselineApproved=False,events=0)
p=N/'graph-comparison.json';assert not p.exists();p.write_text(json.dumps(proof,indent=2));print(json.dumps(proof))
