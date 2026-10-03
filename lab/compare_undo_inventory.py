"""Native complete 5.1 identity/location/reference parity; no transaction semantic certification."""
import json,re,sys
from pathlib import Path

x=tuple(map(int,sys.argv[3].split('.')));sequence=int(sys.argv[4]);expected={}
for record in re.split(r'(?=REDO RECORD - Thread:)',Path(sys.argv[1]).read_text(encoding='utf-8')):
    if not record.startswith('REDO RECORD - Thread:') or 'CON_UID: 677737149' not in record.splitlines()[0]:continue
    rba=re.search(r'RBA: 0x[0-9a-f]+\.([0-9a-f]+)\.([0-9a-f]+)',record)
    for change in re.split(r'(?=CHANGE #\d+)',record)[1:]:
        h=re.search(r'CHANGE #(\d+) CON_ID:3 [^\n]*DBA:0x([0-9a-f]+)[^\n]*OP:5\.1\b',change)
        xid=re.search(r'xid:\s*0x([0-9a-f]+)\.([0-9a-f]+)\.([0-9a-f]+)',change)
        if not h or not xid or tuple(int(xid[i],16) for i in (1,2,3))!=x:continue
        b=re.search(r'ktudb redo:[^\n]*seq: 0x([0-9a-f]+) rec: 0x([0-9a-f]+)',change)
        link=re.search(r'prev dba:\s*0x([0-9a-f]+) rci:\s*(\d+) opc: (\d+\.\d+)',change)
        key=(sequence,int(rba[1],16),int(rba[2],16),int(h[1]))
        if key in expected:raise RuntimeError('duplicate native position')
        expected[key]=[':'.join(str(int(i,16)) for i in (h[2],b[1],b[2])),str(int(link[1],16)),link[2],link[3]]
actual={}
for line in Path(sys.argv[2]).read_text(encoding='utf-8-sig').splitlines():
    p=line.split('|')
    if p[0]!='NODE' or len(p)!=9:raise RuntimeError('unexpected inventory output')
    key=tuple(map(int,p[1:5]))
    if key in actual:raise RuntimeError('duplicate parser position')
    actual[key]=p[5:]
errors=[{'position':k,'expected':expected.get(k),'actual':actual.get(k)} for k in sorted(expected.keys()|actual.keys()) if expected.get(k)!=actual.get(k)]
if not expected:raise RuntimeError('no independent native coverage')
print(json.dumps({'native_nodes':len(expected),'parser_nodes':len(actual),'mismatches':len(errors),'errors':errors,'scope':'selected 5.1 full raw input location/UBA/reference coverage; no full cross-block predecessor or transaction acceptance'},indent=2))
sys.exit(bool(errors))
