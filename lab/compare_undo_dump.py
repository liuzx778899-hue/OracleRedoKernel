import json
import re
import sys
from pathlib import Path

dump=Path(sys.argv[1]).read_text(encoding='utf-8')
actual={tuple(map(int,r[:3])):r for r in (line.split('|') for line in Path(sys.argv[2]).read_text(encoding='utf-8-sig').splitlines())}
object_id=int(sys.argv[3])
errors=[]
compared=0
for record in re.split(r'(?=REDO RECORD - Thread:)',dump):
    rba=re.search(r'RBA: 0x[0-9a-f]+\.([0-9a-f]+)\.([0-9a-f]+)',record)
    if not rba:
        continue
    for change in re.split(r'(?=CHANGE #\d+)',record)[1:]:
        header=re.search(r'CHANGE #(\d+)[^\n]*?DBA:0x([0-9a-f]+)[^\n]*?OP:5\.1\b',change)
        obj=re.search(r'\[objn:\s*(\d+) objd:\s*(\d+) tsn:\s*(\d+)\]',change)
        if not header or not obj or int(obj[2])!=object_id:
            continue
        xid=re.search(r'xid:\s*0x([0-9a-f]+)\.([0-9a-f]+)\.([0-9a-f]+)',change)
        undo=re.search(r'ktudb redo:[^\n]*?seq:\s*0x([0-9a-f]+) rec:\s*0x([0-9a-f]+)',change)
        if not xid or not undo:
            raise RuntimeError('missing dump identity')
        key=(int(rba[1],16),int(rba[2],16),int(header[1]))
        expected=[int(xid[i],16) for i in (1,2,3)]+[int(header[2],16),int(undo[1],16),int(undo[2],16)]+[int(obj[i]) for i in (1,2,3)]
        row=actual.get(key)
        begin=bool(re.search(r'Begin trans\b',change))
        if row is None or list(map(int,row[3:12]))!=expected or (row[12]=='true')!=begin:
            errors.append({'rba_vector':key,'expected':expected,'begin':begin,'actual':row})
        compared+=1
if compared==0 or len(actual)!=compared:
    errors.append({'reason':'ZERO_OR_UNMATCHED_UNDO','actual':len(actual),'compared':compared})
print(json.dumps({'undo_headers_compared':compared,'mismatches':len(errors),'errors':errors,'scope':'5.1 identity, not committed transaction acceptance'},indent=2))
sys.exit(bool(errors))
