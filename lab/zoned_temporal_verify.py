"""Independent SQL/timezone and reacquisition checks for the frozen raw111 complete event/JDBC chain."""
from pathlib import Path
from datetime import datetime,timedelta
import hashlib,json
R=Path(__file__).resolve().parent.parent;B=R/'lab/out/zoned-temporal-20261011';S=B/'source-r2';V=B/'verify-r2';T=B/'target-r1'
for folder,label in [(S,'acquire'),(S,'future-update'),(S,'source-stop'),(V,'project'),(T,'target'),(T,'zoned-acquire'),(T,'stop')]:
    assert (folder/(label+'.rc')).read_text().strip()=='0'
batch=json.loads((V/'project.stdout').read_text());events=batch['events'];assert len(events)==3
truth={f[1]:f[2:] for line in (S/'truth.stdout').read_text().splitlines() if line.startswith('ROW|') for f in [line.split('|')]}
actual={f[1]:f[2:] for line in (T/'actual.stdout').read_text().splitlines() if line.startswith('ROW|') for f in [line.split('|')]}
assert actual==truth and len(truth)==3
def decode(raw):
    b=bytes.fromhex(raw);year=(b[0]-100)*100+b[1]-100
    return datetime(year,b[2],b[3],b[4]-1,b[5]-1,b[6]-1),int.from_bytes(b[7:11],'big')
def civil(text):
    date,time,*rest=text.split();whole,fraction=time.split('.')
    return datetime.fromisoformat(date+'T'+whole),int(fraction),rest
expected={};ids=set()
for event in events:
    id=str(event['after']['ID']);assert id not in ids;ids.add(id)
    assert event['operation']=='UPDATE' and event['before']['ID']==event['after']['ID'] and event['changedColumns']==['TZ','LTZ']
    for name in ['TZ','LTZ']:
        assert event['before'][name]['timeZoneVersion']==event['after'][name]['timeZoneVersion']==32
        assert event['before'][name]['databaseZone']==event['after'][name]['databaseZone']=='+00:00'
    tz=event['after']['TZ'];ltz=event['after']['LTZ'];stored,nano=decode(tz['hex']);wall,truthNano,rest=civil(truth[id][0]);offset=rest[0]
    hours,minutes=map(int,offset[1:].split(':'));delta=timedelta(minutes=(hours*60+minutes)*(1 if offset[0]=='+' else -1))
    assert stored==wall-delta and nano==truthNano
    assert tz['zone'].upper()==rest[1].upper()
    local,nano=decode(ltz['hex']);wall,truthNano,_=civil(truth[id][1]);assert local==wall and nano==truthNano and ltz['zone']=='+00:00'
    expected[id]=[tz['hex'],ltz['hex']]
    assert event['before']['TZ']['hex']=='787c031d17012e075bcd15195a' and event['before']['LTZ']['hex']=='787c031d17012e075bcd15'
assert ids=={'1','2','3'} and len({e['eventId'] for e in events})==3
# Reacquired source native bytes, independent of event parser.
afterRows=[line.split('|')[5:] for line in (T/'after.snapshot').read_text().splitlines() if line.startswith('ROW|')]
for row in afterRows:
    id=str(bytes.fromhex(row[0])[1]-1);assert [h.lower() for h in row[1:]]==expected[id]
assert len(afterRows)==3
sha=lambda p:hashlib.file_digest(p.open('rb'),'sha256').hexdigest()
proof=json.loads((T/'proof.json').read_text());proof.update(regionAndCompleteRowDifferences=0,reacquiredByteDifferences=0,
    sourceFutureStatements=1,sourceFutureRows=3,rawSha256=batch['evidence']['captures'][0]['journalSha256'],baselineSha256=batch['evidence']['baselineSha256'],envelopeSha256=sha(V/'project.stdout'))
out=B/'final-proof.json';assert not out.exists();out.write_text(json.dumps(proof,indent=2));print(json.dumps(proof))
