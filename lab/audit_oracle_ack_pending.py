"""Independent SQL/ACK proof for committed-but-ACK-pending publication facts."""
import ast,base64,hashlib,json
from pathlib import Path
R=Path(__file__).resolve().parent.parent;O=R/'lab/out/oracle-ack-pending-20261005';REF=R/'lab/out/oracle-typed-evidence-20261005'
tree=ast.parse((R/'lab/audit_oracle_control_publication.py').read_text());f=next(n for n in tree.body if isinstance(n,ast.FunctionDef) and n.name=='envelopes');exec(compile(ast.Module(body=[f],type_ignores=[]),'sealed-full-envelope-reader','exec'))
def checkpoint(text,state):
 rows=text.splitlines();task=[x.split('|',2) for x in rows if x.startswith('TASK|')];assert len(task)==1 and task[0][1]==state
 cp=json.loads(task[0][2]);e=json.loads((O/'approved-binding.json').read_text())['evidence'];events=envelopes('SMIX',e)
 assert cp['engine']=='oracle' and cp['sourceId']=='oracle-ack-SMIX' and cp['lastWindowKey']=='ack-SMIX' and cp['oracleEvidence']==e
 assert cp['targetCommit']==dict(targetId='oracle-dml-AKMIX-20261005',source=e['source'],baselineSha256=e['baselineSha256'],schemaSha256=e['schemaSha256'],batchSha256=e['batchSha256'],envelopesSha256=hashlib.sha256(json.dumps(events,sort_keys=True,separators=(',',':'),ensure_ascii=False).encode()).hexdigest(),events=3)
 controls=[x.split('|') for x in rows if x.startswith('CONTROL|')];assert controls==[['CONTROL','PUBLISHED',str(cp['lastBatchId'])]];assert 'TARGET|3|1' in rows and 'WINDOW|CONSUMED' in rows and 'LEDGER|3' in rows
 return cp,task[0][2].encode()
def verify():
 first=(O/'first-ack-failure.stdout.txt').read_text();again=(O/'repeated-ack-failure.stdout.txt').read_text();healthy=(O/'healthy-recovery.stdout.txt').read_text();repeat=(O/'repeat-final.stdout.txt').read_text();cp,b=checkpoint(first,'FAILED');assert checkpoint(again,'FAILED')==(cp,b);assert checkpoint(healthy,'DONE')==(cp,b);assert checkpoint(repeat,'DONE')==(cp,b)
 for count,text in [(1,first),(2,again)]:
  assert text.count('AUDIT|pipeline.task.failed|state=FAILED windows=1 published=1')==count
  assert text.count('FAILCLOSED|PIPELINE_ORACLE_PUBLICATION|ORACLE_ACK_PENDING|')==count
  assert 'published=0' not in text
 ack=next((O/'acks').glob('*.ack.json'));assert ack.read_bytes()==b;assert (O/'retained-conflicting-ack.json').read_bytes()==b'{}'
 assert (O/'AKMIX-before-recovery.stdout.txt').read_bytes()==(O/'AKMIX-final.stdout.txt').read_bytes()==(O/'AKMIX-repeat.stdout.txt').read_bytes()
 norm=lambda t:[x.strip() for x in t.splitlines() if x.strip()]
 reference=(R/'lab/out/oracle-postcommit-proof-20261005/PCMIX-final.stdout.txt').read_text().replace('oracle-dml-PCMIX-20261005','oracle-dml-AKMIX-20261005');assert norm((O/'AKMIX-final.stdout.txt').read_text())==norm(reference)
 assert (O/'source-before.stdout.txt').read_bytes()==(O/'source-after.stdout.txt').read_bytes()
 return cp,hashlib.sha256(b).hexdigest()
if __name__=='__main__':
 cp,digest=verify();negatives=[];original=(O/'first-ack-failure.stdout.txt').read_text()
 for name,text in [('false-unpublished',original.replace('published=1','published=0')),('false-done',original.replace('TASK|FAILED|','TASK|DONE|')),('wrong-source',original.replace('"sourceId":"oracle-ack-SMIX"','"sourceId":"forged"'))]:
  (O/(name+'.derived-proof.txt')).write_text(text)
  try:
   checkpoint(text,'FAILED');assert 'published=0' not in text
  except AssertionError:negatives.append(name)
  else:raise AssertionError('COMPARATOR_ACCEPTED_'+name)
 proof=dict(actual_http_initial_and_empty_queue_ack_failures=2,published_count_preserved=True,task_failed_until_ack_recovered=True,healthy_recovery_and_done_repeat=True,new_batch_count=1,target_events=3,all_target_intermediate_images_and_checkpoint_ack_zero_differences=True,ack_sha256=digest,comparator_refusals=negatives,source_actions_replayed=False,site_accepted=False,release_accepted=False)
 (O/'independent-ack-proof.json').write_text(json.dumps(proof,indent=2));print('ACK_PENDING_FACTS_INDEPENDENTLY_VERIFIED|2_FAILURES|3_REFUSALS')
