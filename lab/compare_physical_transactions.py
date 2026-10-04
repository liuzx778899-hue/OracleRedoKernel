"""Independently discover every native object transaction and reconstruct its physical graph."""
import json
import subprocess
import sys
from pathlib import Path

objects=sys.argv[1];actual_path=Path(sys.argv[2]);dumps=sys.argv[3:];base=Path(__file__).parent
def native(script,args):
    p=subprocess.run([sys.executable,str(base/script),*args],capture_output=True)
    if p.returncode:raise RuntimeError('NATIVE_READER_FAILED:'+p.stderr.decode())
    return json.loads(p.stdout)
discovery=native('compare_object_transactions.py',[objects,'--native-only',*dumps])
targets=[row[1] for row in discovery['native_discovery'] if row[0]=='DISCOVER']
expected=[];originals=compensated=live=0
for xid in targets:
    graph=native('compare_mixed_undo.py',['--concurrent','--objects',objects,xid,'--native-only',*dumps])
    n=graph['original_nodes'];c=graph['compensations'];l=graph['live_nodes']
    expected.append(['TX',xid,str(n),str(c),str(l),str(graph['rolled_back']).lower()]);expected.extend(graph['native_graph'])
    originals+=n;compensated+=c;live+=l
actual=[line.split('|') for line in actual_path.read_text(encoding='utf-8-sig').splitlines()]
errors=[]
for i in range(max(len(expected),len(actual))):
    e=expected[i] if i<len(expected) else None;a=actual[i] if i<len(actual) else None
    if a!=e:errors.append({'row':i,'expected':e,'actual':a})
print(json.dumps({'native_transactions':len(targets),'original_nodes':originals,'compensations':compensated,'live_nodes':live,'mismatches':len(errors),'errors':errors,'scope':'native discovered bounded physical graphs; historical dictionary and logical DML unaccepted','cdc_events':0},indent=2))
sys.exit(bool(errors))
