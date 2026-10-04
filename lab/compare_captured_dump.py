"""Complete native record coverage comparator, independent of production byte parsing."""
import json,re,sys
from pathlib import Path

dump=Path(sys.argv[1]).read_text();rows=[line.split('|') for line in Path(sys.argv[2]).read_text().splitlines()]
actual={};errors=[];previous=None
for row in rows:
    key=(int(row[0]),int(row[1]))
    if previous is not None and key<=previous:errors.append({'unordered_output_position':key})
    previous=key
    if key in actual:errors.append({'duplicate_output_position':key})
    actual[key]=row
native={};parts=re.split(r'(?=REDO RECORD - Thread:)',dump)
pattern=r'REDO RECORD - Thread:(\d+) RBA: 0x([0-9a-f]+)\.([0-9a-f]+)\.([0-9a-f]+) LEN: 0x([0-9a-f]+) VLD: 0x([0-9a-f]+) CON_UID: (\d+)\s+SCN: 0x([0-9a-f]+) SUBSCN:\s*(\d+)'
for part in parts:
    if not part.startswith('REDO RECORD - Thread:'):continue
    match=re.search(pattern,part)
    if not match:errors.append({'unrecognized_native_record_header':part[:250]});continue
    _,sequence,block,offset,length,validity,uid,scn,sub=match.groups();key=(int(block,16),int(offset,16))
    if key in native:errors.append({'duplicate_native_position':key})
    expected=[str(key[0]),str(key[1]),str(int(length,16)),str(int(validity,16)),str(int(scn,16)),str(int(sub)),uid,','.join(re.findall(r'CHANGE #\d+[^\n]*? OP:(\d+\.\d+)',part))]
    native[key]=expected
    if actual.get(key)!=expected:errors.append({'position':key,'expected':expected,'actual':actual.get(key)})
for key in actual.keys()-native.keys():errors.append({'extra_output_position':key})
if not native:errors.append({'reason':'ZERO_COMPARISONS'})
print(json.dumps({'native_records':len(native),'output_records':len(rows),'native_headers_seen':dump.count('REDO RECORD - Thread:'),'vectors':sum(len(row[7].split(',')) if row[7] else 0 for row in native.values()),'mismatches':len(errors),'errors':errors[:30],'scope':'complete native structural record coverage; no DML or ACK acceptance'},indent=2))
sys.exit(bool(errors))
