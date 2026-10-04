"""Require native full rollback after the actual baseline before accepting empty changes and unchanged full SQL state."""
import json,subprocess,sys
from pathlib import Path
base=Path(__file__).parent;m=json.loads(Path(sys.argv[6]).read_text());target=m['rollback_xid'];objects=[]
for line in Path(sys.argv[2]).read_text().splitlines():
 p=line.split('|')
 if p[0] in ('HEAP','INDEX') and p[1]=='BEFORE':objects.extend(p[4:6])
p=subprocess.run([sys.executable,str(base/'compare_mixed_undo.py'),'--concurrent','--objects',','.join(objects),target,'--native-only',sys.argv[1]],capture_output=True)
if p.returncode:raise RuntimeError('NATIVE_ROLLBACK_ONLY_GRAPH_FAILED:'+p.stderr.decode())
graph=json.loads(p.stdout)
if not graph['rolled_back'] or graph['original_nodes']!=2 or graph['compensations']!=2 or graph['live_nodes']!=0:raise RuntimeError('NATIVE_ROLLBACK_ONLY_NOT_COMPLETE_ZERO_EFFECT')
source=(base/'compare_closed_batch.py').read_text();source=source.replace("'10.33.1644'",repr(target))
old="if not targets or any(ns['windows'].get(x,{}).get('last') is None or scns[ns['windows'][x]['last']]>scn for x in targets):raise RuntimeError('NATIVE_BATCH_NOT_CLOSED_BEFORE_BASELINE')"
assert source.count(old)==1
source=source.replace(old,"if targets!={tuple(map(int,target.split('.')))} or any(ns['windows'][x]['last'] is None or not scn<scns[ns['windows'][x]['first']]<=scns[ns['windows'][x]['last']] for x in targets):raise RuntimeError('NATIVE_ROLLBACK_ONLY_AFTER_BASELINE_NOT_PROVEN')")
source=source.replace("['OLD_BASELINE','FINAL']","['OLD_BASELINE','AFTER_ROLLBACK','FINAL']").replace("'sql_snapshots':2","'sql_snapshots':3,'native_original_cancelled_live':[graph['original_nodes'],graph['compensations'],graph['live_nodes']], 'native_full_rollback':graph['rolled_back']")
source=source.replace('closed batch zero changes only; no-target silence and CDC unaccepted','real post-baseline rollback-only zero changes; general silence and CDC unaccepted')
exec(compile(source,str(base/'compare_closed_batch.py')+'+native-rollback-only','exec'))
