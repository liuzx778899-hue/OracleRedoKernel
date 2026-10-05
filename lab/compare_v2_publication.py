"""Independent complete target/control/checkpoint/ACK comparison against native-checked new events."""
import base64,copy,hashlib,json
from decimal import Decimal
from pathlib import Path
R=Path(__file__).resolve().parent.parent;O=R/'lab/out/v2-dml-capture-20261006';p=json.loads((O/'implicit-tail-typed-projection.stdout.txt').read_text());e=p['evidence'];events=p['envelopes']
def image(values):
 if values is None:return ''
 fields=[]
 for c in e['columns']:
  name=c['name'];value=values[name];formatted='NULL' if value is None else ('NUMBER:'+format(Decimal(str(value)),'f') if c['type']=='NUMBER' else 'VARCHAR2:'+base64.urlsafe_b64encode(value.encode()).decode().rstrip('='));fields.append(name+'='+formatted)
 return ';'.join(fields)
def verify(text,ack):
 lines=[l.strip().split('|') for l in text.splitlines() if l.strip()];select=lambda prefix:[r for r in lines if r[0]==prefix]
 assert select('ROW')==[['ROW','0','v2-seed','anchor']]
 expected=sorted([['APPLIED',event['eventId'],proof['payloadSha256'],event['operation'],image(event['before']),image(event['after'])] for event,proof in zip(events,e['events'])]);assert select('APPLIED')==expected
 assert select('MEMBER')==[['MEMBER',e['batchSha256'],str(i),v['eventId']] for i,v in enumerate(events)]
 assert select('COMMIT')==[['COMMIT',e['batchSha256'],'oracle-dml-V2DML-20261005',e['schemaSha256'],'3']]
 assert select('IDENT')==[['IDENT','1','oracle-dml-V2DML-20261005',':'.join(str(e['source'][k]) for k in ['dbid','activationId','compatibility','thread','resetlogsId','resetlogsScn','containerUid','containerId']),e['schemaSha256']]]
 published=[r for r in select('CONTROL') if r[2]=='PUBLISHED'];refused=[r for r in select('CONTROL') if r[2]=='REFUSED'];assert len(published)==len(refused)==1
 assert published[0][3:]==[e['captures'][0]['journalSha256'],'1','1','3'];assert refused[0][3:]==[e['captures'][0]['journalSha256'],'1','1','0']
 task=(O/'task-id.txt').read_text();assert select('TASK')==[['TASK',task,'DONE']] and select('WINDOW')==[['WINDOW','CONSUMED']]
 assert select('LEDGER')==[['LEDGER',x] for x in sorted(v['eventId'] for v in events)]
 cp=json.loads(next(l.split('|',1)[1] for l in text.splitlines() if l.startswith('CHECKPOINT|')));expectedcp=dict(lastWindowKey='v2-new-DML',lastBatchId=int(published[0][1]),engine='oracle',sourceId='oracle-v2-new-DML',oracleEvidence=e,targetCommit=dict(targetId='oracle-dml-V2DML-20261005',source=e['source'],baselineSha256=e['baselineSha256'],schemaSha256=e['schemaSha256'],batchSha256=e['batchSha256'],envelopesSha256=p['envelopesSha256'],events=3));assert cp==expectedcp and json.loads(ack)==expectedcp
 assert len(select('ACQUIRED'))==1 and select('ACQUIRED')[0][1:]==[(O/'acquisition-id.txt').read_text(),'ACQUIRED_ONLY',e['baselineSha256'],str(e['baselineScn'])]
 assert select('LOGGING')==[['LOGGING','NO','NO']] and select('OLD')==[['OLD','11','blocking-closed','new-note']]
 return cp
text=(O/'actual-published.stdout.txt').read_text();acks=list((O/'acks').glob('*.ack.json'));assert len(acks)==1;ack=acks[0].read_bytes();verify(text,ack)
assert (O/'actual-repeat.stdout.txt').read_bytes()==(O/'actual-published.stdout.txt').read_bytes();assert (O/'ack-before-repeat.bin').read_bytes()==ack
rejected=(O/'rejected-before-target.stdout.txt').read_text();assert 'ROW|0|v2-seed|anchor' in rejected and 'WINDOW|PENDING' in rejected and '|FAILED' in rejected and 'CHECKPOINT|\n' in rejected
assert not any(l.startswith(('APPLIED|','MEMBER|','COMMIT|','LEDGER|')) for l in rejected.splitlines())
negatives=[]
for name,badtext,badack in [('operation',text.replace('|INSERT|','|DELETE|',1),ack),('checkpoint-source',text.replace('"dbid":1276726821','"dbid":0',1),ack),('ack',text,b'{}')]:
 (O/(name+'.derived-publication.txt')).write_text(badtext)
 try:verify(badtext,badack)
 except AssertionError:negatives.append(name)
 else:raise AssertionError('COMPARATOR_ACCEPTED_'+name)
(O/'independent-publication-proof.json').write_text(json.dumps(dict(events=3,all_target_intermediate_images_members_receipt_control_checkpoint_ack_fields_zero_differences=True,wrong_acquisition_refused_before_target=True,repeat_zero_new_events_and_exact_bytes=True,negative_comparisons=negatives,ack_sha256=hashlib.sha256(ack).hexdigest(),source_dml_replayed=False,independent_target_instance=False,historical_dictionary_accepted=False,site_accepted=False,release_accepted=False),indent=2));print('ACQUIRED_DML_ACTUAL_PRODUCT_PUBLICATION_INDEPENDENTLY_VERIFIED|3')
