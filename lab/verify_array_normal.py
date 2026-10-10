"""Compare independently frozen SQL truth with raw-produced complete ARRAY events."""
from pathlib import Path
import hashlib,json
root=Path(__file__).resolve().parent.parent/'lab/out/array-normal-20261010'
source=root/'source/resume-r1';verify=root/'verify-r1'
assert (verify/'verify-r3.rc').read_text().strip()=='0'
value=json.loads((verify/'verify-r3.json').read_text())
truth=json.loads((source/'truth.json').read_text())
expected=[dict(ID=int(row[0]),TXT=row[1],NOTE=None if row[2]=='<NULL>' else row[2]) for row in truth if int(row[0])>=100]
events=value['events'];assert len(events)==20 and [e['after'] for e in events]==expected
assert len({e['eventId'] for e in events})==20
assert all(e['operation']=='INSERT' and e['before'] is None and e['metadata']['afterComplete'] for e in events)
assert len({e['transaction']['transactionId'] for e in events})==1
assert [e['transaction']['sequence'] for e in events]==list(range(1,21))
raw=source/'inputs/107.raw';raw_sha=hashlib.file_digest(raw.open('rb'),'sha256').hexdigest()
assert raw_sha==value['evidence']['captures'][0]['journalSha256']
assert all(e['beginRecordScn']>value['evidence']['baselineScn'] for e in value['evidence']['events'])
assert (root/'native-r1/stop.rc').read_text().strip()=='0'
proof=dict(rawSha256=raw_sha,baselineSha256=value['evidence']['baselineSha256'],baselineScn=value['evidence']['baselineScn'],events=20,uniqueEventIds=20,completeRowDifferences=0,sqlTruthUsedAsEventInput=False,nativeUsedAsEventInput=False,envelopesSha256=value['envelopesSha256'],sourceStopped=True,productIntegrated=False,arrayRollbackSupported=False)
(verify/'proof.json').write_text(json.dumps(proof,indent=2))
print('ARRAY_COMPLETE_EVENTS_VERIFIED|20|differences=0')
