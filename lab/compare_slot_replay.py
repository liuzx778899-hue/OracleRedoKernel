"""Independent finite one-DELETE/one-RESTORE native slot/UBA proof, not a logical DML validator."""
import json,re,sys
from pathlib import Path
xid=tuple(map(int,sys.argv[1].split('.')));original=[];restore=[];errors=[]
for r in re.split(r'(?=REDO RECORD - Thread:)',Path(sys.argv[3]).read_text()):
    if not r.startswith('REDO RECORD - Thread:') or 'CON_UID: 677737149' not in r.splitlines()[0]:continue
    at=tuple(int(x,16) for x in re.search(r'RBA: 0x([0-9a-f]+)\.([0-9a-f]+)\.([0-9a-f]+)',r).groups())
    for c in re.split(r'(?=CHANGE #\d+)',r)[1:]:
        h=re.search(r'^CHANGE #(\d+) CON_ID:3 [^\n]*OP:(10\.[45])\b',c)
        if not h:continue
        leaf=re.search(r'itl: (\d+), sno: (\d+), row size (\d+)',c);slot=int(leaf[2])
        if h[2]=='10.4':
            m=re.search(r'op: F  xid: +0x([0-9a-f]+)\.([0-9a-f]+)\.([0-9a-f]+) +uba: 0x([0-9a-f]+)\.([0-9a-f]+)\.([0-9a-f]+)',c)
            if m and tuple(int(m[i],16) for i in (1,2,3))==xid:original.append((slot,tuple(int(m[i],16) for i in (4,5,6))))
        elif re.search(r'slt: '+str(xid[1])+r' wrp: '+str(xid[2])+r' flg: 0x0010',r):restore.append((at+(int(h[1]),),slot))
if len(original)!=1 or len(restore)!=1:raise RuntimeError('FINITE_NATIVE_DELETE_RESTORE_NOT_UNIQUE')
expected='|'.join(['MATCH',':'.join(map(str,restore[0][0])),sys.argv[1],':'.join(map(str,original[0][1])),'RESTORE',str(original[0][0]),str(restore[0][1])])
actual=Path(sys.argv[2]).read_text().splitlines()
if actual!=[expected]:errors.append('NATIVE_SLOT_OR_UBA_DIFFERENCE')
print(json.dumps({'native_match':expected,'mismatches':len(errors),'errors':errors,'scope':'one native DELETE/RESTORE physical slot and original UBA only','cdc_events':0},indent=2));sys.exit(bool(errors))
