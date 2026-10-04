"""Native lifecycle and lock graph plus complete committed row images and independent waiting SQL visibility."""
import json,re,sys
from pathlib import Path
dump,baseline,truth,actual,manifest=map(Path,sys.argv[1:6]);base=Path(__file__).parent;m=json.loads(manifest.read_text())
saved=sys.argv;sys.argv=['native',m['a_xid'],'--native-only',str(dump)];ns={}
exec(compile((base/'compare_compensation_associations.py').read_text().split("if sys.argv[2]=='--native-only':")[0],'native-blocking-window-reader','exec'),ns);sys.argv=saved
a=tuple(map(int,m['a_xid'].split('.')));b=tuple(map(int,m['b_xid'].split('.')))
if not ns['windows'][a]['last']<ns['windows'][b]['first']:raise RuntimeError('NATIVE_SAME_ROW_REDO_WINDOW_ORDER_UNPROVEN')
if not re.search(r'^WAIT\|\d+\|\d+\|enq: TX - row lock contention\|WAITING\|(\d+)\|\1$',truth.read_text(),re.M):raise RuntimeError('INDEPENDENT_LOCK_WAIT_NOT_PROVEN')
source=(base/'compare_baseline_rows.py').read_text();old="elif h[3]=='11.5' and re.search(r'OBJ:'+str(obj)+r'\\b',c):";assert source.count(old)==1
source=source.replace(old,"elif h[3]=='11.5' and 'xtype: XA' in c and re.search(r'OBJ:'+str(obj)+r'\\b',c):")
source=source.replace("errors=[]\nif cells(old)!=initial:","errors=[]\nfor label,image in [('WHILE_BLOCKED',initial),('AFTER_B_COMMIT',current)]:\n row=re.search(r'^'+label+r'\\|[^\\n]+',truth,re.M)[0].split('|',5)[5]\n if cells(row)!=image:errors.append({'kind':'sql_'+label})\nif cells(old)!=initial:")
source=source.replace("'sql_snapshot_comparisons':2","'sql_snapshot_comparisons':4,'independent_lock_wait':True,'native_a_end_before_b_begin':True")
exec(compile(source,str(base/'compare_baseline_rows.py')+'+independent-lock-wait','exec'))
