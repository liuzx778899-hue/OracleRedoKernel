"""Native/raw evidence for a foreign rollback inside A's active window; no support claim."""
import json,re,sys
from pathlib import Path
d=Path(sys.argv[1]); raw={}; errors=[]; native=[]
for line in (d/'fields.stdout.txt').read_text(encoding='utf-8-sig').splitlines():
    p=line.split('|');at=tuple(map(int,p[:4]))
    if at in raw:raise RuntimeError('DUPLICATE_RAW_POSITION')
    raw[at]=(p[4],int(p[6]),[bytes.fromhex(x) for x in p[7:]])
for r in re.split(r'(?=REDO RECORD - Thread:)',(d/'dump.trc').read_text()):
    if not r.startswith('REDO RECORD - Thread:') or 'CON_UID: 677737149' not in r.splitlines()[0]:continue
    rba=tuple(int(x,16) for x in re.search(r'RBA: 0x([0-9a-f]+)\.([0-9a-f]+)\.([0-9a-f]+)',r).groups());scn=int(re.search(r'\nSCN: 0x([0-9a-f]+)',r)[1],16)
    for c in re.split(r'(?=CHANGE #\d+)',r)[1:]:
        h=re.search(r'CHANGE #(\d+) CON_ID:3 [^\n]*DBA:0x0300015b OBJ:73066[^\n]*OP:(10\.\d+)\b',c)
        if not h:continue
        if h[2]=='10.4' and re.search(r'xid: +0x000a\.013\.000004d1',c):role='A_DELETE'
        elif h[2]=='10.2' and re.search(r'xid: +0x0009\.016\.00000248',c):role='B_INSERT'
        elif h[2]=='10.3' and 'slt: 22 wrp: 584 flg: 0x0010' in r:role='B_PURGE'
        elif h[2]=='10.5' and 'slt: 19 wrp: 1233 flg: 0x0010' in r:role='A_RESTORE'
        else:continue
        leaf=re.search(r'itl: (\d+), sno: (\d+), row size (\d+)',c);at=rba+(int(h[1]),)
        n={'role':role,'position':at,'scn':scn,'opcode':h[2],'itl':int(leaf[1]),'slot':int(leaf[2]),'size':int(leaf[3])};native.append(n)
        if at not in raw:errors.append('MISSING_RAW_POSITION');continue
        op,s,f=raw[at];b=f[1]
        if len(b)!=6 or (op,s,b[0],int.from_bytes(b[2:4],'little'),int.from_bytes(b[4:6],'little'))!=(h[2],scn,n['itl'],n['slot'],n['size']):errors.append('LEAF_RAW_NATIVE_DIFFERENCE')
        if b[1]!=(1 if role in ('B_PURGE','A_RESTORE') else 0):errors.append('LEAF_MODE')
        if role=='B_PURGE':
            n['native_description']=re.search(r'index redo[^\n]+',c)[0]
            if len(f[0])!=8 or f[0][:2]!=bytes.fromhex('030d') or 'op: Z' not in c:errors.append('PURGE_SHORT_Z_PROFILE')
if [n['role'] for n in native]!=['A_DELETE','B_INSERT','B_PURGE','A_RESTORE']:raise RuntimeError('NATIVE_ROLES_NOT_UNIQUE')
if [n['slot'] for n in native]!=[1,1,1,1] or any(native[i]['position']>=native[i+1]['position'] for i in range(3)):errors.append('EXPECTED_NATIVE_POSITIONS')
profiles={}
for line in (d/'profiles.stdout.txt').read_text(encoding='utf-8-sig').splitlines():
    p=line.split('|');profiles[p[4]]=(tuple(map(int,p[:4])),int(p[5]),[bytes.fromhex(f) for f in p[6:]])
if set(profiles)!= {'10.3','11.3'}:raise RuntimeError('PROFILE_RAW_VECTORS_MISSING')
for opcode,(at,block,fields) in profiles.items():
    if len(fields[0])!=8 or fields[0][:2]!=bytes.fromhex('030d'):errors.append('PROFILE_KTB_Z')
if profiles['10.3'][0]!=tuple(native[2]['position']) or profiles['10.3'][1]!=0x0300015b:errors.append('PURGE_PROFILE_POSITION')
if profiles['11.3'][0]!=(21,1287,100,1) or profiles['11.3'][1]!=0x03000155 or profiles['11.3'][2][1][11]!=2:errors.append('HEAP_XR_DELETE_PROFILE')
sa=(d/'session-a.stdout.txt').read_text();sb=(d/'session-b.stdout.txt').read_text();scns=[int(re.search(label+r'\|(\d+)',t)[1]) for label,t in [('A_DELETED_SCN',sa),('B_ROLLEDBACK_SCN',sb),('A_BEFORE_ROLLBACK_SCN',sa)]]
if not scns[0]<scns[1]<scns[2] or not json.loads((d/'b-finished-a-live.json').read_text())['a_live'] or 'B_AFTER_TX|NONE' not in sb or 'A_AFTER_TX|NONE' not in sa:errors.append('SOURCE_COORDINATION')
truth=[l.split('|')[1:3] for l in (d/'truth-capture.stdout.txt').read_text().splitlines() if l.startswith('FINAL_TRUTH|')]
if truth!=[['10','ten'],['50','fifty'],['90','ninety']]:errors.append('FINAL_TRUTH')
print(json.dumps({'native':native,'source_scns':scns,'mismatches':len(errors),'errors':errors,'observed_net_slot_change':0,'foreign_marker_native_identity':'B slot22/wrap584; A slot19/wrap1233','scope':'real index short-Z PURGE/heap XR DELETE profiles and positions; no general PURGE movement or complete mixed graph acceptance','cdc_events':0},indent=2));sys.exit(bool(errors))
