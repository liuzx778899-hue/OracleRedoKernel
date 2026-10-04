"""Native window/leaf inventory parity. Does not validate a surviving transaction graph."""
import argparse,json,re,sys
from pathlib import Path

p=argparse.ArgumentParser();p.add_argument('xid');p.add_argument('object',type=int);p.add_argument('block',type=lambda x:int(x,0));p.add_argument('output');p.add_argument('dumps',nargs='+');args=p.parse_args()
target=tuple(map(int,args.xid.split('.')));records=[];starts=[];ends=[];errors=[]
def xid(text):
    m=re.search(r'\n\s+xid:\s+0x([0-9a-f]+)\.([0-9a-f]+)\.([0-9a-f]+)',text)
    return tuple(int(x,16) for x in m.groups()) if m else None
def change_header(text):
    m=re.search(r'^CHANGE #(\d+) CON_ID:3 [^\n]*DBA:0x([0-9a-f]+)[^\n]*OP:(\d+\.\d+)\b',text)
    return (int(m[1]),int(m[2],16),m[3]) if m else None
for filename in args.dumps:
    for record in re.split(r'(?=REDO RECORD - Thread:)',Path(filename).read_text()):
        if not record.startswith('REDO RECORD - Thread:') or 'CON_UID: 677737149' not in record.splitlines()[0]:continue
        rba=re.search(r'RBA: 0x([0-9a-f]+)\.([0-9a-f]+)\.([0-9a-f]+)',record)
        pos=tuple(int(x,16) for x in rba.groups());scn=int(re.search(r'\nSCN: 0x([0-9a-f]+)',record)[1],16)
        changes=re.split(r'(?=CHANGE #\d+)',record)[1:];records.append((pos,scn,changes))
        if any(change_header(c) and change_header(c)[2]=='5.1' and xid(c)==target and '\nBegin trans' in c for c in changes):
            controls=[]
            for c in changes:
                h=change_header(c);control=re.search(r'ktudh redo: slt: 0x([0-9a-f]+) sqn: 0x([0-9a-f]+)',c)
                if h and h[2]=='5.2' and control and (int(control[1],16),int(control[2],16))==target[1:]:controls.append(h[1])
            if len(controls)!=1:raise RuntimeError('NATIVE_BEGIN_CONTROL_COUNT')
            starts.append((pos,controls[0]))
if len(starts)!=1:raise RuntimeError('NATIVE_BEGIN_COUNT')
first,segment=starts[0]
for pos,scn,changes in records:
    if pos<first:continue
    for c in changes:
        h=change_header(c);end=re.search(r'ktucm redo: slt: 0x([0-9a-f]+) sqn: 0x([0-9a-f]+)',c)
        if h and h[2]=='5.4' and h[1]==segment and end and (int(end[1],16),int(end[2],16))==target[1:]:ends.append(pos)
if len(ends)!=1:raise RuntimeError('NATIVE_END_COUNT')
last=ends[0];expected=[]
for pos,scn,changes in records:
    if not first<=pos<=last:continue
    for c in changes:
        h=change_header(c)
        if not h or h[1]!=args.block:continue
        leaf=re.search(r'itl: (\d+), sno: (\d+), row size (\d+)',c)
        kind={'10.2':'INSERT','10.4':'DELETE','10.3':'PURGE','10.5':'RESTORE'}.get(h[2])
        if kind is None or leaf is None:raise RuntimeError('NATIVE_UNKNOWN_LEAF_OPERATION')
        identity='-';uba='-';key='-';data='-';marker='-'
        if kind in ('INSERT','DELETE'):
            address=re.search(r'op: (?:F|C)[^\n]*uba: 0x([0-9a-f]+)\.([0-9a-f]+)\.([0-9a-f]+)',c)
            if not address:raise RuntimeError('NATIVE_FORWARD_UBA_MISSING')
            full=tuple(int(x,16) for x in address.groups());paired=[]
            for u in changes:
                uh=change_header(u);a=re.search(r'ktudb redo:[^\n]*seq: 0x([0-9a-f]+) rec: 0x([0-9a-f]+)',u)
                if uh and uh[2]=='5.1' and a and (uh[1],int(a[1],16),int(a[2],16))==full:paired.append(u)
            if len(paired)!=1:raise RuntimeError('NATIVE_UNDO_PAIR_COUNT')
            u=paired[0];identity='.'.join(map(str,xid(u)));uba=':'.join(map(str,full))
            if f'objn: {args.object} objd: {args.object}' not in u:raise RuntimeError('NATIVE_UNDO_OBJECT_MISMATCH')
            k=re.search(r'key :\(\d+\): +([^\n]+)',u);key=''.join(k[1].split())
            d=re.search(r'(?:keydata|keydata/bitmap): \(\d+\): +([^\n]+)',c if kind=='INSERT' else u);data=''.join(d[1].split())
        else:
            candidates=[]
            for u in changes:
                uh=change_header(u)
                m=re.search(r'ktub[ul] redo: slt: (\d+) wrp: (\d+) flg: 0x[0-9a-f]+ prev dba:  0x([0-9a-f]+) rci: (\d+) opc: 10.22 \[objn: '+str(args.object)+r' objd: '+str(args.object),u)
                if uh and uh[2] in ('5.6','5.11') and m:candidates.append(m)
            if len(candidates)!=1:raise RuntimeError('NATIVE_MARKER_COUNT')
            m=candidates[0];marker=f'{int(m[1])}:{int(m[2])}:{int(m[3],16)}:{int(m[4])}'
        expected.append(['LEAF',':'.join(map(str,pos+(h[0],))),str(scn),kind,str(args.block),leaf[1],leaf[2],leaf[3],identity,uba,key,data,marker])
actual=[line.split('|') for line in Path(args.output).read_text(encoding='utf-8-sig').splitlines()]
window=['WINDOW',':'.join(map(str,first+(0,))),':'.join(map(str,last+(0,)))]
if not actual or actual[0]!=window:errors.append('WINDOW_MISMATCH')
if actual[1:]!=expected:errors.append({'reason':'ORDERED_LEAF_INVENTORY_MISMATCH','expected':expected,'actual':actual[1:]})
print(json.dumps({'native_window':window,'native_operations':len(expected),'raw_operations':max(0,len(actual)-1),'foreign_transaction_originals':sum(e[8]!='-' and e[8]!=args.xid for e in expected),'mismatches':len(errors),'errors':errors,'cdc_events':0,'scope':'native complete selected leaf/window fields; no position replay, graph or publication'},indent=2))
sys.exit(bool(errors))
