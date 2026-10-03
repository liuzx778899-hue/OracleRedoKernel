"""Independent diagnostic comparator, not an event decoder."""
import json
import re
import sys
from pathlib import Path

dump=Path(sys.argv[1]).read_text(encoding='utf-8')
rows=[line.split('|') for line in Path(sys.argv[2]).read_text(encoding='utf-8-sig').splitlines()]
actual={tuple(map(int,row[:3])):row for row in rows}
first_block=int(sys.argv[3])
errors=[]
counts={4:0,6:0,11:0}
for record in re.split(r'(?=REDO RECORD - Thread:)',dump):
    rba=re.search(r'RBA: 0x[0-9a-f]+\.([0-9a-f]+)\.([0-9a-f]+)',record)
    if not rba or int(rba[1],16)<first_block or 'CON_UID: 677737149' not in record.splitlines()[0]:
        continue
    scn=re.search(r'^SCN: 0x([0-9a-f]+)',record,re.M)
    if not scn:
        raise RuntimeError('missing record SCN')
    for change in re.split(r'(?=CHANGE #\d+)',record)[1:]:
        header=re.search(r'CHANGE #(\d+) CON_ID:3 [^\n]*?DBA:0x([0-9a-f]+)[^\n]*?OP:5\.(4|6|11)\b',change)
        if not header:
            continue
        key=(int(rba[1],16),int(rba[2],16),int(header[1]))
        minor=int(header[3])
        expected=[minor,int(scn[1],16),int(header[2],16)]
        if minor==4:
            h=re.search(r'ktucm redo: slt: 0x([0-9a-f]+) sqn: 0x([0-9a-f]+) srt: 0 sta: 9 flg: 0x([0-9a-f]+)',change)
            if not h:
                raise RuntimeError('unrecognized dump end header')
            expected += [int(h[i],16) for i in (1,2,3)]
        else:
            h=re.search(r'ktub[lu] redo: slt:\s*(\d+) wrp:\s*(\d+) flg: 0x([0-9a-f]+) prev dba:\s*0x([0-9a-f]+) rci:\s*(\d+) opc:\s*(\d+)\.(\d+) \[objn:\s*(\d+) objd:\s*(\d+) tsn:\s*(\d+)\]',change)
            if not h or not re.search(r'\[User undo done\s*\]\s*Yes',change):
                raise RuntimeError('unrecognized dump compensation header')
            expected += [int(h[i]) for i in (8,9,10,6,7,1,5)] + [int(h[3],16),int(h[2])]
            expected += [bool(re.search(r'Begin trans\b',change)),int(h[4],16)]
        row=actual.get(key)
        decoded=None if row is None else [x=='true' if x in ('true','false') else int(x) for x in row[3:]]
        if expected!=decoded:
            errors.append({'rba_vector':key,'expected':expected,'actual':decoded})
        counts[minor]+=1
if sum(counts.values())==0 or len(actual)!=sum(counts.values()) or len(rows)!=len(actual):
    errors.append({'reason':'ZERO_DUPLICATE_OR_UNMATCHED_MARKERS','actual':len(actual),'compared':counts})
print(json.dumps({'markers_compared':counts,'mismatches':len(errors),'errors':errors,
                  'scope':'bounded 19.3 PDB marker fields; no segment binding or event cancellation acceptance'},indent=2))
sys.exit(bool(errors))
