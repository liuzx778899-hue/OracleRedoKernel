"""Independent native same-record full-UBA association and decoded field parity."""
import json,re,sys
from pathlib import Path
actual={};errors=[];expected={}
for line in Path(sys.argv[1]).read_text(encoding='utf-8-sig').splitlines():
    row=line.split('|');position=tuple(map(int,row[:4]))
    if position in actual:errors.append({'position':position,'reason':'DUPLICATE_PAIR'})
    actual[position]=row
def hx(match):
    return 'NONE' if match is None else bytes.fromhex(match[2]).hex()
def keydata(change):
    return re.search(r'keydata(?:/bitmap)?:\s*\((\d+)\):\s*((?:[0-9a-f]{2}\s*)+)',change)
for filename in sys.argv[2:]:
    text=Path(filename).read_text(encoding='utf-8')
    for record in re.split(r'(?=REDO RECORD - Thread:)',text):
        if not record.startswith('REDO RECORD - Thread:') or 'CON_UID: 677737149' not in record.splitlines()[0]:continue
        rba=re.search(r'RBA: 0x([0-9a-f]+)\.([0-9a-f]+)\.([0-9a-f]+)',record)
        position=tuple(int(rba[i],16) for i in (1,2,3))
        undos=[];forwards=[]
        for change in re.split(r'(?=CHANGE #\d+)',record)[1:]:
            h=re.search(r'CHANGE #(\d+) CON_ID:3 [^\n]*DBA:0x([0-9a-f]+) OBJ:(\d+)[^\n]*OP:(\d+\.\d+)\b',change)
            if not h:continue
            if h[4]=='5.1' and '[objn: 73025 objd: 73025' in change:
                xid=re.search(r'xid:\s*0x([0-9a-f]+)\.([0-9a-f]+)\.([0-9a-f]+)',change)
                ub=re.search(r'ktudb redo:[^\n]*seq: 0x([0-9a-f]+) rec: 0x([0-9a-f]+)',change)
                metadata=re.search(r'ktubu redo: slt: (\d+) wrp: (\d+)[^\n]*prev dba:\s*0x([0-9a-f]+) rci: (\d+)',change)
                leaf=re.search(r'Dump kdilk : itl=(\d+), kdxlkflg=0x([0-9a-f]+) sdc=(\d+) indexid=0x([0-9a-f]+) block=0x([0-9a-f]+)',change)
                key=re.search(r'key\s*:\s*\((\d+)\):\s*((?:[0-9a-f]{2}\s*)+)',change)
                undos.append({'vector':int(h[1]),'uba':(int(h[2],16),int(ub[1],16),int(ub[2],16)),
                              'xid':tuple(int(xid[i],16) for i in (1,2,3)),
                              'kind':'RESTORE' if '(kdxlre): restore leaf row' in change else 'PURGE',
                              'block':int(leaf[5],16),'itl':int(leaf[1]),'index':int(leaf[4],16),
                              'flags':int(leaf[2],16),'sdc':int(leaf[3]),'prev':int(metadata[3],16),
                              'rci':int(metadata[4]),'wrap':int(metadata[2]),'key':hx(key),'data':hx(keydata(change))})
            elif h[4] in ('10.2','10.4') and int(h[3])==73025:
                ub=re.search(r'op: [FC]\s+(?:xid:\s*0x[0-9a-f.]+\s+)?uba: 0x([0-9a-f]+)\.([0-9a-f]+)\.([0-9a-f]+)',change)
                leaf=re.search(r'itl: (\d+), sno: (\d+), row size (\d+)',change)
                key=re.search(r'insert key:\s*\((\d+)\):\s*((?:[0-9a-f]{2}\s*)+)',change)
                forwards.append({'vector':int(h[1]),'uba':tuple(int(ub[i],16) for i in (1,2,3)),
                                 'kind':'INSERT' if h[4]=='10.2' else 'DELETE','block':int(h[2],16),
                                 'itl':int(leaf[1]),'slot':int(leaf[2]),'size':int(leaf[3]),'key':hx(key),'data':hx(keydata(change))})
        if len(undos)!=len(forwards):raise RuntimeError('NATIVE_PAIR_COVERAGE_MISMATCH')
        for f in forwards:
            candidates=[u for u in undos if u['uba']==f['uba']]
            if len(candidates)!=1:raise RuntimeError('NATIVE_PAIR_UBA_AMBIGUOUS')
            u=candidates[0]
            row=[*position,f['vector'],u['vector'],f['kind'],f['block'],f['itl'],f['slot'],f['size'],
                 ':'.join(map(str,u['xid'])),':'.join(map(str,u['uba'])),u['kind'],u['index'],u['flags'],u['sdc'],
                 u['prev'],u['rci'],u['wrap'],u['key'],u['data'],f['key'],f['data']]
            expected[position+(f['vector'],)]=list(map(str,row))
for position in actual.keys()|expected.keys():
    if actual.get(position)!=expected.get(position):errors.append({'position':position,'reason':'NATIVE_PAIR_OR_FIELD_DIFF'})
if len(expected)!=14 or len(actual)!=14:errors.append({'reason':'FROZEN_PAIR_COUNT_MISMATCH'})
print(json.dumps({'native_pairs':len(expected),'raw_pairs':len(actual),'mismatches':len(errors),'errors':errors,
                  'scope':'frozen same-record association and fields; no compensation or logical DML acceptance','cdc_events':0},indent=2))
sys.exit(bool(errors))
