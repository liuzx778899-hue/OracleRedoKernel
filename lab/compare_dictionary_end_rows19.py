"""Independent captured SQL truth and native positions for the diagnostic V1 counterexample."""
import hashlib,json,re
from pathlib import Path
R=Path(__file__).resolve().parent.parent;O=R/'lab/out/dictionary-end-profiles-20261006';OLD=R/'lab/out/dictionary-anchor-real-20261005'
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
files=[Path(__file__),O/'erased-no-anchor-control.stdout.txt',OLD/'ADD-once.stdout.txt',OLD/'DROP-once.stdout.txt',OLD/'initial.snapshot.txt',OLD/'dump.trc']
(O/'row-comparison-execution-pins.json').write_text(json.dumps({str(p.relative_to(R)):sha(p) for p in files},indent=2))
def tagged(path,prefix):return [l.split('|')[1:] for l in path.read_text().splitlines() if l.startswith(prefix+'|')]
add=tagged(OLD/'ADD-once.stdout.txt','TRUTH');drop=tagged(OLD/'DROP-once.stdout.txt','TRUTH')
assert add==[['ADD','0','dictionary-added','anchor','<NULL>']] and drop==[['DROP','0','dictionary-restored','anchor']]
added_columns=tagged(OLD/'ADD-once.stdout.txt','ADD_COL');dropped_columns=tagged(OLD/'DROP-once.stdout.txt','DROP_COL')
assert len(added_columns)==4 and len(dropped_columns)==3 and added_columns[-1][0]=='EXTRA'
addx=tagged(OLD/'ADD-once.stdout.txt','XID')[0][0];dropx=tagged(OLD/'DROP-once.stdout.txt','XID')[0][0]
rows=tagged(O/'erased-no-anchor-control.stdout.txt','ROW');events=tagged(O/'erased-no-anchor-control.stdout.txt','EVENT')
assert len(rows)==len(events)==2
expected=[('dictionary-seed','dictionary-added',addx),('dictionary-added','dictionary-restored',dropx)]
native={}
for record in re.split(r'(?=REDO RECORD - Thread:)',(OLD/'dump.trc').read_text()):
    m=re.search(r'RBA: 0x([0-9a-f]+)\.([0-9a-f]+)\.([0-9a-f]+)',record)
    if m:native[tuple(int(x,16) for x in m.groups())]=record
for row,event,(before,after,xid) in zip(rows,events,expected):
    u,s,q=map(int,xid.split('.'));assert row[1]==f'Xid[usn={u}, slot={s}, sequence={q}]' and event[3]==row[1]
    assert row[2:6]==['74862','50341165','0','UPDATE']
    # NUMBER zero is also the originally acquired seed cell; text truth was captured at each commit.
    def image(text):return 'VALUE:80,VALUE:'+text.encode().hex()+',VALUE:'+b'anchor'.hex()
    assert row[6:]==[image(before),image(after)]
    seq,block,offset,vector=map(int,row[0].split(':'));record=native[(seq,block,offset)]
    changes=re.split(r'(?=CHANGE #\d+)',record)[1:];change=next(c for c in changes if c.startswith('CHANGE #'+str(vector)+' '))
    assert 'OP:5.1' in change and '[objn: 74862 objd: 74862 tsn:' in change
    assert re.search(r'xid:.*?0x'+format(u,'04x')+r'\.'+format(s,'03x')+r'\.'+format(q,'08x'),change),change
proof={'diagnostic_control_events':2,'three_existing_columns_and_raw_XID_position_differences':0,'first_committed_schema_columns':4,'first_event_columns':len(rows[0][-1].split(',')),'missing_column':'EXTRA=NULL','source_truth':'captured ADD/DROP committed SQL; acquired initial zero cell; native role/XID/position','general_DML_accepted':False,'V1_historical_authority':False,'source_actions':0,'target_actions':0}
assert proof['first_event_columns']==3
(O/'row-counterexample-proof.json').write_text(json.dumps(proof,indent=2));print(json.dumps(proof))
