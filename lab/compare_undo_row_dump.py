"""Independent comparison of 5.1 KDO inverse patches, not complete before images."""
import json
import re
import sys
from pathlib import Path

dump=Path(sys.argv[1]).read_text(encoding='utf-8')
rows=[line.split('|') for line in Path(sys.argv[2]).read_text(encoding='utf-8-sig').splitlines()]
actual={tuple(map(int,r[:3])):r for r in rows}
errors=[]
compared=0
columns=0
for record in re.split(r'(?=REDO RECORD - Thread:)',dump):
    rba=re.search(r'RBA: 0x[0-9a-f]+\.([0-9a-f]+)\.([0-9a-f]+)',record)
    if not rba:
        continue
    for change in re.split(r'(?=CHANGE #\d+)',record)[1:]:
        h=re.search(r'CHANGE #(\d+) CON_ID:3 [^\n]*OP:5\.1\b',change)
        if not h or not re.search(r'objd:\s*73024\b',change):
            continue
        xid=re.search(r'xid:\s*0x([0-9a-f]+)\.([0-9a-f]+)\.([0-9a-f]+)',change)
        kdo=re.search(r'KDO Op code: (IRP|URP|DRP)',change)
        dba=re.search(r'bdba: 0x([0-9a-f]+)',change)
        slot=re.search(r'tabn:\s*0 slot:\s*(\d+)',change)
        chain=re.search(r'ktub[lu] redo: slt:\s*\d+ wrp:\s*(\d+) flg: 0x[0-9a-f]+ prev dba:\s*0x([0-9a-f]+) rci:\s*(\d+)',change)
        if not all((xid,kdo,dba,slot,chain)):
            raise RuntimeError('missing inverse diagnostic fields')
        rawcols={int(m[1]):('VALUE',''.join(m[3].split()).lower()) for m in re.finditer(r'col\s+(\d+):\s*\[\s*(\d+)\]\s*([0-9a-fA-F ]+)',change)}
        for m in re.finditer(r'col\s+(\d+):\s*\*NULL\*',change): rawcols[int(m[1])]=('NULL','')
        key=(int(rba[1],16),int(rba[2],16),int(h[1]))
        row=actual.get(key)
        kind={'IRP':'INSERT','URP':'UPDATE','DRP':'DELETE'}[kdo[1]]
        expected=[rawcols.get(i,('ABSENT','')) for i in range(4)]
        decoded=[tuple(x.split(':',1)) for x in row[9].split(',')] if row else None
        identity=[int(xid[i],16) for i in (1,2,3)]
        if row is None or row[3]!=kind or list(map(int,row[4:6]))!=[int(dba[1],16),int(slot[1])] or list(map(int,row[6:9]))!=identity or decoded!=expected or list(map(int,row[10:13]))!=[int(chain[2],16),int(chain[3]),int(chain[1])]:
            errors.append({'rba_vector':key,'kind':kind,'identity':identity,'columns':expected,'actual':row})
        compared+=1; columns+=len(rawcols)
if compared==0 or len(actual)!=compared or len(actual)!=len(rows):
    errors.append({'reason':'ZERO_DUPLICATE_OR_UNMATCHED_INVERSE','actual':len(actual),'compared':compared})
print(json.dumps({'inverse_patches_compared':compared,'present_columns_compared':columns,
                  'mismatches':len(errors),'errors':errors,'scope':'5.1 physical inverse patches, not complete before rows or event cancellation'},indent=2))
sys.exit(bool(errors))
