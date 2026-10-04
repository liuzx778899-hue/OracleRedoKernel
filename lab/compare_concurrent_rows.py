"""Independent native begin/end positions determine commit order; complete rows checked against staged SQL states."""
import json,re,sys
from pathlib import Path
dump,baseline,truth,actual,manifest=map(Path,sys.argv[1:6]);m=json.loads(manifest.read_text());base=Path(__file__).parent
saved=sys.argv;sys.argv=['native',m['a_xid'],'--native-only',str(dump)];ns={}
prefix=(base/'compare_compensation_associations.py').read_text().split("if sys.argv[2]=='--native-only':")[0];exec(compile(prefix,'native-concurrent-window-reader','exec'),ns);sys.argv=saved
mapping={}
for line in baseline.read_text().splitlines():
 p=line.split('|')
 if p[0] in ('HEAP','INDEX') and p[1]=='BEFORE':mapping[int(p[4])]=int(p[5])
owners={};targets=set()
for u in ns['originals']:
 if u['meta'][7] not in mapping:continue
 if mapping[u['meta'][7]]!=u['meta'][8]:raise RuntimeError('NATIVE_CONCURRENT_ROLE_MISMATCH')
 owners.setdefault(u['at'][:3],set()).add(u['xid']);targets.add(u['xid'])
raw_records={};scns={}
for record in re.split(r'(?=REDO RECORD - Thread:)',dump.read_text()):
 if not record.startswith('REDO RECORD - Thread:') or 'CON_UID: 677737149' not in record.splitlines()[0]:continue
 at=tuple(int(x,16) for x in re.search(r'RBA: 0x([0-9a-f]+)\.([0-9a-f]+)\.([0-9a-f]+)',record).groups());raw_records[at]=record;scns[at]=int(re.search(r'^SCN: 0x([0-9a-f]+)',record,re.M)[1],16)
for own in owners.values():
 if len(own)!=1:raise RuntimeError('NATIVE_CONCURRENT_SHARED_RECORD_UNSUPPORTED')
order=sorted(targets,key=lambda xid:(scns[ns['windows'][xid]['last']],ns['windows'][xid]['last']))
ordered_native=''.join(raw_records[at] for xid in order for at in sorted(owners) if xid in owners[at])
a=tuple(map(int,m['a_xid'].split('.')));b=tuple(map(int,m['b_xid'].split('.')))
if not scns[ns['windows'][a]['first']]<=scns[ns['windows'][b]['first']]<=scns[ns['windows'][b]['last']]<scns[ns['windows'][a]['last']]:raise RuntimeError('NATIVE_OVERLAP_AND_REVERSED_COMMIT_ORDER_NOT_PROVEN')
def changed(c):
 values={}
 for line in c.splitlines():
  match=re.search(r'col\s+(\d+):\s*\[\s*(\d+)\]\s*(.*)',line)
  if match:
   raw=''.join(match[3].split()).lower()
   if len(raw)!=int(match[2])*2:raise RuntimeError('NATIVE_CONCURRENT_VALUE_LENGTH')
   values[int(match[1])]='VALUE:'+raw
 return values
source=(base/'compare_insert_delete_rows.py').read_text()
def replace(old,new):
 global source
 assert source.count(old)==1,old;source=source.replace(old,new)
replace('dump.read_text()', 'ordered_native')
replace("h[3] in ('11.2','11.3')", "h[3] in ('11.2','11.3','11.5') and 'xtype: XA' in c")
replace("  else:\n   if key not in current", """  elif op=='11.5':
   before=current[key].copy();prior=changed(inverse);values=changed(c)
   if set(prior)!=set(values):raise RuntimeError('NATIVE_CONCURRENT_CHANGED_COLUMNS')
   for column,value in prior.items():
    if current[key][column]!=value:raise RuntimeError('NATIVE_CONCURRENT_BEFORE_VALUE')
   for column,value in values.items():current[key][column]=value
   after=current[key].copy();kind='UPDATE'
  else:
   after_a={k:v.copy() for k,v in current.items()}
   if key not in current""")
replace("('AFTER_INSERT',after_insert)","('AFTER_B_COMMIT',after_insert),('AFTER_A_COMMIT',after_a)")
start=source.index('txs=[re.search(');end=source.index('\nif seen!=txs:',start)
source=source[:start]+"txs=[m['b_xid'],m['a_xid'],m['a_xid'],m['cleanup_xid']]"+source[end:]
replace("'sql_snapshot_comparisons':3","'sql_snapshot_comparisons':4,'native_commit_order':['.'.join(map(str,xid)) for xid in order]")
replace("'scope':'fixed schema complete INSERT/DELETE native and SQL images; general DML and CDC unaccepted'","'scope':'bounded native commit ordering and staged concurrent complete rows; general concurrency and CDC unaccepted'")
exec(compile(source,str(base/'compare_insert_delete_rows.py')+'+native-commit-order','exec'))
