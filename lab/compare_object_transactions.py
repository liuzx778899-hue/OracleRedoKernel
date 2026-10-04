"""Native-only object discovery and full-XID header routing comparison; no DML claim."""
import json
import re
import sys
from pathlib import Path

objects=set(map(int,sys.argv[1].split(','))); actual_path=Path(sys.argv[2]); dumps=sys.argv[3:]
targets=[]
for filename in dumps:
    for record in re.split(r'(?=REDO RECORD - Thread:)',Path(filename).read_text()):
        if not record.startswith('REDO RECORD - Thread:') or 'CON_UID: 677737149' not in record.splitlines()[0]:continue
        for change in re.split(r'(?=CHANGE #\d+)',record)[1:]:
            if not re.search(r'^CHANGE #\d+ CON_ID:3 [^\n]*OP:5\.1\b',change):continue
            m=re.search(r'\[objn: (\d+) objd: (\d+) tsn:',change)
            if m is None:raise RuntimeError('NATIVE_OBJECT_FIELDS_MISSING')
            object_id,data_id=map(int,m.groups())
            if object_id not in objects and data_id not in objects:continue
            if object_id not in objects or data_id not in objects:raise RuntimeError('NATIVE_OBJECT_MAPPING_MISMATCH')
            identity=re.search(r'\n\s+xid:\s+0x([0-9a-f]+)\.([0-9a-f]+)\.([0-9a-f]+)',change)
            xid=tuple(int(x,16) for x in identity.groups())
            if xid not in targets:targets.append(xid)
if not targets:raise RuntimeError('NATIVE_NO_OBJECT_TRANSACTIONS')
expected=[]; original_count=0; association_count=0
saved_args=sys.argv
source=Path(__file__).with_name('compare_compensation_associations.py').read_text(encoding='utf-8')
prefix=source.split("if sys.argv[2]=='--native-only':")[0]
for xid in targets:
    label='.'.join(map(str,xid)); sys.argv=['native',label,'--native-only',*dumps]
    native={};exec(compile(prefix,'native-header-reader','exec'),native)
    associations=native['expected']
    window=native['windows'][xid]; originals=[u for u in native['originals'] if u['xid']==xid]
    if any(u['meta'][7] not in objects or u['meta'][8] not in objects for u in originals):raise RuntimeError('NATIVE_TRANSACTION_OUTSIDE_SCOPE')
    position=lambda at: ':'.join(map(str,at+(0,)))
    expected.append(['DISCOVER',label,str(len(originals)),position(window['first']),position(window['last'])])
    expected.extend(associations);original_count+=len(originals);association_count+=len(associations)
sys.argv=saved_args
actual=[line.split('|') for line in actual_path.read_text(encoding='utf-8-sig').splitlines()]
errors=[] if actual==expected else [{'expected':expected,'actual':actual}]
print(json.dumps({'native_transactions':len(targets),'native_originals':original_count,'window_associations':association_count,'mismatches':len(errors),'errors':errors,'scope':'object discovery and header routes only; physical graph and DML unverified','cdc_events':0},indent=2))
sys.exit(bool(errors))
