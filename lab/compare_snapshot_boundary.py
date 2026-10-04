"""Independent native begin/end record SCNs for the source experiment's full XID."""
import json,re,sys
from pathlib import Path
trace,manifest,actual=map(Path,sys.argv[1:4]);m=json.loads(manifest.read_text());xid=tuple(map(int,m['xid'].split('.')));begins=[];ends=[];segment=None
records=[r for r in re.split(r'(?=REDO RECORD - Thread:)',trace.read_text()) if r.startswith('REDO RECORD - Thread:') and 'CON_UID: 677737149' in r.splitlines()[0]]
for record in records:
 scn=int(re.search(r'^SCN: 0x([0-9a-f]+)',record,re.M)[1],16)
 for c in re.split(r'(?=CHANGE #\d+)',record)[1:]:
  if not re.search(r'^CHANGE #\d+ CON_ID:3 [^\n]*OP:5\.2\b',c):continue
  own=any(tuple(int(v,16) for v in match)==xid for match in re.findall(r'\bxid:\s+0x([0-9a-f]+)\.([0-9a-f]+)\.([0-9a-f]+)',record))
  slot,seq=re.search(r'ktudh redo: slt: 0x([0-9a-f]+) sqn: 0x([0-9a-f]+)',c).groups()
  if own and (int(slot,16),int(seq,16))==xid[1:]:
   begins.append(scn);segment=re.search(r'DBA:0x([0-9a-f]+)',c)[1]
for record in records:
 scn=int(re.search(r'^SCN: 0x([0-9a-f]+)',record,re.M)[1],16)
 for c in re.split(r'(?=CHANGE #\d+)',record)[1:]:
  if not re.search(r'^CHANGE #\d+ CON_ID:3 [^\n]*OP:5\.4\b',c) or re.search(r'DBA:0x([0-9a-f]+)',c)[1]!=segment:continue
  slot,seq=re.search(r'ktucm redo: slt: 0x([0-9a-f]+) sqn: 0x([0-9a-f]+)',c).groups()
  if (int(slot,16),int(seq,16))==xid[1:]:ends.append(scn)
if len(begins)!=1 or len(ends)!=1:raise RuntimeError('NATIVE_BEGIN_END_NOT_UNIQUE')
expected='BOUNDARY|'+m['xid']+'|'+str(begins[0])+'|'+str(m['snapshot_scn'])+'|'+str(ends[0])+'|false'
errors=[]
if actual.read_text().strip()!=expected:errors.append({'expected':expected,'actual':actual.read_text().strip()})
if not begins[0]<=m['snapshot_scn']<ends[0]:errors.append('NATIVE_TRANSACTION_NOT_ACROSS_SNAPSHOT')
print(json.dumps({'begin_record_scn':begins[0],'snapshot_scn':m['snapshot_scn'],'end_record_scn':ends[0],'mismatches':len(errors),'errors':errors,'scope':'one real initial boundary refusal; not general initial transaction recovery'},indent=2));sys.exit(bool(errors))
