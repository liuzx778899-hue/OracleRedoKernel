"""Independent native ordered row images plus frozen AS OF and final SQL bytes; never Java change production input."""
import json,re,sys
from pathlib import Path
dump,baseline_file,truth_file,actual_file=map(Path,sys.argv[1:5])
text=baseline_file.read_text();truth=truth_file.read_text();scn=int(re.search(r'^BASELINE_SCN\|(\d+)',text,re.M)[1])
base=re.search(r'^BASELINE\|(\d+)\|(\d+)\|(\d+)\|(\d+)\|([^\n]+)',text,re.M)
obj,file,block,slot=map(int,base.groups()[:4]);dba=(file<<22)|block
def cells(raw):return ['NULL:' if x=='NULL' else 'VALUE:'+x.lower() for x in raw.strip().split('|')]
current=cells(base[5]);initial=current.copy();expected=[]
def changed(c):
 values={}
 for line in c.splitlines():
  m=re.search(r'col\s+(\d+):\s*\[\s*(\d+)\]\s*(.*)',line);n=re.search(r'col\s+(\d+):\s*\*NULL\*',line)
  if m:
   raw=''.join(m[3].split()).lower()
   if len(raw)!=int(m[2])*2:raise RuntimeError('NATIVE_ROW_BYTE_LENGTH')
   values[int(m[1])]='VALUE:'+raw
  elif n:values[int(n[1])]='NULL:'
 return values
for record in re.split(r'(?=REDO RECORD - Thread:)',dump.read_text()):
 if not record.startswith('REDO RECORD - Thread:') or 'CON_UID: 677737149' not in record.splitlines()[0]:continue
 record_scn=int(re.search(r'^SCN: 0x([0-9a-f]+)',record,re.M)[1],16)
 if record_scn<=scn:continue
 at=tuple(int(x,16) for x in re.search(r'RBA: 0x([0-9a-f]+)\.([0-9a-f]+)\.([0-9a-f]+)',record).groups());undos=[];forwards=[]
 for c in re.split(r'(?=CHANGE #\d+)',record)[1:]:
  h=re.search(r'^CHANGE #(\d+) CON_ID:3 [^\n]*DBA:0x([0-9a-f]+)[^\n]*OP:(\d+\.\d+)\b',c)
  if not h:continue
  if h[3]=='5.1' and re.search(r'objd: '+str(obj)+r'\b',c):
   pointer=re.search(r'ktudb redo:[^\n]*seq: 0x([0-9a-f]+) rec: 0x([0-9a-f]+)',c)
   uba=(int(h[2],16),int(pointer[1],16),int(pointer[2],16));undos.append((uba,int(h[1]),c))
  elif h[3]=='11.5' and re.search(r'OBJ:'+str(obj)+r'\b',c):
   pointer=re.search(r'uba: 0x([0-9a-f]+)\.([0-9a-f]+)\.([0-9a-f]+)',c)
   forwards.append((tuple(int(x,16) for x in pointer.groups()),int(h[2],16),c))
 for uba,raw_dba,c in forwards:
  match=[u for u in undos if u[0]==uba]
  if len(match)!=1 or raw_dba!=dba:raise RuntimeError('NATIVE_ROW_PAIR_OR_BLOCK')
  _,number,inverse=match[0];xid=tuple(int(x,16) for x in re.search(r'\n\s*xid:\s*0x([0-9a-f]+)\.([0-9a-f]+)\.([0-9a-f]+)',inverse).groups())
  raw_slot=int(re.search(r'tabn:\s*0 slot:\s*(\d+)',c)[1]);inverse_slot=int(re.search(r'tabn:\s*0 slot:\s*(\d+)',inverse)[1])
  if raw_slot!=slot or inverse_slot!=slot:raise RuntimeError('NATIVE_ROW_SLOT')
  before=current.copy();previous_ncol=int(re.search(r'ncol: (\d+)',inverse)[1]);ncol,nnew,size=map(int,re.search(r'ncol: (\d+) nnew: (\d+) size: (-?\d+)',c).groups());prior_values=changed(inverse);new_values=changed(c)
  for column,value in prior_values.items():
   if current[column]!=value:raise RuntimeError('NATIVE_BASELINE_BEFORE_DISAGREES')
  if ncol!=previous_ncol:
   if previous_ncol!=ncol+1 or nnew!=0 or list(prior_values)!=[ncol] or size!=-((len(prior_values[ncol])-6)//2+1):raise RuntimeError('NATIVE_BASELINE_UNSUPPORTED_TAIL')
   current[ncol]='NULL:'
  for column,value in new_values.items():current[column]=value
  expected.append(['ROW',':'.join(map(str,at+(number,))),f'Xid[usn={xid[0]}, slot={xid[1]}, sequence={xid[2]}]',str(obj),str(dba),str(slot),'UPDATE',','.join(before),','.join(current)])
old=re.search(r'^OLD_BASELINE\|[^\n]+',truth,re.M)[0].split('|',5)[5];final=re.search(r'^FINAL\|[^\n]+',truth,re.M)[0].split('|',5)[5]
errors=[]
if cells(old)!=initial:errors.append({'kind':'sql_old_baseline','actual':cells(old),'expected':initial})
if cells(final)!=current:errors.append({'kind':'sql_final','actual':cells(final),'expected':current})
actual=[x.split('|') for x in actual_file.read_text(encoding='utf-8-sig').splitlines()]
for i in range(max(len(expected),len(actual))):
 e=expected[i] if i<len(expected) else None;a=actual[i] if i<len(actual) else None
 if e!=a:errors.append({'row':i,'expected':e,'actual':a})
print(json.dumps({'baseline_scn':scn,'native_ordered_changes':len(expected),'sql_snapshot_comparisons':2,'mismatches':len(errors),'errors':errors,'scope':'trusted frozen AS OF baseline and fixed schema only; baseline provenance and historical dictionary unaccepted','cdc_events':0},indent=2));sys.exit(bool(errors))
