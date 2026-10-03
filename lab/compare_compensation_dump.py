"""Independent native 5.6 matching plus frozen SQL row7 truth; no full CDC acceptance."""
import json
import re
import sys
from pathlib import Path

actual=Path(sys.argv[1]).read_text(encoding='utf-8-sig').splitlines()
truth=[json.loads(line) for line in Path(sys.argv[2]).read_text(encoding='utf-8-sig').splitlines() if line.startswith('{')]
history=[]
cancelled=[]
expected=[]
def cols(change):
    values={int(m[1]):('VALUE',''.join(m[3].split()).lower()) for m in re.finditer(r'col\s+(\d+):\s*\[\s*(\d+)\]\s*([0-9a-fA-F ]+)',change)}
    for m in re.finditer(r'col\s+(\d+):\s*\*NULL\*',change): values[int(m[1])]=('NULL','')
    return [values.get(i,('ABSENT','')) for i in range(4)]
def body(change):
    kind=re.search(r'KDO Op code: (IRP|URP|DRP)',change)
    dba=re.search(r'bdba: 0x([0-9a-f]+)',change)
    slot=re.search(r'tabn:\s*0 slot:\s*(\d+)',change)
    if not all((kind,dba,slot)): raise RuntimeError('missing native row body')
    return kind[1],int(dba[1],16),int(slot[1]),cols(change)
for dump_path in sys.argv[3:]:
    dump=Path(dump_path).read_text(encoding='utf-8')
    for record in re.split(r'(?=REDO RECORD - Thread:)',dump):
        rba=re.search(r'RBA: 0x[0-9a-f]+\.([0-9a-f]+)\.([0-9a-f]+)',record)
        if not rba or 'CON_UID: 677737149' not in record.splitlines()[0]: continue
        changes=re.split(r'(?=CHANGE #\d+)',record)[1:]
        for undo in changes:
            header=re.search(r'CHANGE #\d+ CON_ID:3 [^\n]*DBA:0x([0-9a-f]+)[^\n]*OP:5\.1\b',undo)
            if not header or not re.search(r'objd:\s*73024\b',undo): continue
            rec=re.search(r'ktudb redo:[^\n]*seq: 0x([0-9a-f]+) rec: 0x([0-9a-f]+)',undo)
            xid=re.search(r'xid:\s*0x([0-9a-f]+)\.([0-9a-f]+)\.([0-9a-f]+)',undo)
            chain=re.search(r'ktub[lu] redo: slt:\s*(\d+) wrp:\s*(\d+) flg: 0x([0-9a-f]+) prev dba:\s*0x([0-9a-f]+) rci:\s*(\d+)',undo)
            if not all((rec,xid,chain)): raise RuntimeError('missing native original fields')
            uba=[int(header[1],16),int(rec[1],16),int(rec[2],16)]
            originals=[]
            for change in changes:
                if not re.search(r'CHANGE #\d+ CON_ID:3 [^\n]*OP:11\.[235]\b',change) or 'xtype: XA' not in change: continue
                ref=re.search(r'uba: 0x([0-9a-f]+)\.([0-9a-f]+)\.([0-9a-f]+)',change)
                if ref and [int(ref[i],16) for i in (1,2,3)]==uba: originals.append(change)
            if len(originals)!=1: raise RuntimeError('missing native forward association')
            history.append({'uba':uba,'xid':[int(xid[i],16) for i in (1,2,3)],
                            'chain':[int(chain[1]),int(chain[2]),int(chain[3],16),int(chain[4],16),int(chain[5])],
                            'inverse':body(undo),'forward':body(originals[0])})
        for change in changes:
            header=re.search(r'CHANGE #\d+ CON_ID:3 [^\n]*DBA:0x([0-9a-f]+)[^\n]*OP:5\.6\b',change)
            if not header or not re.search(r'opc: 11\.1 \[objn: 73024 objd: 73024 tsn: 5\]',change): continue
            h=re.search(r'ktubu redo: slt:\s*(\d+) wrp:\s*(\d+) flg: 0x0010 prev dba:\s*0x([0-9a-f]+) rci:\s*(\d+)',change)
            rows=[c for c in changes if re.search(r'CHANGE #\d+ CON_ID:3 [^\n]*OP:11\.[235]\b',c) and 'xtype: XR' in c]
            if not h or len(rows)!=1: raise RuntimeError('missing native compensation body')
            key=[int(h[1]),int(h[2]),0,int(h[3],16),int(h[4])]
            candidates=[u for u in history if u['uba'][0]==int(header[1],16) and u['chain']==key and u['inverse']==body(rows[0])]
            if len(candidates)!=1 or candidates[0] in cancelled: raise RuntimeError('ambiguous/repeated native cancellation')
            u=candidates[0]; cancelled.append(u)
            kind={'IRP':'INSERT','URP':'UPDATE','DRP':'DELETE'}[u['forward'][0]]
            expected.append('|'.join(map(str,['cancel',int(rba[1],16),int(rba[2],16),*u['xid'],*u['uba'],kind,*u['forward'][1:3]])))
retained=[u for u in history if u['xid']==[10,5,1133] and u not in cancelled]
if len(retained)!=1: raise RuntimeError('native retained count mismatch')
u=retained[0]
native_cols=u['forward'][3]
expected.append('|'.join(map(str,['retain',*u['xid'],*u['uba'],'INSERT',*u['forward'][1:3],','.join(':'.join(c) for c in native_cols)])))
sql=[r for r in truth if r['id']==7]
errors=[]
if actual!=expected or len(cancelled)!=2 or len(history)!=18:
    errors.append({'reason':'COMPENSATION_DIFF_OR_COVERAGE','actual':actual,'expected':expected,'history':len(history),'cancelled':len(cancelled)})
if len(sql)!=1 or any(r['id']==8 for r in truth): errors.append({'reason':'SQL_SAVEPOINT_TRUTH_MISSING_OR_ROW8_PRESENT'})
else:
    dumps=[sql[0][k] for k in ('id_dump','txt_dump','amount_dump','date_dump')]
    values=[''.join(f'{int(part,16):02x}' for part in d.split(':',1)[1].strip().split(',')) for d in dumps]
    if native_cols!=[('VALUE',v) for v in values]: errors.append({'reason':'RETAINED_ROW7_SQL_DUMP_DIFF'})
print(json.dumps({'original_pairs':len(history),'compensations_compared':len(cancelled),'retained_savepoint_pairs':len(retained),
                  'sql_row7_columns_compared':4,'mismatches':len(errors),'errors':errors,
                  'scope':'T007 5.6 original association and retained row7 truth; 5.11/index/full chain/dictionary pending'},indent=2))
sys.exit(bool(errors))
