"""Independent complete NULL image comparison with native fragments and SQL truth."""
import json,re,sys
from pathlib import Path
output,fixture=map(Path,sys.argv[1:3]);errors=[]
rows=[line.split('|') for line in output.read_text().splitlines() if line.startswith('ROW|')]
truth=[line for line in (fixture/'INSERT-once.stdout.txt').read_text().splitlines() if line.startswith('TRUTH|INSERT|11|')]
assert truth==['TRUTH|INSERT|11|<NULL>|<NULL>']
assert 'TRUTH_COUNT|DELETE|1' in (fixture/'DELETE-once.stdout.txt').read_text()
schema=(fixture/'baseline/snapshot.stdout.txt').read_text()
assert 'COLUMN|BEFORE|2|2|TXT|VARCHAR2|Y|NO|NO' in schema
assert 'COLUMN|BEFORE|3|3|NOTE|VARCHAR2|Y|YES|NO' in schema
for row in rows:
    seq,block,offset,vector=map(int,row[1].split(':'))
    native=(fixture/('native-end-%d-%d-%d.txt'%(seq,block,offset))).read_text()
    position=tuple(int(v,16) for v in re.search(r'RBA: 0x([0-9a-f]+)\.([0-9a-f]+)\.([0-9a-f]+)',native).groups())
    xid=tuple(map(int,re.fullmatch(r'Xid\[usn=(\d+), slot=(\d+), sequence=(\d+)\]',row[2]).groups()))
    undo=re.split(r'CHANGE #',native)[vector]
    native_xid=tuple(int(v,16) for v in re.search(r'xid:\s+0x([0-9a-f]+)\.([0-9a-f]+)\.([0-9a-f]+)',undo).groups())
    columns=re.findall(r'col\s+(\d+): \[\s*(\d+)\]\s+([0-9a-f ]+)\n',native)
    ok=(position==(seq,block,offset) and xid==native_xid and 'OP:5.1' in undo
        and '[objn: 73254 objd: 73254' in undo and 'cc: 1' in native and 'fb: --H-FL--' in native
        and columns==[('0','2','c1 0c')] and row[3:6]==['73254','50333103','1'])
    image='VALUE:c10c,NULL:,NULL:'
    if row[6]=='INSERT':ok=ok and row[7]=='' and row[8]==image
    elif row[6]=='DELETE':ok=ok and row[7]==image and row[8]==''
    else:ok=False
    if not ok:errors.append(row[1])
if [row[6] for row in rows]!=['INSERT','DELETE']:errors.append('operation_sequence')
print(json.dumps({'complete_null_changes':len(rows),'differences':errors,
                  'native_stored_columns':1,'sql_complete_columns':3,'full_dml_matrix_accepted':False}))
sys.exit(bool(errors))
