"""Compare bounded identity closure against independent Oracle diagnostic text."""
import json
import re
import sys
from pathlib import Path

actual=[line.split('|') for line in Path(sys.argv[1]).read_text(encoding='utf-8-sig').splitlines()]
actual=[[int(x) if x not in ('true','false') else x=='true' for x in row] for row in actual]
pending={}
expected=[]
cross_archive=0
for dump_path in sys.argv[2:]:
    dump=Path(dump_path).read_text(encoding='utf-8')
    for record in re.split(r'(?=REDO RECORD - Thread:)',dump):
        rba=re.search(r'RBA: 0x([0-9a-f]+)\.[0-9a-f]+\.[0-9a-f]+',record)
        if not rba or 'CON_UID: 677737149' not in record.splitlines()[0]:
            continue
        scn=int(re.search(r'^SCN: 0x([0-9a-f]+)',record,re.M)[1],16)
        changes=re.split(r'(?=CHANGE #\d+)',record)[1:]
        # Native begin undo gives full XID; native segment assignment supplies the segment DBA.
        for undo in changes:
            if not re.search(r'^CHANGE #\d+ CON_ID:3 [^\n]*OP:5\.1\b',undo) or not re.search(r'objd:\s*73024\b',undo) or 'Begin trans' not in undo:
                continue
            xid=re.search(r'xid:\s*0x([0-9a-f]+)\.([0-9a-f]+)\.([0-9a-f]+)',undo)
            if not xid:
                raise RuntimeError('missing native begin XID')
            x=[int(xid[i],16) for i in (1,2,3)]
            starts=[]
            for change in changes:
                header=re.search(r'^CHANGE #\d+ CON_ID:3 [^\n]*DBA:0x([0-9a-f]+)[^\n]*OP:5\.2\b',change)
                h=re.search(r'ktudh redo: slt: 0x([0-9a-f]+) sqn: 0x([0-9a-f]+)',change)
                if header and h and [int(h[i],16) for i in (1,2)]==x[1:]:
                    starts.append(int(header[1],16))
            if len(starts)!=1:
                raise RuntimeError('native begin segment assignment missing or ambiguous')
            key=(starts[0],x[1],x[2])
            if key in pending:
                raise RuntimeError('duplicate native pending identity')
            pending[key]=(x,scn,int(rba[1],16))
        for change in changes:
            header=re.search(r'^CHANGE #\d+ CON_ID:3 [^\n]*DBA:0x([0-9a-f]+)[^\n]*OP:5\.4\b',change)
            end=re.search(r'ktucm redo: slt: 0x([0-9a-f]+) sqn: 0x([0-9a-f]+) srt: 0 sta: 9 flg: 0x([0-9a-f]+)',change)
            if not header or not end:
                continue
            key=(int(header[1],16),int(end[1],16),int(end[2],16))
            if key not in pending:
                continue
            x,begin_scn,begin_archive=pending.pop(key)
            expected.append(x+[677737149,3,begin_scn,scn,int(end[3],16)==4])
            cross_archive+=begin_archive!=int(rba[1],16)
errors=[]
if actual!=expected:
    errors.append({'reason':'IDENTITY_CLOSURE_DIFF','expected':expected,'actual':actual})
if pending or len(expected)!=11 or cross_archive!=1:
    errors.append({'reason':'FIXTURE_COVERAGE_INCOMPLETE','pending':len(pending),'closed':len(expected),'cross_archive':cross_archive})
print(json.dumps({'identities_compared':len(expected),'commit_markers':sum(not r[-1] for r in expected),
                  'rollback_markers':sum(r[-1] for r in expected),'cross_archive_identities':cross_archive,
                  'mismatches':len(errors),'errors':errors,
                  'scope':'19.3 selected identity closure only; begin SCN is first begin record, not ktubl txn start SCN; no row/savepoint acceptance'},indent=2))
sys.exit(bool(errors))
