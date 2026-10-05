"""Compare JDBC source value bytes with independent SQL DUMP rulers for every frozen SYS cell."""
import hashlib,json,re
from pathlib import Path
R=Path(__file__).resolve().parent.parent;O=R/'lab/out/sys-full-baseline-research-20261006'
assert not (O/'full-row-proof.json').exists()
rows=[];cells=[];layouts={};table={}
for line in (O/'research-r4.stdout.txt').read_text().splitlines():
    q=line.split('|')
    if q[0]=='SYS_ROW':rows.append(q[1:])
    elif q[0] in ['OBJ$','TAB$','COL$'] and q[1]=='LAYOUT':layouts.setdefault(q[0],[]).append(q[2:])
    elif q[0]=='TABLE_POSITION':table[q[1]]=q[2:]
    elif q[0]=='CELL':
        assert q[9].startswith('JDBC_BYTES=');value=q[9].split('=',1)[1]
        if q[8]=='NULL':assert value=='NULL'
        else:
            m=re.fullmatch(r'Typ=(\d+) Len=(\d+): (.*)',q[8]);assert m and m[1]==q[7]
            expected=''.join(f'{int(b,16):02x}' for b in m[3].split(','))
            assert len(expected)==int(m[2])*2 and value==expected,q
        cells.append(q[1:8]+[value])
assert len(rows)==6 and len(cells)==230
assert {k:len(v) for k,v in layouts.items()}=={'OBJ$':37,'TAB$':45,'COL$':37}
assert table=={'OBJ$':['','','37',''],'TAB$':['1','2','45','1'],'COL$':['5','2','37','1']}
assert next(c for c in layouts['COL$'] if c[2]=='DEFAULT$')[:2]==['14','37']
assert next(c for c in layouts['COL$'] if c[2]=='ACDRRESCOL#')[:2]==['37','14']
proof=dict(scn=2522819,rows=rows,cell_count=len(cells),layouts=layouts,tables=table,jdbc_bytes_vs_independent_dump_differences=0,source_actions=0,historical_authority=False,production_format_implemented=False)
(O/'full-row-proof.json').write_text(json.dumps(proof,indent=2));(O/'canonical-cells.json').write_text(json.dumps(cells,indent=2));(O/'executed-full-row-comparator.py').write_bytes(Path(__file__).read_bytes())
print(json.dumps(dict(full_sys_rows=6,cells=len(cells),differences=0,history_authority=False)))
