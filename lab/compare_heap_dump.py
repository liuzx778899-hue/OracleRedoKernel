"""Independent selected physical row-patch comparison; no commit/rollback acceptance."""
import json
import re
import sys
from pathlib import Path

dump=Path(sys.argv[1]).read_text(encoding='utf-8')
actual={tuple(map(int,r[:3])):r for r in (line.split('|') for line in Path(sys.argv[2]).read_text(encoding='utf-8-sig').splitlines())}
dba=int(sys.argv[3],0)
compared=0
fields=0
errors=[]
for record in re.split(r'(?=REDO RECORD - Thread:)',dump):
    rba=re.search(r'RBA: 0x[0-9a-f]+\.([0-9a-f]+)\.([0-9a-f]+)',record)
    if not rba:
        continue
    for change in re.split(r'(?=CHANGE #\d+)',record)[1:]:
        header=re.search(r'CHANGE #(\d+)[^\n]*?DBA:0x([0-9a-f]+)[^\n]*?OP:11\.([235])\b',change)
        if not header or int(header[2],16)!=dba:
            continue
        key=(int(rba[1],16),int(rba[2],16),int(header[1]))
        kind={'2':'INSERT','3':'DELETE','5':'UPDATE'}[header[3]]
        slot=re.search(r'tabn:\s*0 slot:\s*(\d+)',change)
        if not slot:
            raise RuntimeError('missing diagnostic slot at '+str(key))
        rawcols={int(m[1]):('VALUE', ''.join(m[3].split()).lower()) for m in re.finditer(r'col\s+(\d+):\s*\[\s*(\d+)\]\s*([0-9a-fA-F ]+)',change)}
        for m in re.finditer(r'col\s+(\d+):\s*\*NULL\*',change):
            rawcols[int(m[1])]=('NULL','')
        row=actual.get(key)
        expected=[rawcols.get(i,('ABSENT','')) for i in range(4)]
        values=[tuple(x.split(':',1)) for x in row[8].split(',')] if row else None
        if row is None or row[3]!=kind or int(row[4])!=int(slot[1]) or values!=expected:
            errors.append({'rba_vector':key,'kind':kind,'slot':int(slot[1]),'expected':expected,'actual':row})
        compared+=1
        fields+=len(rawcols)
if compared==0 or len(actual)!=compared:
    errors.append({'reason':'ZERO_OR_UNMATCHED_PATCHES','actual':len(actual),'compared':compared})
report={'patches_compared':compared,'present_columns_compared':fields,'mismatches':len(errors),'errors':errors,
        'scope':'raw physical patches; absent columns, rollback and commit remain separate gates'}
print(json.dumps(report,indent=2))
sys.exit(bool(errors))
