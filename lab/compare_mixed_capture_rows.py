"""Independent native same-transaction row replay, checked against each source SQL step."""
import json,re,sys
from pathlib import Path
O=Path(sys.argv[1]);actual=Path(sys.argv[2]);sql=(O/'MIXED_COMMIT-once.stdout.txt').read_text()
xid=tuple(map(int,re.search(r'^XID\|([^\r\n]+)',sql,re.M)[1].split('.')))
baseline=(O/'baseline/snapshot.stdout.txt').read_text();obj=int(re.search(r'^HEAP\|BEFORE\|[^|]+\|[^|]+\|\d+\|(\d+)',baseline,re.M)[1]);scn=int(re.search(r'^SNAPSHOT_SCN\|(\d+)',baseline,re.M)[1]);current=None;expected=[];steps=[]
def values(text):
    result={}
    for ordinal,size,b in re.findall(r'col\s+(\d+):\s*\[\s*(\d+)\]\s*([^\n]*)',text):
        value=''.join(b.split()).lower();assert len(value)==int(size)*2
        result[int(ordinal)]='VALUE:'+value
    for ordinal in re.findall(r'col\s+(\d+):\s*\*NULL\*',text):result[int(ordinal)]='NULL:'
    return result
for record in re.split(r'(?=REDO RECORD - Thread:)',(O/'dump.trc').read_text()):
    if not record.startswith('REDO RECORD') or 'CON_UID: 677737149' not in record.splitlines()[0]:continue
    if int(re.search(r'^SCN: 0x([0-9a-f]+)',record,re.M)[1],16)<=scn:continue
    position=tuple(int(v,16) for v in re.search(r'RBA: 0x([0-9a-f]+)\.([0-9a-f]+)\.([0-9a-f]+)',record).groups());undos=[];rows=[]
    for c in re.split(r'(?=CHANGE #\d+)',record)[1:]:
        h=re.search(r'^CHANGE #(\d+) CON_ID:3 [^\n]*DBA:0x([0-9a-f]+)[^\n]*OP:(\d+\.\d+)\b',c)
        if not h:continue
        if h[3]=='5.1' and re.search(r'objd: '+str(obj)+r'\b',c):
            x=tuple(int(v,16) for v in re.search(r'\n\s*xid:\s*0x([0-9a-f]+)\.([0-9a-f]+)\.([0-9a-f]+)',c).groups())
            if x!=xid:continue
            p=re.search(r'ktudb redo:[^\n]*seq: 0x([0-9a-f]+) rec: 0x([0-9a-f]+)',c)
            undos.append(((int(h[2],16),int(p[1],16),int(p[2],16)),int(h[1]),c))
        elif h[3] in ('11.2','11.5','11.3') and 'xtype: XA' in c and re.search(r'OBJ:'+str(obj)+r'\b',c):
            p=re.search(r'uba: 0x([0-9a-f]+)\.([0-9a-f]+)\.([0-9a-f]+)',c);rows.append((tuple(int(v,16) for v in p.groups()),int(h[2],16),h[3],c))
    for uba,dba,op,c in rows:
        matches=[u for u in undos if u[0]==uba]
        if not matches:continue
        assert len(matches)==1;_,number,inverse=matches[0];slot=int(re.search(r'tabn:\s*0 slot:\s*(\d+)',c)[1])
        assert slot==int(re.search(r'tabn:\s*0 slot:\s*(\d+)',inverse)[1]) and dba==int(re.search(r'bdba:\s*0x([0-9a-f]+)',inverse)[1],16)
        before=[] if current is None else current.copy()
        if op=='11.2':
            assert current is None and 'KDO Op code: DRP' in inverse;v=values(c);assert set(v)=={0,1,2};current=[v[i] for i in range(3)];kind='INSERT'
        elif op=='11.5':
            assert current is not None;old,new=values(inverse),values(c);assert set(old)==set(new)
            for i,v in old.items():assert current[i]==v
            for i,v in new.items():current[i]=v
            kind='UPDATE'
        else:
            assert current is not None and values(inverse)==dict(enumerate(current));current=None;kind='DELETE'
        after=[] if current is None else current.copy();steps.append(after)
        expected.append(['ROW',':'.join(map(str,position+(number,))),f'Xid[usn={xid[0]}, slot={xid[1]}, sequence={xid[2]}]',str(obj),str(dba),str(slot),kind,','.join(before),','.join(after)])
errors=[]
for step,image in enumerate(steps,1):
    truth=[l.split('|')[3:] for l in sql.splitlines() if l.startswith('STEP|MIXED_COMMIT|'+str(step)+'|11|')]
    expected_sql=[] if not image else [['11',bytes.fromhex(image[1].split(':')[1]).decode(),bytes.fromhex(image[2].split(':')[1]).decode()]]
    if truth!=expected_sql:errors.append('sql_step_'+str(step))
rows=[l.split('|') for l in actual.read_text().splitlines() if l.startswith('ROW|')]
if rows!=expected:errors.append({'expected':expected,'actual':rows})
if [r[6] for r in expected]!=['INSERT','UPDATE','DELETE']:errors.append('native_operation_order')
for name in ['MIXED_COMMIT','INSERT_ROLLBACK','DELETE_ROLLBACK']:
    t=(O/(name+'-once.stdout.txt')).read_text()
    if 'TRUTH|'+name+'|0|mixed-seed|anchor' not in t or 'TRUTH_COUNT|'+name+'|1' not in t:errors.append(name+'_final')
print(json.dumps({'native_complete_changes':len(expected),'sql_intermediate_steps':3,'differences':errors,'full_dml_matrix_accepted':False}));sys.exit(bool(errors))
