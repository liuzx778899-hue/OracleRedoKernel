"""Native direct-field parity; full cross-block predecessor UBA is deliberately not certified."""
import json,re,sys
from pathlib import Path
rows=[s.split('|') for s in Path(sys.argv[2]).read_text(encoding='utf-8-sig').splitlines()]
actual={tuple(map(int,r[1:4])):r for r in rows if r[0]=='NODE'}
x=tuple(map(int,sys.argv[3].split('.')))
expected={};cross=historical_differences=0;blocks=set();end_tails=[]
for record in re.split(r'(?=REDO RECORD - Thread:)',Path(sys.argv[1]).read_text(encoding='utf-8')):
    if not record.startswith('REDO RECORD - Thread:') or 'CON_UID: 677737149' not in record.splitlines()[0]:continue
    rba=re.search(r'RBA: 0x[0-9a-f]+\.([0-9a-f]+)\.([0-9a-f]+)',record)
    for change in re.split(r'(?=CHANGE #\d+)',record)[1:]:
        h=re.search(r'CHANGE #(\d+) CON_ID:3 [^\n]*DBA:0x([0-9a-f]+)[^\n]*OP:5\.1\b',change)
        xid=re.search(r'xid:\s*0x([0-9a-f]+)\.([0-9a-f]+)\.([0-9a-f]+)',change)
        if not h or not xid or tuple(int(xid[i],16) for i in (1,2,3))!=x:continue
        b=re.search(r'ktudb redo:[^\n]*seq: 0x([0-9a-f]+) rec: 0x([0-9a-f]+)',change)
        link=re.search(r'prev dba:\s*0x([0-9a-f]+) rci:\s*(\d+)',change)
        uba=(int(h[2],16),int(b[1],16),int(b[2],16));blocks.add(uba[:2])
        key=(int(rba[1],16),int(rba[2],16),int(h[1]))
        dba=int(re.search(r'bdba: 0x([0-9a-f]+)',change)[1],16)
        slot=int(re.search(r'tabn:\s*0 slot:\s*(\d+)',change)[1])
        ktb=re.search(r'op: C\s+uba: 0x([0-9a-f]+)\.([0-9a-f]+)\.([0-9a-f]+)',change)
        old='NONE' if not ktb else ':'.join(str(int(ktb[i],16)) for i in (1,2,3))
        previous=int(link[1],16);rci=int(link[2]);cross+=previous!=0
        direct=[':'.join(map(str,uba)),str(previous),str(rci),str(dba),str(slot),old]
        expected[key]=direct
        if key in actual and old!='NONE' and old!=actual[key][5]:historical_differences+=1
    end=re.search(r'ktucm redo: slt: 0x([0-9a-f]+) sqn: 0x([0-9a-f]+) srt: 0 sta: 9 flg: 0x(?:2|12) ktucf redo: uba: 0x([0-9a-f]+)\.([0-9a-f]+)\.([0-9a-f]+)',record)
    if end and (int(end[1],16),int(end[2],16))==x[1:]:end_tails.append(':'.join(str(int(end[i],16)) for i in (3,4,5)))
errors=[]
for key in actual.keys()|expected.keys():
    row=actual.get(key);direct=None if row is None else [row[4],*row[6:11]]
    if direct!=expected.get(key):errors.append({'position':key,'reason':'DIRECT_FIELD_DIFF'})
    if row and key in expected:
        previous=int(row[6]);rci=int(row[7])
        if previous:
            if row[5]=='NONE' or int(row[5].split(':')[0])!=previous or rci!=0:errors.append({'position':key,'reason':'CROSS_DBA_DIFF'})
        elif rci and row[5]!=':'.join([*row[4].split(':')[:2],str(rci)]):errors.append({'position':key,'reason':'SAME_BLOCK_REFERENCE_DIFF'})
actual_end=[r[3] for r in rows if r[0]=='END']
if len(expected)!=120 or len(actual)!=120 or len(rows)!=121 or actual_end!=end_tails:errors.append({'reason':'COVERAGE_OR_END_TAIL_DIFF'})
print(json.dumps({'nodes_direct_fields_compared':len(expected),'undo_block_generations':len(blocks),'cross_dba_references':cross,
                  'historical_ktb_vs_candidate_transaction_predecessor_differences':historical_differences,
                  'native_end_tail_matches':actual_end==end_tails,'mismatches':len(errors),'errors':errors,
                  'scope':'cross DBA and control identity observed; full cross predecessor sequence/record inferred by experimental cursor, not independently certified'},indent=2))
sys.exit(bool(errors))
