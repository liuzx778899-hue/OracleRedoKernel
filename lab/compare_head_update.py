"""Independent native head-control/new-undo identity parity; never produces events."""
import json,re,sys
from pathlib import Path

expected=[]
for record in re.split(r'(?=REDO RECORD - Thread:)',Path(sys.argv[1]).read_text(encoding='utf-8')):
    if not record.startswith('REDO RECORD - Thread:') or 'CON_UID: 677737149' not in record.splitlines()[0]:continue
    rba=re.search(r'RBA: 0x[0-9a-f]+\.([0-9a-f]+)\.([0-9a-f]+)',record)
    changes=re.split(r'(?=CHANGE #\d+)',record)[1:]
    undos={}
    for c in changes:
        h=re.search(r'CHANGE #\d+ CON_ID:3 [^\n]*DBA:0x([0-9a-f]+)[^\n]*OP:5\.1\b',c)
        if not h or not re.search(r'xid:\s*0x000a\.002\.0000048b\b',c):continue
        u=re.search(r'ktudb redo:[^\n]*seq: 0x([0-9a-f]+) rec: 0x([0-9a-f]+)',c)
        link=re.search(r'prev dba:\s*0x([0-9a-f]+) rci:\s*(\d+)',c)
        address=tuple(int(i,16) for i in (h[1],u[1],u[2]))
        if address in undos:raise RuntimeError('native head original UBA ambiguous')
        undos[address]=(str(int(link[1],16)),link[2])
    for c in changes:
        h=re.search(r'CHANGE #(\d+) CON_ID:3 [^\n]*DBA:0x([0-9a-f]+)[^\n]*OP:5\.2\b',c)
        control=re.search(r'ktudh redo: slt: 0x([0-9a-f]+) sqn: 0x00000000 flg: 0x0002',c)
        if not h or not control:continue
        u=re.search(r'uba: 0x([0-9a-f]+)\.([0-9a-f]+)\.([0-9a-f]+)',c)
        address=tuple(int(u[i],16) for i in (1,2,3))
        if address not in undos:continue
        expected.append('|'.join(['HEAD',str(int(rba[1],16)),str(int(rba[2],16)),h[1],str(int(h[2],16)),str(int(control[1],16)),':'.join(map(str,address)),'10.2.1163',*undos[address]]))
actual=Path(sys.argv[2]).read_text(encoding='utf-8-sig').splitlines()
errors=[]
if expected!=actual or len(expected)!=1:errors.append({'reason':'HEAD_UPDATE_IDENTITY_OR_COVERAGE_DIFF','expected':expected,'actual':actual})
print(json.dumps({'head_updates_compared':len(expected),'mismatches':len(errors),'errors':errors,'scope':'flags=2 allocated-block head control and exact bound undo/active identity; complete predecessor graph unaccepted'},indent=2))
sys.exit(bool(errors))
