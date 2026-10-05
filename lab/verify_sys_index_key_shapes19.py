"""Independent native comparison of frozen composite key/rowid research. Does not issue index authority."""
import json,re,sys,traceback
from pathlib import Path
R=Path(__file__).resolve().parent.parent;H=R/'lab/out/v2-historical-product-20261006';S=R/'lab/out/sys-cluster-row-research-20261006';O=R/'lab/out/sys-cluster-pair-20261006'
assert not (O/'native-index-key-proof.json').exists();(O/'executed-index-key-driver-r2.py').write_bytes(Path(__file__).read_bytes())
def failure(kind,value,tb):
    (O/'index-key-comparator-r2.failure.txt').write_text(''.join(traceback.format_exception(kind,value,tb)));sys.__excepthook__(kind,value,tb)
sys.excepthook=failure
native={}
for rec in re.split(r'(?=REDO RECORD - Thread:)',(H/'dump.trc').read_text()):
    p=re.search(r'RBA: 0x([0-9a-f]+)\.([0-9a-f]+)\.([0-9a-f]+)',rec)
    if p:native[tuple(int(x,16) for x in p.groups())]=rec
records=json.loads((S/'raw-records.json').read_text());keys=json.loads((O/'parent-key-research.json').read_text())['keys'];verified=[]
def blob(c,label):
    m=re.search(label+r'(?:/bitmap)?\s*:?\s*\((\d+)\)\s*:\s*',c);assert m,(label,c[:1000])
    return ''.join(re.findall(r'(?<!\S)([0-9a-f]{2})(?!\S)',c[m.end():])[:int(m[1])])
for key in keys:
    pos=key['position'];rec=next(r for r in records if r['position']==pos[:3]);v=next(v for v in rec['vectors'] if v['index']==pos[3]);n=native[tuple(pos[:3])]
    changes={int(re.search(r'^CHANGE #(\d+)',c)[1]):c for c in re.split(r'(?=CHANGE #\d+)',n)[1:]};inverse=changes[pos[3]]
    assert blob(inverse,'key')==v['fields'][4]
    if key['inline_rowid']:assert key['components'][2]=='004089a40033'
    elif len(v['fields'])==6:assert blob(inverse,'keydata')==v['fields'][5]=='004089a40033'
    else:
        f=next(f for f in rec['vectors'] if f['opcode']=='10.2');assert blob(changes[f['index']],'keydata')==f['fields'][3]=='004089a40033'
    verified.append(dict(position=pos,index=key['index'],native_key_differences=0,native_rowid_differences=0))
assert len(verified)==10
(O/'native-index-key-proof.json').write_text(json.dumps(dict(verified=verified,index_authority=False,parent_history_authority=False),indent=2))
print(json.dumps(dict(native_keys=len(verified),native_differences=0,index_authority=False)))
