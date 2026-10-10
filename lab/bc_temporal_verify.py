"""Read-only independent truth checks for the frozen BC event, target and reacquired bytes."""
from pathlib import Path
import hashlib,json
R=Path(__file__).resolve().parent.parent;B=R/'lab/out/bc-temporal-20261011'
S=B/'source/resume-r1';V=B/'verify-r2';T=B/'target-r1'
for folder,label in [(S,'acquire'),(S,'future-update'),(S,'source-stop'),(V,'project'),(T,'target'),(T,'bc-acquire'),(T,'stop')]:
    assert (folder/(label+'.rc')).read_text().strip()=='0'
event=json.loads((V/'project.stdout').read_text())['events'][0]
assert event['operation']=='UPDATE' and event['before']['ID']==event['after']['ID']==1
assert event['changedColumns']==['D','T0','T3','T6','T9']
truth=next(l.split('|') for l in (S/'truth.stdout').read_text().splitlines() if l.startswith('ROW|'))
target=next(l.split('|') for l in (T/'actual.stdout').read_text().splitlines() if l.startswith('ROW|'))
assert target==truth
for i,name in enumerate(['D','T0','T3','T6','T9'],2):
    iso=event['after'][name]['iso'];year=-int(iso[1:5]) if iso.startswith('-') else int(iso[:4])
    text=truth[i];assert int(text[:4])==1-year and text.endswith(' BC')
    suffix=iso[5 if iso.startswith('-') else 4:].replace('T',' ')
    actual=text[4:-3];assert actual.split('.')[0]==suffix.split('.')[0]
    if name in ['T3','T6','T9']:assert int(actual.split('.')[1].ljust(9,'0'))==int(suffix.split('.')[1])
# Independent raw110/native seq110 observed after-image, not input to the production event path.
row=next(l.split('|')[5:] for l in (T/'after.snapshot').read_text().splitlines() if l.startswith('ROW|'))
assert [v.lower() for v in row]==['c102','6438030f0d2339','6438030f0d233a','6438030f0d23393ae3af00','6438030f0d23393ade6770','6463010101010100000001']
sha=lambda p:hashlib.file_digest(p.open('rb'),'sha256').hexdigest()
proof=json.loads((T/'proof.json').read_text());proof.update(sqlDifferences=0,reacquiredByteDifferences=0,
    sourceFutureUpdates=1,rawSha256=sha(S/'inputs/110.raw'),baselineSha256=sha(S/'baseline.snapshot'),envelopeSha256=sha(V/'project.stdout'))
out=B/'final-proof.json';assert not out.exists();out.write_text(json.dumps(proof,indent=2))
print(json.dumps(proof))
