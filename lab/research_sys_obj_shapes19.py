"""Inspect frozen OBJ$ physical row profile after strict ordinary heap rejection."""
import json,re
from pathlib import Path
R=Path(__file__).resolve().parent.parent;O=R/'lab/out/sys-full-history-research-20261006';H=R/'lab/out/v2-historical-product-20261006'
records=json.loads((R/'lab/out/sys-cluster-row-research-20261006/raw-records.json').read_text());selected=[]
for r in records:
    for v in r['vectors']:
        if v['opcode']!='5.1' or len(v['fields'])<4:continue
        m=bytes.fromhex(v['fields'][1]);logical=int.from_bytes(m[:4],'little')
        if logical!=18:continue
        selected.append(dict(position=r['position'],xid=r['xid'],scn=r['scn'],undo=v,forward=[f for f in r['vectors'] if f['opcode'].startswith('11.')]))
native=[]
for rec in re.split(r'(?=REDO RECORD - Thread:)',(H/'dump.trc').read_text()):
    m=re.search(r'RBA: 0x([0-9a-f]+)\.([0-9a-f]+)\.([0-9a-f]+)',rec)
    if m and any([int(x,16) for x in m.groups()]==s['position'] for s in selected):native.append(rec)
(O/'obj-row-shapes.json').write_text(json.dumps(selected,indent=2));(O/'native-obj-row-records.txt').write_text(''.join(native));(O/'executed-obj-shape-driver.py').write_bytes(Path(__file__).read_bytes())
print(json.dumps([dict(position=s['position'],xid=s['xid'],undo_kdo=s['undo']['fields'][3],forward=[dict(op=f['opcode'],ktb=f['fields'][0],kdo=f['fields'][1],lengths=[len(bytes.fromhex(b)) for b in f['fields']]) for f in s['forward']]) for s in selected]))
