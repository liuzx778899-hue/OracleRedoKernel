"""Independent native KTB/ktudb identity and forward/inverse column comparison."""
import argparse
import json
import re
import sys
from pathlib import Path

parser=argparse.ArgumentParser()
parser.add_argument('--object-id',type=int,default=73024)
parser.add_argument('--columns',type=int,default=4)
parser.add_argument('dump');parser.add_argument('pairs')
args=parser.parse_args()
dump=Path(args.dump).read_text(encoding='utf-8')
rows=[line.split('|') for line in Path(args.pairs).read_text(encoding='utf-8-sig').splitlines()]
actual={tuple(map(int,r[:3])):r for r in rows}
errors=[]
compared=0
after_columns=before_columns=0
def cols(change):
    values={int(m[1]):('VALUE',''.join(m[3].split()).lower()) for m in re.finditer(r'col\s+(\d+):\s*\[\s*(\d+)\]\s*([0-9a-fA-F ]+)',change)}
    for m in re.finditer(r'col\s+(\d+):\s*\*NULL\*',change): values[int(m[1])]=('NULL','')
    return [values.get(i,('ABSENT','')) for i in range(args.columns)],len(values)
for record in re.split(r'(?=REDO RECORD - Thread:)',dump):
    rba=re.search(r'RBA: 0x[0-9a-f]+\.([0-9a-f]+)\.([0-9a-f]+)',record)
    if not rba:
        continue
    changes=re.split(r'(?=CHANGE #\d+)',record)[1:]
    undos={}
    for change in changes:
        header=re.search(r'CHANGE #\d+ CON_ID:3 [^\n]*DBA:0x([0-9a-f]+)[^\n]*OP:5\.1\b',change)
        if not header or not re.search(r'objd:\s*'+str(args.object_id)+r'\b',change): continue
        h=re.search(r'ktudb redo:[^\n]*seq: 0x([0-9a-f]+) rec: 0x([0-9a-f]+)',change)
        if not h: raise RuntimeError('missing native undo UBA')
        uba=(int(header[1],16),int(h[1],16),int(h[2],16))
        if uba in undos: raise RuntimeError('duplicate native undo')
        undos[uba]=change
    for change in changes:
        header=re.search(r'CHANGE #(\d+) CON_ID:3 [^\n]*DBA:0x([0-9a-f]+)[^\n]*OP:11\.([235])\b',change)
        uba=re.search(r'uba: 0x([0-9a-f]+)\.([0-9a-f]+)\.([0-9a-f]+)',change)
        if not header or not uba or not re.search(r'xtype: XA\b',change): continue
        address=tuple(int(uba[i],16) for i in (1,2,3))
        if address not in undos: continue
        undo=undos[address]
        xid=re.search(r'xid:\s*0x([0-9a-f]+)\.([0-9a-f]+)\.([0-9a-f]+)',undo)
        slot=re.search(r'tabn:\s*0 slot:\s*(\d+)',change)
        before_slot=re.search(r'tabn:\s*0 slot:\s*(\d+)',undo)
        before_dba=re.search(r'bdba: 0x([0-9a-f]+)',undo)
        if not all((xid,slot,before_slot,before_dba)): raise RuntimeError('missing native pair identity')
        kind={'2':'INSERT','3':'DELETE','5':'UPDATE'}[header[3]]
        inverse={'INSERT':'DRP','DELETE':'IRP','UPDATE':'URP'}[kind]
        if int(before_slot[1])!=int(slot[1]) or int(before_dba[1],16)!=int(header[2],16) or not re.search(r'KDO Op code: '+inverse,undo):
            raise RuntimeError('native pair position/inverse mismatch')
        expected=[int(header[2],16),int(slot[1])]+[int(xid[i],16) for i in (1,2,3)]+list(address)
        after,ac=cols(change); before,bc=cols(undo)
        key=(int(rba[1],16),int(rba[2],16),int(header[1]))
        row=actual.get(key)
        def decode_col(s): return [tuple(x.split(':',1)) for x in s.split(',')]
        if row is None or row[3]!=kind or list(map(int,row[4:12]))!=expected or decode_col(row[12])!=after or decode_col(row[13])!=before:
            errors.append({'rba_vector':key,'expected_identity':expected,'after':after,'before':before,'actual':row})
        compared+=1; after_columns+=ac; before_columns+=bc
if compared==0 or len(actual)!=compared or len(actual)!=len(rows):
    errors.append({'reason':'ZERO_DUPLICATE_OR_UNMATCHED_PAIR','actual':len(actual),'compared':compared})
print(json.dumps({'pairs_compared':compared,'forward_present_columns':after_columns,'inverse_present_columns':before_columns,
                  'mismatches':len(errors),'errors':errors,'scope':'selected same-record forward/inverse pairs; no complete rows or compensation cancellation'},indent=2))
sys.exit(bool(errors))
