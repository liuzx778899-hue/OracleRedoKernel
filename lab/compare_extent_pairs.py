"""Independent native same-record complete UBA pairing and physical extent comparison."""
import json,re,sys
from pathlib import Path
object_id=int(sys.argv[3]);columns=int(sys.argv[4]);expected=[]
def image(c):
    op=re.search(r'KDO Op code: (IRP|URP|DRP)',c)[1];slot=int(re.search(r'tabn:\s*0 slot:\s*(\d+)',c)[1])
    ncol=-1;nnew=0;delta=0
    if op=='IRP':ncol=int(re.search(r'cc:\s*(\d+)',c)[1]);nnew=ncol
    if op=='URP':ncol,nnew,delta=map(int,re.search(r'ncol: (\d+) nnew: (\d+) size: (-?\d+)',c).groups())
    values=['ABSENT:']*columns
    for line in c.splitlines():
        m=re.search(r'col\s+(\d+):\s*\[\s*(\d+)\]\s*(.*)',line);null=re.search(r'col\s+(\d+):\s*\*NULL\*',line)
        if m:
            value=''.join(m[3].split()).lower()
            if len(value)!=int(m[2])*2:raise RuntimeError('NATIVE_EXTENT_VALUE_LENGTH')
            values[int(m[1])]='VALUE:'+value
        elif null:values[int(null[1])]='NULL:'
    return {'op':op,'slot':slot,'ncol':ncol,'nnew':nnew,'delta':delta,'values':values}
for record in re.split(r'(?=REDO RECORD - Thread:)',Path(sys.argv[1]).read_text(encoding='utf-8')):
    if not record.startswith('REDO RECORD - Thread:') or 'CON_UID: 677737149' not in record.splitlines()[0]:continue
    at=tuple(int(x,16) for x in re.search(r'RBA: 0x([0-9a-f]+)\.([0-9a-f]+)\.([0-9a-f]+)',record).groups());undo=[];rows=[]
    for c in re.split(r'(?=CHANGE #\d+)',record)[1:]:
        h=re.search(r'^CHANGE #(\d+) CON_ID:3 [^\n]*DBA:0x([0-9a-f]+)[^\n]*OP:(\d+\.\d+)\b',c)
        if not h:continue
        if h[3]=='5.1' and re.search(r'objd: '+str(object_id)+r'\b',c):
            address=re.search(r'ktudb redo:[^\n]*seq: 0x([0-9a-f]+) rec: 0x([0-9a-f]+)',c)
            xid=tuple(int(x,16) for x in re.search(r'\n\s*xid:\s*0x([0-9a-f]+)\.([0-9a-f]+)\.([0-9a-f]+)',c).groups())
            uba=(int(h[2],16),int(address[1],16),int(address[2],16));undo.append((uba,xid,image(c)))
        elif h[3] in ('11.2','11.3','11.5') and re.search(r'OBJ:'+str(object_id)+r'\b',c):
            pointer=re.search(r'uba: 0x([0-9a-f]+)\.([0-9a-f]+)\.([0-9a-f]+)',c)
            if not pointer:raise RuntimeError('NATIVE_FORWARD_UBA_MISSING')
            rows.append((int(h[1]),tuple(int(x,16) for x in pointer.groups()),image(c)))
    for number,uba,forward in rows:
        matches=[u for u in undo if u[0]==uba]
        if len(matches)!=1:raise RuntimeError('NATIVE_PAIR_NOT_UNIQUE')
        _,xid,before=matches[0]
        if before['slot']!=forward['slot']:raise RuntimeError('NATIVE_PAIR_SLOT_MISMATCH')
        removed=-1
        if forward['op']=='URP' and before['ncol']!=forward['ncol']:
            removed=forward['ncol']
            if before['ncol']!=removed+1 or forward['nnew']!=0 or before['nnew']!=1 or not before['values'][removed].startswith('VALUE:') or forward['delta']!=-((len(before['values'][removed])-6)//2+1):raise RuntimeError('NATIVE_TAIL_EXTENT_MISMATCH')
        expected.append(['PAIR',':'.join(map(str,at+(number,))),f'Xid[usn={xid[0]}, slot={xid[1]}, sequence={xid[2]}]',f'Uba[dba={uba[0]}, sequence={uba[1]}, record={uba[2]}]',str(object_id),str(before['ncol']),str(forward['ncol']),str(forward['nnew']),str(removed),str(forward['delta']),','.join(before['values']),','.join(forward['values'])])
actual=[line.split('|') for line in Path(sys.argv[2]).read_text(encoding='utf-8-sig').splitlines()];errors=[]
for i in range(max(len(expected),len(actual))):
    e=expected[i] if i<len(expected) else None;a=actual[i] if i<len(actual) else None
    if e!=a:errors.append({'row':i,'expected':e,'actual':a})
print(json.dumps({'native_pairs':len(expected),'tail_pairs':sum(e[8]!='-1' for e in expected),'mismatches':len(errors),'errors':errors,'scope':'complete UBA and physical extents only; SQL NULL/full row/dictionary unaccepted','cdc_events':0},indent=2));sys.exit(bool(errors))
