"""Frozen BasicFile allocation/page/visible locator scope discovery, not writer authorization."""
from pathlib import Path
import hashlib,json,re,sys
R=Path(__file__).resolve().parent.parent;F=R/'lab/out/d4-lob-source-20261009/continue-r2'
assert len(sys.argv)==7 and sys.argv[1:5]==['--layout','inventory-r22','--graph','rollback-r41'] and sys.argv[5]=='--run' and re.fullmatch(r'allocation-r[1-9][0-9]*',sys.argv[6])
N=F/sys.argv[6];N.mkdir(exist_ok=False)
layout=(F/'inventory-r22/inventory.stdout').read_text();graph=(F/'rollback-r41/inventory.stdout').read_text()
allocations=[];pages=[];locators=[];retirements=[];owners={};originals={};graphs={}
for line in graph.splitlines():
 f=line.split('|')
 if f[0]=='GRAPHCLOSED':graphs[f[1]]=dict(begin=int(f[2]),end=int(f[3]))
 if f[0]=='OWNEDORIGINAL':originals.setdefault(f[6],[]).append(tuple(map(int,f[1:4])))
for record in layout.split('RECORD|')[1:]:
 h=record.splitlines()[0].split('|');position=tuple(map(int,h[:3]));scn=int(h[3]);pending=None;lastLocator=None
 for line in record.splitlines():
  f=line.split('|')
  if f[0]=='HEAPPAIR':owners[tuple(map(int,f[1:5]))]=f[6]
  if f[0]=='PAGE':pending=dict(position=list(map(int,f[1:5])),scn=scn,object=int(f[5]),lid=f[6],version=int(f[7]),page=int(f[8]),parent=int(f[9]))
  if f[0]=='VECTOR' and pending:pending['dba']=int(f[3]);pages.append(pending);pending=None
  if f[0]=='LOCATOR':lastLocator=f
  if f[0]=='LOB' and lastLocator[8]=='1':
   locators.append(dict(position=list(map(int,lastLocator[1:5])),scn=int(f[1]),owner=owners[tuple(map(int,lastLocator[1:5]))],
    storage=f[6],lid=f[7],version=int(f[9]),pages=list(map(int,f[10].split(','))) if f[10] else []))
 for vector in record.split('VECTOR|')[1:]:
  ls=vector.splitlines();v=ls[0].split('|')
  fields=[bytes.fromhex(x.split('|')[4]) for x in ls[1:] if x.startswith('FIELD|')]
  if v[1]=='10.2' and v[-1] in ['79336','79338']:
   assert len(fields)==4 and fields[0][0] in [1,2,17]
   offset=8 if fields[0][0]==2 else 16
   key=(int.from_bytes(fields[0][offset:offset+4],'little'),int.from_bytes(fields[0][offset+4:offset+6],'little'),fields[0][offset+6])
   candidates=[]
   for other in record.split('VECTOR|')[1:]:
    ol=other.splitlines();ov=ol[0].split('|')
    if ov[1]!='5.1':continue
    uf=[bytes.fromhex(x.split('|')[4]) for x in ol[1:] if x.startswith('FIELD|')]
    if (int(ov[2]),int.from_bytes(uf[0][16:18],'little'),uf[0][18])==key:candidates.append(uf)
   assert len(candidates)==1 and int.from_bytes(candidates[0][1][:4],'little')==int(v[-1])
   retirements.append(dict(position=list(position)+[int(v[0])+1],dba=int.from_bytes(fields[3][:4],'big'),
    owner='.'.join(str(int.from_bytes(candidates[0][0][p:p+n],'little')) for p,n in [(8,2),(10,2),(12,4)])))
  if v[1]!='5.1':continue
  if len(fields)!=4 or len(fields[1])!=24 or fields[1][16:18]!=bytes([13,23]) or len(fields[3])!=16:continue
  object=int.from_bytes(fields[1][:4],'little')
  if object not in [79335,79337]:continue
  parent=int.from_bytes(fields[2][:4],'little');offset=int.from_bytes(fields[3][12:16],'little')
  owner='.'.join(str(int.from_bytes(fields[0][p:p+n],'little')) for p,n in [(8,2),(10,2),(12,4)])
  allocations.append(dict(position=list(position)+[int(v[0])+1],scn=scn,object=object,parent=parent,dba=parent+offset,owner=owner))
relations=[]
for page in pages:
 candidates=[a for a in allocations if all(a[k]==page[k] for k in ['object','parent','dba'])]
 assert len(candidates)==1,(page,candidates)
 allocation=candidates[0];window=graphs[allocation['owner']]
 assert window['begin']<=page['scn']<=window['end'] and window['begin']<=allocation['scn']<=window['end']
 visible=[x for x in locators if x['storage']=='BASICFILE' and x['owner']==allocation['owner'] and x['lid']==page['lid']
  and x['version']==page['version']+1 and page['dba'] in x['pages'] and tuple(x['position'])>tuple(page['position'])]
 retired=[x for x in retirements if x['owner']==allocation['owner'] and x['dba']==page['dba'] and tuple(x['position'])>tuple(page['position'])]
 assert len(visible)<=1 and (visible or len(retired)==1),(page,visible,retired)
 relations.append(dict(page=page,allocation=allocation,visibleLocator=visible[0] if visible else None,retirementWithoutVisibleLocator=retired[0] if not visible else None,
  pageBeforeFirstUndo=tuple(page['position'][:3])<min(originals[allocation['owner']])))
assert len(relations)==len(pages)==len(allocations)==18
assert sum(x['visibleLocator'] is not None for x in relations)==16 and sum(x['retirementWithoutVisibleLocator'] is not None for x in relations)==2
proof=dict(pages=18,uniqueAllocationRelations=18,ownedVisibleLocatorRelations=16,retiredWithoutVisibleLocator=2,unequalPageAllocationRecordScn=sum(x['page']['scn']!=x['allocation']['scn'] for x in relations),
 pageBeforeFirstUndo=sum(x['pageBeforeFirstUndo'] for x in relations),physicalWriterAuthorized=False,transactionAccepted=False,baselineApproved=False,events=0,
 inputSha256=hashlib.sha256((F/'inventory-r22/inventory.stdout').read_bytes()).hexdigest(),
 graphSha256=hashlib.sha256((F/'rollback-r41/inventory.stdout').read_bytes()).hexdigest(),relations=relations)
(N/'scope-proof.json').write_text(json.dumps(proof,indent=2));print(json.dumps({k:v for k,v in proof.items() if k!='relations'}))
