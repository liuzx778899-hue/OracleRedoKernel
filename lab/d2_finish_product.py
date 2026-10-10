"""Finish comparison after publication; no source, HTTP, restart, or target write."""
from pathlib import Path
from decimal import Decimal
import base64,hashlib,json,sys
R=Path(__file__).resolve().parent.parent;kind=sys.argv[1];assert kind in ['initial','overlap']
N=R/f'lab/out/d2-{kind}-product-20261010';assert not (N/'normal-product-proof.json').exists()
actual=(N/'actual.stdout').read_text();prepared=json.loads((N/'preparation.stdout').read_text(),parse_float=Decimal);e=prepared['evidence']
def image(values):
 if values is None:return ''
 return ';'.join(k+'='+('NULL' if v is None else 'VARCHAR2:'+base64.urlsafe_b64encode(v.encode()).decode().rstrip('=') if isinstance(v,str) else 'NUMBER:'+format(v.normalize(),'f') if isinstance(v,Decimal) else 'NUMBER:'+str(v)) for k,v in values.items())
expected=[[str(i),event['eventId'],e['events'][i]['payloadSha256'],image(event['before']),image(event['after']),event['operation']] for i,event in enumerate(prepared['events'])]
rows=[l.split('|')[1:] for l in actual.splitlines() if l.startswith('EVENT|')]
assert rows==expected, [(a,b) for row,ref in zip(rows,expected) for a,b in zip(row,ref) if a!=b]
count=3 if kind=='initial' else 5;first=86 if kind=='initial' else 88;last=first+1;source='oracle-d2-'+kind
target='oracle-raw-'+('RINIT1' if kind=='initial' else 'ROVER1')
assert f'COUNTS|{count}|{count}|1|{count}|1' in actual and f"COMMIT|{e['batchSha256']}|{target}|{e['schemaSequenceSha256']}|{count}" in actual
assert 'SOURCE|0|statistics-seed|anchor' in actual and 'ROW|0|statistics-seed|anchor|' in actual
if kind=='overlap':assert 'SOURCE|1|statistics-seed|anchor' in actual and 'ROW|1|statistics-seed|anchor|' in actual
assert 'WINDOW|d2-'+kind+'|CONSUMED' in actual
tasks=[l.split('|')[1:] for l in actual.splitlines() if l.startswith('TASK|')];assert len(tasks)==1 and tasks[0][1]=='DONE';task=tasks[0][0]
parts=sorted((int(l.split('|')[2]),l.split('|')[3]) for l in actual.splitlines() if l.startswith('CPHEX|'+task+'|'));assert [i for i,_ in parts]==list(range(len(parts)))
raw=bytes.fromhex(''.join(p for _,p in parts));checkpoint=json.loads(raw)
ack=N/'acks'/(hashlib.sha256((source+'\0d2-'+kind).encode()).hexdigest()+'.ack.json')
assert ack.read_bytes()==raw and checkpoint['oracleEvidence']==e
producer=checkpoint['producerState'];state=N/'prefix-state/producer'/Path(producer['path']).name
sha=lambda p:hashlib.sha256(Path(p).read_bytes()).hexdigest()
assert state.read_bytes()==(N/'expected.state').read_bytes() and sha(state)==producer['sha256']
assert (producer['sourceCount'],producer['decodedThroughSequence'],producer['requiredRawFirstSequence'],producer['pendingTransactions'],producer['publishedEvents'])==(2,last,first,0,count)
assert all((N/(name+'.rc')).read_text()=='0' for name in ['helper-remove','source-stop'])
(N/'normal-product-proof.json').write_text(json.dumps(dict(taskId=task,applicationSha256=sha(N/'application.jar'),events=count,target=[count,count,1],mismatches=0,checkpoint=checkpoint,cpAckSha256=sha(ack),acquisitionMode='original-trusted-HTTP-receipt-verified-again',sourceDml=0,newAcquisition=0,comparisonFailurePreserved=True,independentTargetAccepted=False),indent=2))
print(json.dumps(dict(events=count,target=[count,count,1],mismatches=0,cpAckSha256=sha(ack))))
