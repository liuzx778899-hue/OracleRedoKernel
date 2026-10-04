"""Independent native INSERT/DELETE row pairs and intermediate SQL state; no Java event production input."""
import json,re,sys
from pathlib import Path
dump,baseline,truth,actual=map(Path,sys.argv[1:5]);text=baseline.read_text();sql=truth.read_text();scn=int(re.search(r'^SNAPSHOT_SCN\|(\d+)',text,re.M)[1]);logical,data=map(int,re.search(r'^HEAP\|BEFORE\|[^|]+\|[^|]+\|(\d+)\|(\d+)',text,re.M).groups())
columns=len(re.findall(r'^COLUMN\|BEFORE\|',text,re.M))
def state(text,label):
 result={}
 for line in text.splitlines():
  p=line.split('|')
  if p[0]!=label:continue
  obj,file,block,slot=map(int,p[1:5]);key=(obj,(file<<22)|block,slot)
  if key in result:raise RuntimeError('SQL_DUPLICATE_ROW_KEY')
  result[key]=['NULL:' if value=='NULL' else 'VALUE:'+value.lower() for value in p[5:]]
 return result
initial=state(text,'ROW');current={k:v.copy() for k,v in initial.items()};expected=[];after_insert=None;seen=[]
def full_values(c):
 count=int(re.search(r'\bcc:\s*(\d+)',c)[1]);values={}
 for line in c.splitlines():
  m=re.search(r'col\s+(\d+):\s*\[\s*(\d+)\]\s*(.*)',line);n=re.search(r'col\s+(\d+):\s*\*NULL\*',line)
  if m:
   raw=''.join(m[3].split()).lower()
   if len(raw)!=int(m[2])*2:raise RuntimeError('NATIVE_INSERT_BYTE_LENGTH')
   values[int(m[1])]='VALUE:'+raw
  elif n:values[int(n[1])]='NULL:'
 if count!=columns or set(values)!=set(range(columns)):raise RuntimeError('NATIVE_INCOMPLETE_FULL_ROW')
 return [values[i] for i in range(columns)]
for record in re.split(r'(?=REDO RECORD - Thread:)',dump.read_text()):
 if not record.startswith('REDO RECORD - Thread:') or 'CON_UID: 677737149' not in record.splitlines()[0]:continue
 if int(re.search(r'^SCN: 0x([0-9a-f]+)',record,re.M)[1],16)<=scn:continue
 at=tuple(int(x,16) for x in re.search(r'RBA: 0x([0-9a-f]+)\.([0-9a-f]+)\.([0-9a-f]+)',record).groups());undos=[];forwards=[]
 for c in re.split(r'(?=CHANGE #\d+)',record)[1:]:
  h=re.search(r'^CHANGE #(\d+) CON_ID:3 [^\n]*DBA:0x([0-9a-f]+)[^\n]*OP:(\d+\.\d+)\b',c)
  if not h:continue
  if h[3]=='5.1' and re.search(r'objd: '+str(data)+r'\b',c):
   if not re.search(r'objn: '+str(logical)+r'\b',c):raise RuntimeError('NATIVE_OBJECT_ROLE')
   pointer=re.search(r'ktudb redo:[^\n]*seq: 0x([0-9a-f]+) rec: 0x([0-9a-f]+)',c);uba=(int(h[2],16),int(pointer[1],16),int(pointer[2],16));undos.append((uba,int(h[1]),c))
  elif h[3] in ('11.2','11.3') and re.search(r'OBJ:'+str(data)+r'\b',c):
   pointer=re.search(r'uba: 0x([0-9a-f]+)\.([0-9a-f]+)\.([0-9a-f]+)',c)
   forwards.append((tuple(int(x,16) for x in pointer.groups()),int(h[2],16),h[3],c))
 for uba,dba,op,c in forwards:
  matched=[u for u in undos if u[0]==uba]
  if len(matched)!=1:raise RuntimeError('NATIVE_INSERT_DELETE_PAIR')
  _,number,inverse=matched[0];slot=int(re.search(r'tabn:\s*0 slot:\s*(\d+)',c)[1]);inverse_slot=int(re.search(r'tabn:\s*0 slot:\s*(\d+)',inverse)[1]);inverse_dba=int(re.search(r'bdba:\s*0x([0-9a-f]+)',inverse)[1],16)
  if inverse_slot!=slot or inverse_dba!=dba:raise RuntimeError('NATIVE_ROW_POSITION')
  xid=tuple(int(x,16) for x in re.search(r'\n\s*xid:\s*0x([0-9a-f]+)\.([0-9a-f]+)\.([0-9a-f]+)',inverse).groups());key=(data,dba,slot);seen.append('.'.join(map(str,xid)))
  if op=='11.2':
   if key in current or 'KDO Op code: DRP' not in inverse:raise RuntimeError('NATIVE_INSERT_BASELINE_OR_INVERSE')
   before=[];after=full_values(c);current[key]=after.copy();after_insert={k:v.copy() for k,v in current.items()};kind='INSERT'
  else:
   if key not in current or 'KDO Op code: IRP' not in inverse:raise RuntimeError('NATIVE_DELETE_BASELINE_OR_INVERSE')
   before=full_values(inverse)
   if current[key]!=before:raise RuntimeError('NATIVE_DELETE_BEFORE_MISMATCH')
   after=[];del current[key];kind='DELETE'
  expected.append(['ROW',':'.join(map(str,at+(number,))),f'Xid[usn={xid[0]}, slot={xid[1]}, sequence={xid[2]}]',str(data),str(dba),str(slot),kind,','.join(before),','.join(after)])
errors=[]
for label,expected_state in [('OLD_BASELINE',initial),('AFTER_INSERT',after_insert),('FINAL',current)]:
 if state(sql,label)!=expected_state:errors.append({'kind':'sql_'+label,'expected':str(expected_state),'actual':str(state(sql,label))})
txs=[re.search(r'^'+label+r'\|([^\r\n]+)',sql,re.M)[1] for label in ['INSERT_TX','DELETE_TX']]
if seen!=txs:errors.append({'kind':'native_logical_operation_xids','expected':txs,'actual':seen})
rows=[line.split('|') for line in actual.read_text(encoding='utf-8-sig').splitlines()]
for i in range(max(len(expected),len(rows))):
 e=expected[i] if i<len(expected) else None;a=rows[i] if i<len(rows) else None
 if a!=e:errors.append({'row':i,'expected':e,'actual':a})
print(json.dumps({'baseline_scn':scn,'native_ordered_changes':len(expected),'sql_snapshot_comparisons':3,'mismatches':len(errors),'errors':errors,'scope':'fixed schema complete INSERT/DELETE native and SQL images; general DML and CDC unaccepted','cdc_events':0},indent=2));sys.exit(bool(errors))
