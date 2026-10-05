"""Compare selected raw 5.2 controls and exact begin-undo fields to independent native dump text."""
import json,re,struct,sys
from pathlib import Path
raw,dump=map(Path,sys.argv[1:3]);records={}
for record in re.split(r'(?=REDO RECORD - Thread:)',dump.read_text()):
    if not record.startswith('REDO RECORD - Thread:'):continue
    m=re.search(r'RBA: 0x([0-9a-f]+)\.([0-9a-f]+)\.([0-9a-f]+)',record)
    if m:records[tuple(int(v,16) for v in m.groups())]=record
count=0;objects=[]
for line in raw.read_text().splitlines():
    if not line.startswith('CONTROL|'):continue
    p=line.split('|');seq,block,offset=map(int,re.search(r'archiveSequence=(\d+), block=(\d+), offset=(\d+)',p[1]).groups());record=records[(seq,block,offset)]
    assert int(re.search(r'^SCN: 0x([0-9a-f]+)',record,re.M)[1],16)==int(p[3])
    changes=re.split(r'(?=CHANGE #\d+)',record)[1:];change=next(c for c in changes if c.startswith('CHANGE #'+p[2]+' '))
    assert 'OP:5.2 ' in change and int(re.search(r'DBA:0x([0-9a-f]+)',change)[1],16)==int(p[4])
    slot,seqno,flags=(int(v,16) for v in re.search(r'ktudh redo: slt: 0x([0-9a-f]+) sqn: 0x([0-9a-f]+) flg: 0x([0-9a-f]+)',change).groups())
    uba=tuple(int(v,16) for v in re.search(r'uba: 0x([0-9a-f]+)\.([0-9a-f]+)\.([0-9a-f]+)',change).groups());b=bytes.fromhex(p[5].split(',')[0])
    assert (slot,seqno,flags,uba)==(struct.unpack_from('<H',b)[0],struct.unpack_from('<I',b,4)[0],struct.unpack_from('<H',b,16)[0],(struct.unpack_from('<I',b,8)[0],struct.unpack_from('<H',b,12)[0],b[14]))
    candidates=[]
    for c in changes:
        if 'OP:5.1 ' not in c:continue
        dba=int(re.search(r'DBA:0x([0-9a-f]+)',c)[1],16);u=re.search(r'ktudb redo:[^\n]*seq: 0x([0-9a-f]+) rec: 0x([0-9a-f]+)',c)
        if (dba,int(u[1],16),int(u[2],16))!=uba:continue
        x=tuple(int(v,16) for v in re.search(r'\n\s*xid:\s*0x([0-9a-f]+)\.([0-9a-f]+)\.([0-9a-f]+)',c).groups());obj=tuple(map(int,re.search(r'\[objn: (\d+) objd: (\d+) tsn: (\d+)\]',c).groups()))
        assert x[1:]==(slot,seqno) and 'Begin trans' in c;candidates.append((x,obj))
    assert len(candidates)==1;objects.append(candidates[0][1][0]);count+=1
assert count==4 and objects.count(720)==2
print(json.dumps({'native_control_and_begin_undo_zero_differences':count,'foreign_object_720_controls':2,'format_source':'frozen raw seq51 and independent native diagnostic dump; no external source copied'}))
