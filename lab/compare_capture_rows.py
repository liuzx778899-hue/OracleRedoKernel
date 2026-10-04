"""Independent finite native URP complete images plus the three already executed SQL snapshots."""
import json,re,sys
from pathlib import Path

baseline,actual,state_output,actions=map(Path,sys.argv[1:5]);dumps=list(map(Path,sys.argv[5:]));text=baseline.read_text()
scn=int(re.search(r'^SNAPSHOT_SCN\|(\d+)',text,re.M)[1]);logical,data=map(int,re.search(r'^HEAP\|BEFORE\|[^|]+\|[^|]+\|(\d+)\|(\d+)',text,re.M).groups())
rows={}
for line in text.splitlines():
    p=line.split('|')
    if p[0]=='ROW':
        obj,file,block,slot=map(int,p[1:5]);key=(obj,(file<<22)|block,slot)
        if key in rows:raise RuntimeError('NATIVE_BASELINE_DUPLICATE_KEY')
        rows[key]=['NULL:' if value=='NULL' else 'VALUE:'+value.lower() for value in p[5:]]
if len(rows)!=1 or next(iter(rows.values()))[0]!='VALUE:c10c' or len(next(iter(rows.values())))!=3:
    raise RuntimeError('COMPARATOR_ONLY_OWNED_ID11_THREE_COLUMN_FIXTURE')
expected=[];sql_comparisons=0;seen=[]

def changed(change):
    values={}
    for line in change.splitlines():
        m=re.search(r'col\s+(\d+):\s*\[\s*(\d+)\]\s*(.*)',line)
        if m:
            raw=''.join(m[3].split()).lower();column=int(m[1])
            if len(raw)!=int(m[2])*2 or column in values:raise RuntimeError('NATIVE_CHANGED_BYTE_LENGTH_OR_DUPLICATE')
            values[column]='VALUE:'+raw
    if re.search(r'ncol:\s*3 nnew:\s*1\b',change) is None or set(values)!={1}:raise RuntimeError('NATIVE_ONLY_COLUMN_ONE_URP_PROFILE')
    return values

for dump in dumps:
    for record in re.split(r'(?=REDO RECORD - Thread:)',dump.read_text()):
        if not record.startswith('REDO RECORD - Thread:') or 'CON_UID: 677737149' not in record.splitlines()[0]:continue
        if int(re.search(r'^SCN: 0x([0-9a-f]+)',record,re.M)[1],16)<=scn:continue
        at=tuple(int(x,16) for x in re.search(r'RBA: 0x([0-9a-f]+)\.([0-9a-f]+)\.([0-9a-f]+)',record).groups());undos=[];forwards=[]
        for change in re.split(r'(?=CHANGE #\d+)',record)[1:]:
            h=re.search(r'^CHANGE #(\d+) CON_ID:3 [^\n]*DBA:0x([0-9a-f]+)[^\n]*OP:(\d+\.\d+)\b',change)
            if not h:continue
            if h[3]=='5.1' and re.search(r'objd: '+str(data)+r'\b',change):
                if not re.search(r'objn: '+str(logical)+r'\b',change):raise RuntimeError('NATIVE_CAPTURE_ROW_ROLE')
                pointer=re.search(r'ktudb redo:[^\n]*seq: 0x([0-9a-f]+) rec: 0x([0-9a-f]+)',change)
                undos.append(((int(h[2],16),int(pointer[1],16),int(pointer[2],16)),int(h[1]),change))
            elif h[3]=='11.5' and re.search(r'OBJ:'+str(data)+r'\b',change):
                if 'xtype: XA' not in change:raise RuntimeError('NATIVE_CAPTURE_ROW_NOT_ORIGINAL_XA')
                pointer=re.search(r'uba: 0x([0-9a-f]+)\.([0-9a-f]+)\.([0-9a-f]+)',change)
                forwards.append((tuple(int(x,16) for x in pointer.groups()),int(h[2],16),change))
        for uba,dba,forward in forwards:
            matches=[u for u in undos if u[0]==uba]
            if len(matches)!=1:raise RuntimeError('NATIVE_CAPTURE_ROW_PAIR_COUNT')
            _,number,inverse=matches[0];slot=int(re.search(r'tabn:\s*0 slot:\s*(\d+)',forward)[1])
            if int(re.search(r'tabn:\s*0 slot:\s*(\d+)',inverse)[1])!=slot or int(re.search(r'bdba:\s*0x([0-9a-f]+)',inverse)[1],16)!=dba:raise RuntimeError('NATIVE_CAPTURE_ROW_POSITION')
            xid=tuple(int(x,16) for x in re.search(r'\n\s*xid:\s*0x([0-9a-f]+)\.([0-9a-f]+)\.([0-9a-f]+)',inverse).groups());key=(data,dba,slot)
            if key not in rows:raise RuntimeError('NATIVE_CAPTURE_MISSING_BASELINE_KEY')
            before=rows[key].copy();prior=changed(inverse);values=changed(forward)
            if any(before[k]!=v for k,v in prior.items()):raise RuntimeError('NATIVE_CAPTURE_BEFORE_VALUE')
            after=before.copy()
            for k,v in values.items():after[k]=v
            rows[key]=after;seen.append(at[0])
            expected.append(['ROW',':'.join(map(str,at+(number,))),f'Xid[usn={xid[0]}, slot={xid[1]}, sequence={xid[2]}]',str(data),str(dba),str(slot),'UPDATE',','.join(before),','.join(after)])
            sql=[line.strip().split('|') for line in (actions/('step-'+str(at[0])+'.stdout.txt')).read_text().splitlines() if line.strip().startswith('TRUTH|')]
            if len(sql)!=1 or sql[0][1:3]!=[str(at[0]),'11'] or after!=['VALUE:c10c','VALUE:'+sql[0][3].encode('utf-8').hex(),'VALUE:'+sql[0][4].encode('utf-8').hex()]:raise RuntimeError('NATIVE_CAPTURE_COMMITTED_SQL_MISMATCH')
            sql_comparisons+=1
if seen!=[40,41,42]:raise RuntimeError('NATIVE_CAPTURE_EXPECTED_CONTROLLED_SEQUENCE_COVERAGE')
output=[line.split('|') for line in actual.read_text().splitlines()];errors=[]
for i in range(max(len(expected),len(output))):
    e=expected[i] if i<len(expected) else None;a=output[i] if i<len(output) else None
    if a!=e:errors.append({'row':i,'expected':e,'actual':a})
expected_state=['STATE|'+str(key[0])+'|'+str(key[1])+'|'+str(key[2])+'|'+','.join(value) for key,value in sorted(rows.items())]
if state_output.read_text().splitlines()!=expected_state:errors.append({'final_state_expected':expected_state,'actual':state_output.read_text().splitlines()})
print(json.dumps({'native_complete_updates':len(expected),'post_commit_sql_comparisons':sql_comparisons,'complete_final_rows':len(rows),'mismatches':len(errors),'errors':errors,'scope':'owned ID11 three-column three-URP fixture only; general capture DML and ACK unaccepted'},indent=2));sys.exit(bool(errors))
