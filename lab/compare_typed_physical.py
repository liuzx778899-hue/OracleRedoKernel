"""Validate native logical/data roles before independent complete physical graph comparison."""
import re,sys,subprocess
from pathlib import Path
snapshot,actual=map(Path,sys.argv[1:3]);dumps=sys.argv[3:];mapping={}
for line in snapshot.read_text().splitlines():
 p=line.split('|')
 if p[0] in ('HEAP','INDEX') and p[1]=='BEFORE':mapping[int(p[4])]=int(p[5])
known=set(mapping)|set(mapping.values())
for filename in dumps:
 for record in re.split(r'(?=REDO RECORD - Thread:)',Path(filename).read_text()):
  if not record.startswith('REDO RECORD - Thread:') or 'CON_UID: 677737149' not in record.splitlines()[0]:continue
  for c in re.split(r'(?=CHANGE #\d+)',record)[1:]:
   if not re.search(r'^CHANGE #\d+ CON_ID:3 [^\n]*OP:5\.(1|6|11)\b',c):continue
   m=re.search(r'\[objn: (\d+) objd: (\d+) tsn:',c)
   if m is None:raise RuntimeError('NATIVE_TYPED_OBJECT_FIELDS_MISSING')
   obj,data=map(int,m.groups())
   if (obj in known or data in known) and mapping.get(obj)!=data:raise RuntimeError('NATIVE_TYPED_OBJECT_MAPPING_MISMATCH')
args=[sys.executable,str(Path(__file__).with_name('compare_physical_transactions.py')),','.join(map(str,sorted(known))),str(actual),*dumps]
p=subprocess.run(args,capture_output=True);sys.stdout.buffer.write(p.stdout);sys.stderr.buffer.write(p.stderr);sys.exit(p.returncode)
