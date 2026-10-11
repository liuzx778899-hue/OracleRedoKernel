"""Compare raw116 events/recovery with independently acquired native rows; no source actions."""
from pathlib import Path
import hashlib,json

S=Path(__file__).resolve().parent/'out/array-update-rollback-20261011/source'
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
for label in ['projection-r5','enroll-r1','resume-r1','after','actions','source-stop']:
    assert (S/(label+'.rc')).read_text().strip()=='0',label
batch=json.loads((S/'projection-r5.stdout').read_text());events=batch['events']
restored=json.loads((S/'recovery-r1.state.events.json').read_text())
assert restored==events and len(events)==3
truth={}
for line in (S/'after.snapshot').read_text().splitlines():
    if line.startswith('ROW|'):
        f=line.split('|');key=(int(f[1]),(int(f[2])<<22)|int(f[3]),int(f[4]))
        assert key not in truth
        truth[key]=[v.lower() for v in f[5:]]
assert len(truth)==3
ids=set();eventIds=set()
for event,evidence in zip(events,batch['evidence']['events'],strict=True):
    assert event['operation']=='UPDATE' and event['transaction']['transactionId']=='oracle-xid:10.20.5217'
    assert event['metadata']['beforeComplete'] and event['metadata']['afterComplete']
    assert event['changedColumns']==['TZ','LTZ']
    row=evidence['rowKey'];key=(row['dataObjectId'],row['dba'],row['slot'])
    values=truth.pop(key);id=event['after']['ID']
    assert values[0]==bytes([193,int(id)+1]).hex() and event['before']['ID']==id
    assert values[1:]==[event['after'][c]['hex'] for c in ['TZ','LTZ']]
    assert event['after']['TZ']['zone']=='Europe/Paris' and event['after']['LTZ']['zone']=='+00:00'
    assert event['before']['TZ']['hex']=='787c031d17012e075bcd15195a'
    assert event['before']['LTZ']['hex']=='787c031d17012e075bcd15'
    assert id not in ids and event['eventId'] not in eventIds
    ids.add(id);eventIds.add(event['eventId'])
assert not truth and ids=={1,2,3}
proof=dict(rawSha256=sha(S/'inputs/116.raw'),baselineSha256=sha(S/'baseline.snapshot'),
    afterSnapshotSha256=sha(S/'after.snapshot'),checkpointSha256=sha(S/'recovery-r1.state'),
    envelopeSha256=batch['envelopesSha256'],events=3,completeRowDifferences=0,
    rolledBackEvents=0,restoredEqualsDirect=True,sourceActionsRepeated=0,
    scope='complete owned ARRAY UPDATE compensation; arbitrary partial rows remain open')
out=S/'verify-r1.json'
assert not out.exists()
out.write_text(json.dumps(proof,indent=2))
print(json.dumps(proof))
