"""Independent native/raw proof of a ordinary DELETE/RESTORE; no generic PURGE position claim."""
import json,re,sys
from pathlib import Path
d=Path(sys.argv[1]);raw={};native=[];errors=[]
for l in (d/'fields.stdout.txt').read_text(encoding='utf-8-sig').splitlines():
    p=l.split('|');at=tuple(map(int,p[:4]));assert at not in raw;raw[at]=(p[4],int(p[6]),[bytes.fromhex(f) for f in p[7:]])
for r in re.split(r'(?=REDO RECORD - Thread:)',(d/'dump.trc').read_text()):
    if not r.startswith('REDO RECORD - Thread:') or 'CON_UID: 677737149' not in r.splitlines()[0]:continue
    at=tuple(int(x,16) for x in re.search(r'RBA: 0x([0-9a-f]+)\.([0-9a-f]+)\.([0-9a-f]+)',r).groups());scn=int(re.search(r'\nSCN: 0x([0-9a-f]+)',r)[1],16)
    for c in re.split(r'(?=CHANGE #\d+)',r)[1:]:
        h=re.search(r'^CHANGE #(\d+) CON_ID:3 [^\n]*DBA:0x0300018b OBJ:73072[^\n]*OP:(10\.\d+)\b',c)
        if not h:continue
        if h[2]=='10.4' and re.search(r'xid: +0x000a\.00c\.000004bc',c):role='A_DELETE'
        elif h[2]=='10.4' and re.search(r'xid: +0x0009\.005\.00000249',c):role='B_DELETE'
        elif h[2]=='10.5' and 'slt: 5 wrp: 585 flg: 0x0010' in r:role='B_RESTORE'
        elif h[2]=='10.5' and 'slt: 12 wrp: 1212 flg: 0x0010' in r:role='A_RESTORE'
        else:continue
        leaf=re.search(r'itl: (\d+), sno: (\d+), row size (\d+)',c);pos=at+(int(h[1]),);n={'role':role,'position':pos,'scn':scn,'opcode':h[2],'itl':int(leaf[1]),'slot':int(leaf[2]),'size':int(leaf[3])};native.append(n)
        if pos not in raw:errors.append('MISSING_RAW_POSITION');continue
        op,s,f=raw[pos];b=f[1]
        if len(b)!=6 or (op,s,b[0],int.from_bytes(b[2:4],'little'),int.from_bytes(b[4:6],'little'))!=(h[2],scn,n['itl'],n['slot'],n['size']):errors.append('LEAF_RAW_NATIVE_DIFFERENCE')
        if b[1]!=(1 if role in ('B_RESTORE','A_RESTORE') else 0):errors.append('LEAF_MODE')
        if role=='B_RESTORE':
            n['native_description']=re.search(r'index redo[^\n]+',c)[0]
            n['raw_ktb']=f[0].hex()
            if f[0].hex()!='030db87100000000' or 'op: Z' not in c:errors.append('RESTORE_SHORT_Z_PROFILE')
if [n['role'] for n in native]!=['A_DELETE','B_DELETE','B_RESTORE','A_RESTORE']:raise RuntimeError('NATIVE_ROLES_NOT_UNIQUE')
if [n['slot'] for n in native]!=[1,0,0,1] or any(native[i]['position']>=native[i+1]['position'] for i in range(3)):errors.append('EXPECTED_POSITIONS_NOT_OBSERVED')
sa=(d/'session-a.stdout.txt').read_text();sb=(d/'session-b.stdout.txt').read_text();scns=[int(re.search(label+r'\|(\d+)',t)[1]) for label,t in [('A_DELETED_SCN',sa),('B_ROLLEDBACK_SCN',sb),('A_BEFORE_ROLLBACK_SCN',sa)]]
if not scns[0]<scns[1]<scns[2] or not json.loads((d/'b-finished-a-live.json').read_text())['a_live'] or 'A_AFTER_TX|NONE' not in sa or 'B_AFTER_TX|NONE' not in sb:errors.append('SOURCE_COORDINATION')
truth=[l.split('|')[1:3] for l in (d/'truth-capture.stdout.txt').read_text().splitlines() if l.startswith('FINAL_TRUTH|')]
if truth!=[['10','ten'],['50','fifty'],['90','ninety']]:errors.append('FINAL_TRUTH')
print(json.dumps({'native':native,'source_scns':scns,'mismatches':len(errors),'errors':errors,'observed_net_target_slot_change':0,'scope':'one ordinary DELETE/RESTORE position pair; no generic PURGE movement or A graph acceptance','cdc_events':0},indent=2));sys.exit(bool(errors))
