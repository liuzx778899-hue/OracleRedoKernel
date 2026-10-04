"""Native selected full-XID heap/index allocation and compensation history, without Java input."""
import json,re,sys
from pathlib import Path
xid=tuple(map(int,sys.argv[1].split('.')));expected=[];history=[];nodes=[];active=False;ends=0;segment=None
def metadata(change):
    m=re.search(r'(?:ktubu|ktubl) redo: slt: (\d+) wrp: (\d+) flg: 0x([0-9a-f]+) prev dba:\s*0x([0-9a-f]+) rci: (\d+) opc: (\d+)\.(\d+) \[objn: (\d+) objd: (\d+) tsn: (\d+)\]',change)
    return [int(m[i],16 if i in (3,4) else 10) for i in range(1,11)] if m else None
for filename in sys.argv[3:]:
    text=Path(filename).read_text(encoding='utf-8')
    for record in re.split(r'(?=REDO RECORD - Thread:)',text):
        if not record.startswith('REDO RECORD - Thread:') or 'CON_UID: 677737149' not in record.splitlines()[0]:continue
        rba=re.search(r'RBA: 0x([0-9a-f]+)\.([0-9a-f]+)\.([0-9a-f]+)',record)
        position=[int(rba[i],16) for i in (1,2,3)];changes=[]
        for change in re.split(r'(?=CHANGE #\d+)',record)[1:]:
            h=re.search(r'CHANGE #(\d+) CON_ID:3 [^\n]*DBA:0x([0-9a-f]+)[^\n]*OP:(\d+\.\d+)\b',change)
            if h:changes.append((h,change))
        for h,change in changes:
            if h[3]!='5.1':continue
            current=re.search(r'xid:\s*0x([0-9a-f]+)\.([0-9a-f]+)\.([0-9a-f]+)',change)
            if current is None or tuple(int(current[i],16) for i in (1,2,3))!=xid:continue
            m=metadata(change);ub=re.search(r'ktudb redo:[^\n]*seq: 0x([0-9a-f]+) rec: 0x([0-9a-f]+)',change)
            uba=(int(h[2],16),int(ub[1],16),int(ub[2],16))
            if 'Begin trans' in change:
                if active or history:raise RuntimeError('NATIVE_DUPLICATE_BEGIN')
                starts=[]
                for control,c in changes:
                    pointer=re.search(r'uba: 0x([0-9a-f]+)\.([0-9a-f]+)\.([0-9a-f]+)',c)
                    if control[3]=='5.2' and pointer and tuple(int(pointer[i],16) for i in (1,2,3))==uba:
                        starts.append(int(control[2],16))
                if len(starts)!=1:raise RuntimeError('NATIVE_BEGIN_SEGMENT_BINDING_MISMATCH')
                segment=starts[0]
                active=True
            if not active:raise RuntimeError('NATIVE_NODE_WITHOUT_BEGIN')
            if history and m[3]==0 and uba[:2]+(m[4],)!=history[-1]['uba']:raise RuntimeError('NATIVE_HEAD_LINK_MISMATCH')
            node={'uba':uba,'meta':m,'begin':'Begin trans' in change};history.append(node);nodes.append(node)
            expected.append(['NODE',*position,int(h[1]),':'.join(map(str,uba)),m[8]])
        if active:
            for h,change in changes:
                if h[3] not in ('5.6','5.11'):continue
                m=metadata(change)
                if not m or m[8] not in (73024,73025):raise RuntimeError('UNSUPPORTED_NATIVE_WINDOW_COMPENSATION')
                candidates=[n for n in nodes if n['meta'][0]==m[0] and n['meta'][3:]==m[3:] and
                            (n['begin'] and m[2]==0x0c18 or not n['begin'] and n['meta'][1]==m[1] and n['uba'][0]==int(h[2],16))]
                if len(candidates)!=1 or not history or candidates[0]!=history[-1]:raise RuntimeError('NATIVE_COMPENSATION_HEAD_OR_IDENTITY_MISMATCH')
                original=history.pop()
                expected.append(['CANCEL',*position,int(h[1]),':'.join(map(str,original['uba'])),m[8]])
        for h,change in changes:
            e=re.search(r'ktucm redo: slt: 0x([0-9a-f]+) sqn: 0x([0-9a-f]+)',change)
            if active and h[3]=='5.4' and e and int(h[2],16)==segment and (int(e[1],16),int(e[2],16))==xid[1:]:
                active=False;ends+=1
                if 'rolled back transaction' in change and history:raise RuntimeError('NATIVE_ROLLBACK_LIVE_HEAD')
if active or ends!=1:raise RuntimeError('NATIVE_END_COVERAGE_MISMATCH')
for n in reversed(history):expected.append(['LIVE',':'.join(map(str,n['uba'])),n['meta'][8]])
expected=[list(map(str,row)) for row in expected]
actual=[line.split('|') for line in Path(sys.argv[2]).read_text(encoding='utf-8-sig').splitlines()]
errors=[]
for i in range(max(len(expected),len(actual))):
    a=actual[i] if i<len(actual) else None;e=expected[i] if i<len(expected) else None
    if a!=e:errors.append({'row':i,'actual':a,'expected':e})
print(json.dumps({'xid':sys.argv[1],'original_nodes':len(nodes),'compensations':sum(r[0]=='CANCEL' for r in expected),
                  'live_nodes':len(history),'mismatches':len(errors),'errors':errors,
                  'scope':'native selected full-XID heap+index allocation/cancel order; not all redo semantics or logical DML','cdc_events':0},indent=2))
sys.exit(bool(errors))
