"""Independent native/value comparison and full-input checksum-valid marker refusals."""
import hashlib,json,re,struct,subprocess
from pathlib import Path
R=Path(__file__).resolve().parent.parent;O=R/'lab/out/product-active-20261007';N=R/'lab/out/product-active-marker2-20261007'
assert not (N/'events-proof.json').exists()
def sha(p):
    with p.open('rb') as f:return hashlib.file_digest(f,'sha256').hexdigest()
native=(O/'active-native-layout.trc').read_text()
records={}
for m in re.finditer(r'REDO RECORD - Thread:1 RBA: 0x([0-9a-f]+)\.([0-9a-f]+)\.([0-9a-f]+).*?(?=REDO RECORD -|\Z)',native,re.S):
    text=m.group();at=tuple(int(x,16) for x in m.groups());scn=int(re.search(r'\nSCN: 0x([0-9a-f]+)',text)[1],16)
    changes={int(q[1]):q.group() for q in re.finditer(r'CHANGE #(\d+).*?(?=CHANGE #|\Z)',text,re.S)}
    records[at]=(scn,changes)
def column(text):
    m=re.search(r'col\s+1: \[\s*(\d+)\]\s*((?:[0-9a-f]{2}\s+)+)',text)
    assert m
    raw=''.join(m[2].split());assert len(raw)==int(m[1])*2
    return 'VALUE:'+raw
before=column(records[(75,95318,16)][1][3]);middle=column(records[(75,95318,16)][1][1]);after=column(records[(76,2,16)][1][2])
assert before==after and middle==column(records[(76,2,16)][1][1])
assert 'Begin trans' in records[(75,95318,16)][1][3]
assert 'xid:  0x000a.000.00000b8f' in records[(75,95318,16)][1][3] and 'xid:  0x000a.000.00000b8f' in records[(76,2,16)][1][1]
assert 'slt: 0x0000 sqn: 0x00000b8f' in records[(76,2,372)][1][1]
def verify(text):
    q=[x.split('|') for x in text.splitlines()];events=[x for x in q if x[0]=='EVENT'];rows=[x for x in q if x[0]=='ROW'];schemas=[x for x in q if x[0]=='SCHEMA']
    assert len(events)==len(rows)==len(schemas)==2
    for i,at in enumerate(['75:95318:16:3','76:2:16:1']):
        assert events[i][3:]==[at,'Xid[usn=10, slot=0, sequence=2959]',str(records[(75,95318,16)][0]),str(records[(76,2,372)][0])]
        assert rows[i][1:6]==[at,'75185','50342532','0','UPDATE']
        assert rows[i][6:]==['VALUE:80,'+(before if i==0 else middle)+',VALUE:616e63686f72','VALUE:80,'+(middle if i==0 else after)+',VALUE:616e63686f72']
        assert schemas[i][1:3]==[at,'9a0aac66a1babbb575e9ce68fb60383fecd6006707bc6570af5a065e079dbde0']
        assert 'name=NOTE, type=VARCHAR2, nullable=true, hidden=true' in schemas[i][3]
        assert re.fullmatch('[a-f0-9]{64}',events[i][1]) and re.fullmatch('[a-f0-9]{64}',events[i][2])
    assert len({x[1] for x in events})==2
text=(N/'actual-closed.stdout.txt').read_text();verify(text)
detected=0
for old,new in [('sequence=2959','sequence=2960'),('|2773780','|2773781'),('VALUE:616e63686f72','VALUE:616e63686f73'),('76:2:16:1','76:2:16:2')]:
    try:verify(text.replace(old,new));raise RuntimeError('comparison failed to detect poison')
    except AssertionError:detected+=1
proof=dict(events=2,same_xid=True,begin_record_scn=records[(75,95318,16)][0],commit_record_scn=records[(76,2,372)][0],native_before_after_changes=4,complete_baseline_cells_preserved=True,differences=0,comparison_poisons_detected=detected,actual_product_recovery_verified=False)
assert detected==4
(N/'events-proof.json').write_text(json.dumps(proof,indent=2));(N/'executed-native-verifier.py').write_bytes(Path(__file__).read_bytes());print(json.dumps(proof))
