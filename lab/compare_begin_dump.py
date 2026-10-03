"""Independent selected-object 5.2/5.1 begin comparator; not a CDC transaction engine."""
import json
import re
import sys
from pathlib import Path

dump=Path(sys.argv[1]).read_text(encoding='utf-8')
rows=[line.split('|') for line in Path(sys.argv[2]).read_text(encoding='utf-8-sig').splitlines()]
actual={tuple(map(int,r[:3])):list(map(int,r[3:])) for r in rows}
errors=[]
compared=0
for record in re.split(r'(?=REDO RECORD - Thread:)',dump):
    rba=re.search(r'RBA: 0x[0-9a-f]+\.([0-9a-f]+)\.([0-9a-f]+)',record)
    if not rba or int(rba[1],16)<int(sys.argv[3]) or 'CON_UID: 677737149' not in record.splitlines()[0]:
        continue
    changes=re.split(r'(?=CHANGE #\d+)',record)[1:]
    for change in changes:
        header=re.search(r'CHANGE #(\d+) CON_ID:3 [^\n]*?DBA:0x([0-9a-f]+)[^\n]*?OP:5\.2\b',change)
        if not header:
            continue
        start=re.search(r'ktudh redo: slt: 0x([0-9a-f]+) sqn: 0x([0-9a-f]+) flg: 0x([0-9a-f]+)',change)
        uba=re.search(r'uba: 0x([0-9a-f]+)\.([0-9a-f]+)\.([0-9a-f]+)',change)
        if not start or not uba:
            raise RuntimeError('missing dump start fields')
        matches=[]
        for undo in changes:
            uh=re.search(r'CHANGE #\d+ CON_ID:3 [^\n]*?DBA:0x([0-9a-f]+)[^\n]*?OP:5\.1\b',undo)
            rec=re.search(r'ktudb redo:[^\n]*?seq: 0x([0-9a-f]+) rec: 0x([0-9a-f]+)',undo)
            if uh and rec and [int(uh[1],16),int(rec[1],16),int(rec[2],16)]==[int(uba[i],16) for i in (1,2,3)]:
                matches.append(undo)
        if not any(re.search(r'objd:\s*73024\b',m) for m in matches):
            continue
        if len(matches)!=1:
            raise RuntimeError('ambiguous diagnostic begin')
        xid=re.search(r'xid:\s*0x([0-9a-f]+)\.([0-9a-f]+)\.([0-9a-f]+)',matches[0])
        uid=re.search(r'pdbid:(\d+)',change)
        if not xid or not uid or not re.search(r'Begin trans\b',matches[0]):
            raise RuntimeError('missing diagnostic begin identity')
        expected=[int(header[2],16),int(start[1],16),int(start[2],16)]
        expected += [int(uba[i],16) for i in (1,2,3)] + [int(start[3],16),int(uid[1]),int(xid[1],16),int(xid[3],16)]
        key=(int(rba[1],16),int(rba[2],16),int(header[1]))
        if actual.get(key)!=expected or int(xid[2],16)!=int(start[1],16) or int(xid[3],16)!=int(start[2],16):
            errors.append({'rba_vector':key,'expected':expected,'actual':actual.get(key)})
        compared+=1
if compared==0 or len(actual)!=compared or len(rows)!=len(actual):
    errors.append({'reason':'ZERO_DUPLICATE_OR_UNMATCHED_BEGIN','actual':len(actual),'compared':compared})
print(json.dumps({'begins_compared':compared,'mismatches':len(errors),'errors':errors,
                  'scope':'selected 73024 5.2 exact UBA / same-record 5.1 begin; no end or compensation chain acceptance'},indent=2))
sys.exit(bool(errors))
