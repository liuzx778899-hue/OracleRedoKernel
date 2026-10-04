"""Independent native positions for the frozen lower/higher/lower INSERT experiment."""
import json,re,sys
from pathlib import Path

d=Path(sys.argv[1]); errors=[]; native=[]; original_uba=None
raw={}
for line in (d/'fields.stdout.txt').read_text(encoding='utf-8-sig').splitlines():
    p=line.split('|'); at=tuple(map(int,p[:4]))
    if at in raw: raise RuntimeError('DUPLICATE_RAW_POSITION')
    raw[at]=(p[4],int(p[6]),[bytes.fromhex(f) for f in p[7:]])
for r in re.split(r'(?=REDO RECORD - Thread:)',(d/'dump.trc').read_text()):
    if not r.startswith('REDO RECORD - Thread:') or 'CON_UID: 677737149' not in r.splitlines()[0]: continue
    rba=tuple(int(x,16) for x in re.search(r'RBA: 0x([0-9a-f]+)\.([0-9a-f]+)\.([0-9a-f]+)',r).groups())
    scn=int(re.search(r'\nSCN: 0x([0-9a-f]+)',r)[1],16)
    for c in re.split(r'(?=CHANGE #\d+)',r)[1:]:
        h=re.search(r'CHANGE #(\d+) CON_ID:3 [^\n]*DBA:0x0300014b OBJ:73064[^\n]*OP:(10\.\d+)\b',c)
        if not h: continue
        if h[2]=='10.4' and re.search(r'xid: +0x000a\.01d\.000004ce',c): role='A_DELETE'
        elif h[2]=='10.2' and '0x02402e81.00e0.' in c: role='B_INSERT'
        elif h[2]=='10.5' and 'slt: 29 wrp: 1230 flg: 0x0010' in r: role='A_RESTORE'
        else: continue
        leaf=re.search(r'itl: (\d+), sno: (\d+), row size (\d+)',c)
        at=rba+(int(h[1]),); fields=raw.get(at)
        row={'role':role,'position':at,'scn':scn,'opcode':h[2],'itl':int(leaf[1]),'slot':int(leaf[2]),'size':int(leaf[3])}
        if fields is None: errors.append('MISSING_RAW:'+str(at))
        else:
            op,s,f=fields; b=f[1]
            if len(b)!=6 or (op,s,b[0],int.from_bytes(b[2:4],'little'),int.from_bytes(b[4:6],'little'))!=(h[2],scn,row['itl'],row['slot'],row['size']): errors.append('RAW_NATIVE_DIFFERENCE:'+str(at))
            if b[1]!=(1 if role=='A_RESTORE' else 0): errors.append('LEAF_MODE:'+str(at))
            if role=='B_INSERT':
                key=re.search(r'insert key: \(3\): +([^\n]+)',c)
                row['key']=bytes.fromhex(key[1]).hex()
                if f[2].hex()!=row['key']: errors.append('INSERT_KEY_BYTE_DIFFERENCE')
        native.append(row)
        if role=='A_DELETE':
            found=[]
            for undo in re.split(r'(?=CHANGE #\d+)',r)[1:]:
                uh=re.search(r'CHANGE #\d+ CON_ID:3 [^\n]*DBA:0x([0-9a-f]+)[^\n]*OP:5\.1\b',undo)
                address=re.search(r'ktudb redo:[^\n]*seq: 0x([0-9a-f]+) rec: 0x([0-9a-f]+)',undo)
                if uh and address and 'opc: 10.22 [objn: 73064 objd: 73064' in undo and re.search(r'\n\s+xid: +0x000a\.01d\.000004ce',undo): found.append((int(uh[1],16),int(address[1],16),int(address[2],16)))
            if len(found)!=1: raise RuntimeError('NATIVE_ORIGINAL_UBA_COUNT')
            original_uba=found[0]
if [n['role'] for n in native]!=['A_DELETE','B_INSERT','B_INSERT','B_INSERT','A_RESTORE']: raise RuntimeError('NATIVE_OPERATION_ROLES')
if [n['slot'] for n in native]!=[1,1,3,2,3]: errors.append('EXPECTED_POSITIONS_NOT_OBSERVED')
if [n.get('key') for n in native[1:4]]!=['02c115','02c147','02c11f']: errors.append('NATIVE_KEY_ORDER')
if any(native[i]['position']>=native[i+1]['position'] for i in range(4)): errors.append('NATIVE_POSITION_ORDER')
expected='MATCH|'+':'.join(map(str,native[-1]['position']))+'|10.29.1230|'+':'.join(map(str,original_uba))+'|RESTORE|1|3'
if (d/'position.stdout.txt').read_text(encoding='utf-8-sig').splitlines()!=[expected]: errors.append('POSITION_MATCH_NATIVE_DIFFERENCE')
if (d/'match.stdout.txt').stat().st_size or (d/'match.rc.txt').read_text().strip()!='1' or 'MISSING_INDEX_COMPENSATION_ORIGINAL' not in (d/'match.stderr.txt').read_text(): errors.append('STATIC_REFUSAL_NOT_PROVEN')
sa=(d/'session-a.stdout.txt').read_text(); sb=(d/'session-b.stdout.txt').read_text()
scns=[int(re.search(x+r'\|(\d+)',t)[1]) for x,t in [('A_DELETED_SCN',sa),('B_COMMITTED_SCN',sb),('A_BEFORE_ROLLBACK_SCN',sa)]]
if not scns[0]<scns[1]<scns[2] or not json.loads((d/'b-finished-a-live.json').read_text())['a_live']: errors.append('SOURCE_OVERLAP_NOT_PROVEN')
truth=[l.split('|')[1:3] for l in (d/'truth-capture.stdout.txt').read_text().splitlines() if l.startswith('FINAL_TRUTH|')]
if truth!=[['10','ten'],['20','twenty'],['30','thirty'],['50','fifty'],['70','seventy'],['90','ninety']]: errors.append('FINAL_SQL_TRUTH')
print(json.dumps({'native':native,'native_original_uba':original_uba,'source_scns':scns,'mismatches':len(errors),'errors':errors,'cdc_events':0,'scope':'three INSERT positions and one compensation; complete mixed graph, logical DML and dictionary unaccepted'},indent=2))
sys.exit(bool(errors))
