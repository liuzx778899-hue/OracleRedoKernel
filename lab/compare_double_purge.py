"""Finite independent two-insert/two-purge native/raw experiment; no generic position rule."""
import json,re,sys
from pathlib import Path
d=Path(sys.argv[1]);raw={};native=[];errors=[];active=False
for line in (d/'fields.stdout.txt').read_text(encoding='utf-8-sig').splitlines():
    p=line.split('|');at=tuple(map(int,p[:4]));assert at not in raw;raw[at]=(p[4],int(p[6]),[bytes.fromhex(x) for x in p[7:]])
for r in re.split(r'(?=REDO RECORD - Thread:)',(d/'dump.trc').read_text()):
    if not r.startswith('REDO RECORD - Thread:') or 'CON_UID: 677737149' not in r.splitlines()[0]:continue
    at=tuple(int(x,16) for x in re.search(r'RBA: 0x([0-9a-f]+)\.([0-9a-f]+)\.([0-9a-f]+)',r).groups());scn=int(re.search(r'\nSCN: 0x([0-9a-f]+)',r)[1],16)
    for c in re.split(r'(?=CHANGE #\d+)',r)[1:]:
        h=re.search(r'^CHANGE #(\d+) CON_ID:3 [^\n]*DBA:0x0300017b OBJ:73070[^\n]*OP:(10\.[2345])\b',c)
        if not h:continue
        if h[2]=='10.4' and re.search(r'xid: +0x000a\.014\.000004d8',c):
            if active or native:raise RuntimeError('NATIVE_A_DELETE_NOT_UNIQUE')
            active=True
        if not active:continue
        leaf=re.search(r'itl: (\d+), sno: (\d+), row size (\d+)',c);pos=at+(int(h[1]),)
        item={'position':pos,'scn':scn,'opcode':h[2],'itl':int(leaf[1]),'slot':int(leaf[2]),'size':int(leaf[3])};native.append(item)
        op,rs,f=raw[pos];b=f[1]
        if len(b)!=6 or (op,rs,b[0],int.from_bytes(b[2:4],'little'),int.from_bytes(b[4:6],'little'))!=(h[2],scn,item['itl'],item['slot'],item['size']):errors.append('LEAF_RAW_NATIVE_DIFFERENCE')
        if b[1]!=(1 if h[2] in ('10.3','10.5') else 0):errors.append('LEAF_MODE')
        if h[2]=='10.2':
            key=bytes.fromhex(re.search(r'insert key: \(3\): +([^\n]+)',c)[1]).hex();item['key']=key
            if f[2].hex()!=key:errors.append('INSERT_KEY_RAW_NATIVE_DIFFERENCE')
            if not re.search(r'\n\s+xid: +0x0009\.01e\.00000248',r):errors.append('INSERT_B_NATIVE_OWNER')
        if h[2]=='10.3':
            item['native_ktb']=re.search(r'\nop: ([CZ])\b',c)[1];item['raw_ktb']=f[0].hex()
            if item['native_ktb']=='Z' and item['raw_ktb']!='030d000000000000':errors.append('SHORT_Z_PROFILE')
            if 'slt: 30 wrp: 584 flg: 0x0010' not in r:errors.append('PURGE_B_NATIVE_MARKER')
        if h[2]=='10.5':
            if 'slt: 20 wrp: 1240 flg: 0x0010' not in r:errors.append('RESTORE_A_NATIVE_MARKER')
            active=False
if [x['opcode'] for x in native]!=['10.4','10.2','10.2','10.3','10.3','10.5']:raise RuntimeError('NATIVE_ROLES_NOT_UNIQUE')
if [x['slot'] for x in native]!=[1,1,2,2,1,1] or [x.get('key') for x in native[1:3]]!=['02c115','02c11f']:errors.append('EXPECTED_LAYOUT_NOT_OBSERVED')
sa=(d/'session-a.stdout.txt').read_text();sb=(d/'session-b.stdout.txt').read_text();scns=[int(re.search(label+r'\|(\d+)',text)[1]) for label,text in [('A_DELETED_SCN',sa),('B_ROLLEDBACK_SCN',sb),('A_BEFORE_ROLLBACK_SCN',sa)]]
if not scns[0]<scns[1]<scns[2] or not json.loads((d/'b-finished-a-live.json').read_text())['a_live'] or 'A_AFTER_TX|NONE' not in sa or 'B_AFTER_TX|NONE' not in sb:errors.append('COORDINATION')
truth=[l.split('|')[1:3] for l in (d/'truth-capture.stdout.txt').read_text().splitlines() if l.startswith('FINAL_TRUTH|')]
if truth!=[['10','ten'],['50','fifty'],['90','ninety']]:errors.append('FINAL_TRUTH')
print(json.dumps({'native':native,'source_scns':scns,'mismatches':len(errors),'errors':errors,'scope':'finite two-key raw/native positions only; not a generic PURGE rule or A/B graph acceptance','cdc_events':0},indent=2));sys.exit(bool(errors))
