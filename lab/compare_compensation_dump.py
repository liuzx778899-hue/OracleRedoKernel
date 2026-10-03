"""Independent native 5.6/5.11 matching plus frozen SQL truth; no full CDC acceptance."""
import json
import re
import sys
from pathlib import Path

actual=Path(sys.argv[1]).read_text(encoding='utf-8-sig').splitlines()
truth=[json.loads(line) for line in Path(sys.argv[2]).read_text(encoding='utf-8-sig').splitlines() if line.startswith('{')]
history=[]
active={}
cancelled=[]
expected=[]
regular_count=begin_count=0
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
            x=[int(xid[i],16) for i in (1,2,3)]
            segment=None
            if 'Begin trans' in undo:
                assignments=[]
                for change in changes:
                    sh=re.search(r'CHANGE #\d+ CON_ID:3 [^\n]*DBA:0x([0-9a-f]+)[^\n]*OP:5\.2\b',change)
                    st=re.search(r'ktudh redo: slt: 0x([0-9a-f]+) sqn: 0x([0-9a-f]+)',change)
                    if sh and st and [int(st[i],16) for i in (1,2)]==x[1:]: assignments.append(int(sh[1],16))
                if len(assignments)!=1: raise RuntimeError('missing native begin assignment')
                segment=assignments[0]
                if (segment,x[1]) in active: raise RuntimeError('native active slot reassignment')
                active[(segment,x[1])]=x
            history.append({'uba':uba,'xid':x,'segment':segment,
                            'chain':[int(chain[1]),int(chain[2]),int(chain[3],16),int(chain[4],16),int(chain[5])],
                            'inverse':body(undo),'forward':body(originals[0])})
        for change in changes:
            header=re.search(r'CHANGE #\d+ CON_ID:3 [^\n]*DBA:0x([0-9a-f]+)[^\n]*OP:5\.(6|11)\b',change)
            if not header or not re.search(r'opc: 11\.1 \[objn: 73024 objd: 73024 tsn: 5\]',change): continue
            h=re.search(r'ktub[lu] redo: slt:\s*(\d+) wrp:\s*(\d+) flg: 0x([0-9a-f]+) prev dba:\s*0x([0-9a-f]+) rci:\s*(\d+)',change)
            rows=[c for c in changes if re.search(r'CHANGE #\d+ CON_ID:3 [^\n]*OP:11\.[235]\b',c) and 'xtype: XR' in c]
            if not h or len(rows)!=1: raise RuntimeError('missing native compensation body')
            begin=header[2]=='11'
            key=[int(h[1]),int(h[2]),int(h[3],16)&~0x10,int(h[4],16),int(h[5])]
            address=int(header[1],16)
            candidates=[u for u in history if u['chain']==key and u['inverse']==body(rows[0])
                        and (u['segment']==address and u['xid']==active.get((address,key[0])) if begin
                             else u['uba'][0]==address and u['xid'] in active.values())]
            if len(candidates)!=1 or candidates[0] in cancelled: raise RuntimeError('ambiguous/repeated native cancellation')
            u=candidates[0]; cancelled.append(u)
            begin_count+=begin; regular_count+=not begin
            kind={'IRP':'INSERT','URP':'UPDATE','DRP':'DELETE'}[u['forward'][0]]
            expected.append('|'.join(map(str,['cancel',int(rba[1],16),int(rba[2],16),*u['xid'],*u['uba'],kind,*u['forward'][1:3]])))
        for change in changes:
            eh=re.search(r'CHANGE #\d+ CON_ID:3 [^\n]*DBA:0x([0-9a-f]+)[^\n]*OP:5\.4\b',change)
            en=re.search(r'ktucm redo: slt: 0x([0-9a-f]+) sqn: 0x([0-9a-f]+)',change)
            if not eh or not en: continue
            key=(int(eh[1],16),int(en[1],16))
            if key in active and active[key][2]==int(en[2],16): active.pop(key)
retained=[u for u in history if u['xid']==[10,5,1133] and u not in cancelled]
if len(retained)!=1: raise RuntimeError('native retained count mismatch')
u=retained[0]
native_cols=u['forward'][3]
expected.append('|'.join(map(str,['retain',*u['xid'],*u['uba'],'INSERT',*u['forward'][1:3],','.join(':'.join(c) for c in native_cols)])))
sql=[r for r in truth if r['id']==7]
errors=[]
if actual!=expected or len(cancelled)!=4 or len(history)!=18 or active or begin_count!=2 or regular_count!=2:
    errors.append({'reason':'COMPENSATION_DIFF_OR_COVERAGE','actual':actual,'expected':expected,'history':len(history),'cancelled':len(cancelled)})
if len(sql)!=1 or any(r['id'] in (6,8,18) for r in truth): errors.append({'reason':'SQL_TRUTH_MISSING_OR_ROLLED_BACK_ROW_PRESENT'})
else:
    dumps=[sql[0][k] for k in ('id_dump','txt_dump','amount_dump','date_dump')]
    values=[''.join(f'{int(part,16):02x}' for part in d.split(':',1)[1].strip().split(',')) for d in dumps]
    if native_cols!=[('VALUE',v) for v in values]: errors.append({'reason':'RETAINED_ROW7_SQL_DUMP_DIFF'})
print(json.dumps({'original_pairs':len(history),'compensations_compared':len(cancelled),'retained_savepoint_pairs':len(retained),
                  'begin_compensations':begin_count,'regular_compensations':regular_count,
                  'sql_row7_columns_compared':4,'mismatches':len(errors),'errors':errors,
                  'scope':'5.6/5.11 original association and frozen row7/rolled-back IDs truth; index/full chain/dictionary pending'},indent=2))
sys.exit(bool(errors))
