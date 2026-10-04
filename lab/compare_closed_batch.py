"""Native closed-window timestamps and independent SQL full state for a fresh baseline with zero subsequent changes."""
import json,re,sys
from pathlib import Path
dump,baseline,truth,changes,state_output=map(Path,sys.argv[1:6]);base=Path(__file__).parent
saved=sys.argv;sys.argv=['native','10.33.1644','--native-only',str(dump)];ns={}
exec(compile((base/'compare_compensation_associations.py').read_text().split("if sys.argv[2]=='--native-only':")[0],'native-closed-batch-window-reader','exec'),ns);sys.argv=saved
text=baseline.read_text();scn=int(re.search(r'^SNAPSHOT_SCN\|(\d+)',text,re.M)[1]);generations={}
for line in text.splitlines():
 p=line.split('|')
 if p[0] in ('HEAP','INDEX') and p[1]=='BEFORE':generations[int(p[4])]=int(p[5])
targets=set()
for u in ns['originals']:
 if u['meta'][7] not in generations:continue
 if generations[u['meta'][7]]!=u['meta'][8]:raise RuntimeError('NATIVE_CLOSED_BATCH_GENERATION')
 targets.add(u['xid'])
scns={}
for record in re.split(r'(?=REDO RECORD - Thread:)',dump.read_text()):
 if not record.startswith('REDO RECORD - Thread:') or 'CON_UID: 677737149' not in record.splitlines()[0]:continue
 at=tuple(int(x,16) for x in re.search(r'RBA: 0x([0-9a-f]+)\.([0-9a-f]+)\.([0-9a-f]+)',record).groups());scns[at]=int(re.search(r'^SCN: 0x([0-9a-f]+)',record,re.M)[1],16)
if not targets or any(ns['windows'].get(x,{}).get('last') is None or scns[ns['windows'][x]['last']]>scn for x in targets):raise RuntimeError('NATIVE_BATCH_NOT_CLOSED_BEFORE_BASELINE')
def sql_rows(text,label):
 result={}
 for line in text.splitlines():
  p=line.split('|')
  if p[0]!=label:continue
  obj,file,block,slot=map(int,p[1:5]);key=(obj,(file<<22)|block,slot)
  if key in result:raise RuntimeError('DUPLICATE_SQL_KEY')
  result[key]=['NULL:' if x=='NULL' else 'VALUE:'+x.lower() for x in p[5:]]
 return result
expected=sql_rows(text,'ROW');actual={}
for line in state_output.read_text().splitlines():
 p=line.split('|');assert p[0]=='STATE' and len(p)==5;key=tuple(map(int,p[1:4]))
 if key in actual:raise RuntimeError('DUPLICATE_ACTUAL_KEY')
 actual[key]=p[4].split(',')
errors=[]
if actual!=expected:errors.append('complete_result_state')
for label in ['OLD_BASELINE','FINAL']:
 if sql_rows(truth.read_text(),label)!=expected:errors.append('sql_'+label)
if changes.read_bytes():errors.append('unexpected_row_change')
print(json.dumps({'baseline_scn':scn,'native_closed_transactions':len(targets),'native_end_scns':{'.'.join(map(str,x)):scns[ns['windows'][x]['last']] for x in sorted(targets)},'complete_state_rows':len(expected),'sql_snapshots':2,'row_changes':len(changes.read_text().splitlines()),'mismatches':len(errors),'errors':errors,'scope':'closed batch zero changes only; no-target silence and CDC unaccepted','cdc_events':0},indent=2));sys.exit(bool(errors))
