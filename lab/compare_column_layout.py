"""Independent native physical column-count/maps for bounded heap objects, no row-image inference."""
import json,re,sys
from pathlib import Path
object_id=int(sys.argv[1]);dump=Path(sys.argv[2]).read_text(encoding='utf-8');expected=[]
for record in re.split(r'(?=REDO RECORD - Thread:)',dump):
    if not record.startswith('REDO RECORD - Thread:') or 'CON_UID: 677737149' not in record.splitlines()[0]:continue
    at=tuple(int(x,16) for x in re.search(r'RBA: 0x([0-9a-f]+)\.([0-9a-f]+)\.([0-9a-f]+)',record).groups())
    for change in re.split(r'(?=CHANGE #\d+)',record)[1:]:
        h=re.search(r'^CHANGE #(\d+) CON_ID:3 [^\n]*OP:(\d+\.\d+)\b',change)
        if not h:continue
        if h[2].startswith('11.') and re.search(r'OBJ:'+str(object_id)+r'\b',change):side='FORWARD'
        elif h[2]=='5.1' and re.search(r'objd: '+str(object_id)+r'\b',change):side='INVERSE'
        else:continue
        kind=re.search(r'KDO Op code: (IRP|URP|DRP)',change)[1];op={'IRP':2,'URP':5,'DRP':3}[kind]
        slot=int(re.search(r'tabn:\s*0 slot:\s*(\d+)',change)[1]);ncol=-1;changed=0
        if op==2:ncol=int(re.search(r'cc:\s*(\d+)',change)[1]);changed=ncol
        if op==5:ncol,changed=map(int,re.search(r'ncol: (\d+) nnew: (\d+)',change).groups())
        values=[]
        for line in change.splitlines():
            m=re.search(r'col\s+(\d+):\s*\[\s*(\d+)\]\s*(.*)',line)
            null=re.search(r'col\s+(\d+):\s*\*NULL\*',line)
            if m:
                value=''.join(m[3].split()).lower()
                if len(value)!=int(m[2])*2:raise RuntimeError('NATIVE_COLUMN_LENGTH_MISMATCH')
                values.append(m[1]+':VALUE:'+value)
            elif null:values.append(null[1]+':NULL:')
        if len(values)!=changed:raise RuntimeError('NATIVE_CHANGED_COLUMN_COVERAGE_MISMATCH')
        expected.append(list(map(str,[*at,int(h[1]),side,op,ncol,changed,slot,','.join(values)])))
actual=[line.split('|') for line in Path(sys.argv[3]).read_text(encoding='utf-8-sig').splitlines()]
errors=[]
for i in range(max(len(expected),len(actual))):
    e=expected[i] if i<len(expected) else None;a=actual[i] if i<len(actual) else None
    if a!=e:errors.append({'row':i,'expected':e,'actual':a})
print(json.dumps({'native_vectors':len(expected),'mismatches':len(errors),'errors':errors,'scope':'column count/map/value fields only; trailing NULL and complete row reconstruction unaccepted','cdc_events':0},indent=2))
sys.exit(bool(errors))
