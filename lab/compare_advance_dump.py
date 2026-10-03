"""Independent native control transfer/identity-end parity; no predecessor chain claim."""
import json,re,sys
from pathlib import Path
actual=Path(sys.argv[2]).read_text(encoding='utf-8-sig').splitlines()
expected=[]
for record in re.split(r'(?=REDO RECORD - Thread:)',Path(sys.argv[1]).read_text(encoding='utf-8')):
    if not record.startswith('REDO RECORD - Thread:') or 'CON_UID: 677737149' not in record.splitlines()[0]: continue
    rba=re.search(r'RBA: 0x[0-9a-f]+\.([0-9a-f]+)\.([0-9a-f]+)',record)
    scn=int(re.search(r'^SCN: 0x([0-9a-f]+)',record,re.M)[1],16)
    if not 2158699<=scn<=2158815: continue
    changes=re.split(r'(?=CHANGE #\d+)',record)[1:]
    for change in changes:
        h=re.search(r'CHANGE #(\d+) CON_ID:3 [^\n]*DBA:0x02400080[^\n]*OP:5\.(2|4)\b',change)
        if not h: continue
        position=[int(rba[1],16),int(rba[2],16),int(h[1])]
        if h[2]=='2':
            start=re.search(r'ktudh redo: slt: 0x0001 sqn: 0x([0-9a-f]+) flg: 0x([0-9a-f]+)',change)
            if not start: continue
            flag=int(start[2],16)
            if flag==0x52:
                if int(start[1],16)!=604: raise RuntimeError('unexpected selected begin')
                expected.append('|'.join(map(str,['BEGIN',*position])));continue
            if flag!=10: raise RuntimeError('unknown selected flags')
            a=re.search(r'uba: 0x([0-9a-f]+)\.([0-9a-f]+)\.([0-9a-f]+)',change)
            uba=tuple(int(a[i],16) for i in (1,2,3))
            candidates=[]
            for undo in changes:
                u=re.search(r'CHANGE #\d+ CON_ID:3 [^\n]*DBA:0x([0-9a-f]+)[^\n]*OP:5\.1\b',undo)
                b=re.search(r'ktudb redo:[^\n]*seq: 0x([0-9a-f]+) rec: 0x([0-9a-f]+)',undo)
                if u and b and (int(u[1],16),int(b[1],16),int(b[2],16))==uba:candidates.append(undo)
            if len(candidates)!=1: raise RuntimeError('native advance candidate count')
            xid=re.search(r'xid:\s*0x([0-9a-f]+)\.([0-9a-f]+)\.([0-9a-f]+)',candidates[0])
            x=tuple(int(xid[i],16) for i in (1,2,3))
            if x!=(1,1,604): raise RuntimeError('native wrong full XID')
            expected.append('|'.join(map(str,['ADVANCE',*position,0x02400080,x[1],x[0],x[2],*uba])))
        else:
            end=re.search(r'ktucm redo: slt: 0x0001 sqn: 0x0000025c srt: 0 sta: 9 flg: 0x12 ktucf redo: uba: 0x([0-9a-f]+)\.([0-9a-f]+)\.([0-9a-f]+)',change)
            if not end: continue
            expected.append('|'.join(map(str,['END',*position,scn,*[int(end[i],16) for i in (1,2,3)],'false'])))
errors=[]
if actual!=expected or len(expected)!=17:errors.append({'reason':'CONTROL_DIFF_OR_COVERAGE','expected':expected,'actual':actual})
print(json.dumps({'advance_controls':15,'begin_identities':1,'extended_end_markers':1,'mismatches':len(errors),'errors':errors,
                  'scope':'explicit new UBA and full active identity only; predecessor UBA and surviving effects remain unproven'},indent=2))
sys.exit(bool(errors))
