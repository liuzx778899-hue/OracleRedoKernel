"""Compare two preserved full member snapshots without normalizing their bytes."""
from pathlib import Path
import sys,json,hashlib,struct
a,b=map(Path,sys.argv[1:]);result=dict(paths=[str(a),str(b)],fileHeader=[],redoHeader=[],usedData=[],unusedTail=0)
with a.open('rb') as x,b.open('rb') as y:
 x.seek(512);h=x.read(512);nab=struct.unpack_from('<I',h,156)[0];x.seek(0)
 hx=hashlib.sha256();hy=hashlib.sha256();index=0
 while True:
  p=x.read(512);q=y.read(512)
  if not p and not q:break
  if 2<=index<nab:hx.update(p);hy.update(q)
  if p!=q:
   if index<2:result['fileHeader' if index==0 else 'redoHeader']=[i for i,(u,v) in enumerate(zip(p,q)) if u!=v]
   elif index<nab:result['usedData'].append(index)
   else:result['unusedTail']+=1
  index+=1
result.update(blocks=nab,usedPayloadSha256=[hx.hexdigest(),hy.hexdigest()]);print(json.dumps(result,indent=2))
