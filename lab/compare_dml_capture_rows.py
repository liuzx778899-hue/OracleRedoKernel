"""Independent bounded INSERT/DELETE image audit from native records and SQL truth."""
import json
import re
import sys
from pathlib import Path

output,fixture=Path(sys.argv[1]),Path(sys.argv[2])
rows=[line.split('|') for line in output.read_text().splitlines() if line.startswith('ROW|')]
differences=[]
for row in rows:
    seq,block,offset,vector=map(int,row[1].split(':'))
    native=(fixture/('native-end-%d-%d-%d.txt'%(seq,block,offset))).read_text()
    xid=tuple(map(int,re.fullmatch(r'Xid\[usn=(\d+), slot=(\d+), sequence=(\d+)\]',row[2]).groups()))
    native_xids=[tuple(int(v,16) for v in m) for m in re.findall(r'xid:\s+0x([0-9a-f]+)\.([0-9a-f]+)\.([0-9a-f]+)',native)]
    undo=re.split(r'CHANGE #',native)[vector]
    cols=re.findall(r'col\s+(\d+): \[\s*(\d+)\]\s+([0-9a-f ]+)\n',native)
    values=[]
    for ordinal,size,hexbytes in cols:
        value=''.join(hexbytes.split())
        if int(ordinal)!=len(values) or len(value)!=int(size)*2: differences.append('column_layout')
        values.append('VALUE:'+value)
    image=','.join(values)
    truth=(fixture/'INSERT-once.stdout.txt').read_text().splitlines()
    source_row=next(line.split('|')[2:] for line in truth if line.startswith('TRUTH|INSERT|11|'))
    sql_image=['VALUE:c1'+format(int(source_row[0])+1,'02x')]+['VALUE:'+v.encode('utf-8').hex() for v in source_row[1:]]
    match=(xid in native_xids and 'OP:5.1' in undo and '[objn: 73252 objd: 73252' in undo
           and int(row[3])==73252 and int(row[4])==int(re.search(r'bdba: 0x([0-9a-f]+)',native)[1],16)
           and int(row[5])==int(re.search(r'tabn: 0 slot: (\d+)',native)[1])
           and values==sql_image)
    if row[6]=='INSERT':match=match and row[7]=='' and row[8]==image
    elif row[6]=='DELETE':match=match and row[7]==image and row[8]==''
    else:match=False
    if not match:differences.append(row[1])
if [row[6] for row in rows]!=['INSERT','DELETE']:differences.append('operation_sequence')
print(json.dumps({'complete_changes':len(rows),'image_differences':differences,'full_dml_matrix_accepted':False}))
sys.exit(bool(differences))
