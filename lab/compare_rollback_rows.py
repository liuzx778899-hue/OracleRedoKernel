"""Independently prove native full rollback graphs before comparing remaining committed UPDATE row images."""
import json,subprocess,sys
from pathlib import Path
dump,baseline,truth,actual=map(Path,sys.argv[1:5]);base=Path(__file__).parent;objects=[]
for line in baseline.read_text().splitlines():
 p=line.split('|')
 if p[0] in ('HEAP','INDEX') and p[1]=='BEFORE':objects.extend(p[4:6])
def native(script,args):
 p=subprocess.run([sys.executable,str(base/script),*args],capture_output=True)
 if p.returncode:raise RuntimeError('NATIVE_READER_FAILED:'+p.stderr.decode())
 return json.loads(p.stdout)
found=native('compare_object_transactions.py',[','.join(objects),'--native-only',str(dump)]);rolledback=set()
for row in found['native_discovery']:
 if row[0]!='DISCOVER':continue
 graph=native('compare_mixed_undo.py',['--concurrent','--objects',','.join(objects),row[1],'--native-only',str(dump)])
 if not graph['rolled_back']:continue
 if graph['live_nodes']!=0 or graph['original_nodes']==0 or graph['compensations']!=graph['original_nodes']:raise RuntimeError('NATIVE_INCOMPLETE_FULL_ROLLBACK')
 rolledback.add(row[1])
if not rolledback:raise RuntimeError('NATIVE_NO_FULL_ROLLBACK')
source=(base/'compare_baseline_rows.py').read_text()
old="elif h[3]=='11.5' and re.search(r'OBJ:'+str(obj)+r'\\b',c):"
assert source.count(old)==1
source=source.replace(old,"elif h[3]=='11.5' and 'xtype: XA' in c and re.search(r'OBJ:'+str(obj)+r'\\b',c):")
old="  raw_slot=int(re.search(r'tabn:"
assert source.count(old)==1
source=source.replace(old,"  if '.'.join(map(str,xid)) in rolledback:continue\n"+old)
old="errors=[]\nif cells(old)!=initial:"
assert source.count(old)==1
source=source.replace(old,"errors=[]\nrollback_truth=re.search(r'^AFTER_ROLLBACK\\|[^\\n]+',truth,re.M)[0].split('|',5)[5]\nif cells(rollback_truth)!=initial:errors.append({'kind':'sql_after_rollback','actual':cells(rollback_truth),'expected':initial})\nif cells(old)!=initial:")
source=source.replace("'sql_snapshot_comparisons':2","'sql_snapshot_comparisons':3,'native_full_rollback_xids':sorted(rolledback)")
exec(compile(source,str(base/'compare_baseline_rows.py')+'+verified-full-rollback','exec'))
