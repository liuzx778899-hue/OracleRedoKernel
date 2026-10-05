"""Offline independent proof: real SQL snapshots, complete native-checked events, two SIGKILL epochs."""
import ast,base64,hashlib,json,copy
from pathlib import Path
R=Path(__file__).resolve().parent.parent;O=R/'lab/out/oracle-http-mixed-20261005';REF=R/'lab/out/oracle-typed-evidence-20261005';PREV=R/'lab/out/oracle-postcommit-proof-20261005'
tree=ast.parse((R/'lab/audit_oracle_control_publication.py').read_text());f=next(n for n in tree.body if isinstance(n,ast.FunctionDef) and n.name=='envelopes');exec(compile(ast.Module(body=[f],type_ignores=[]),'sealed-envelope-reader','exec'))
norm=lambda t:[x.strip().split('|') for x in t.splitlines() if x.strip()]
def verify(target,control,ack):
 e=json.loads((O/'approved-binding.json').read_text())['evidence'];events=envelopes('SMIX',e);lines=norm(control);row=next(x for x in lines if x[0]=='CONTROL');batch=int(row[1])
 assert norm(target)==norm((PREV/'PCMIX-final.stdout.txt').read_text().replace('oracle-dml-PCMIX-20261005','oracle-dml-HTMIX-20261005'))
 assert row[2:]==['PUBLISHED',e['captures'][0]['journalSha256'],'1','1','3']
 cptext=next(x.split('|',1)[1] for x in control.splitlines() if x.startswith('CHECKPOINT|'));cp=json.loads(cptext)
 assert cp['lastBatchId']==batch and cp['sourceId']=='oracle-http-SMIX' and cp['lastWindowKey']=='http-SMIX' and cp['engine']=='oracle';assert cp['oracleEvidence']==e
 receipt=cp['targetCommit'];assert receipt==dict(targetId='oracle-dml-HTMIX-20261005',source=e['source'],baselineSha256=e['baselineSha256'],schemaSha256=e['schemaSha256'],batchSha256=e['batchSha256'],envelopesSha256=hashlib.sha256(json.dumps(events,sort_keys=True,separators=(',',':'),ensure_ascii=False).encode()).hexdigest(),events=3)
 assert [x for x in lines if x[0]=='TASK']==[['TASK','DONE']] and [x for x in lines if x[0]=='WINDOW']==[['WINDOW','CONSUMED','1','0']] and [x for x in lines if x[0]=='GATE']==[['GATE','PASS']]
 expected=[['LEDGER',x['eventId'],'oracle-http-SMIX',x['table']['schema']+'.'+x['table']['name'],x['operation'],str(batch)] for x in events];assert [x for x in lines if x[0]=='LEDGER']==sorted(expected,key=lambda x:x[1]);assert ack==cptext.encode();return cp
if __name__=='__main__':
 t=(O/'HTMIX-final.stdout.txt').read_text();c=(O/'SMIX-control-final.stdout.txt').read_text();files=list((O/'acks').glob('*.ack.json'));assert len(files)==1;a=files[0].read_bytes();cp=verify(t,c,a)
 assert (O/'HTMIX-repeat.stdout.txt').read_bytes()==(O/'HTMIX-final.stdout.txt').read_bytes();assert (O/'SMIX-control-repeat.stdout.txt').read_bytes()==(O/'SMIX-control-final.stdout.txt').read_bytes()
 for filename,targetcount in [('actual-precommit-boundary',0),('after-precommit-crash',0),('actual-postcommit-boundary',3),('after-postcommit-crash',3)]:
  text=(O/(filename+'.stdout.txt')).read_text();assert 'TARGET|'+str(targetcount)+'|'+str(0 if targetcount==0 else 1) in text and 'WINDOW|PENDING' in text and '|RUNNING|NULL' in text and 'LEDGER|0' in text
 assert 'HTMIX_ROWS(ID,TXT,NOTE)' in (O/'actual-precommit-boundary.stdout.txt').read_text();assert 'UPDATE SH_CAP_WINDOW' in (O/'actual-postcommit-boundary.stdout.txt').read_text()
 epochs=[]
 for n in ['crash-pre-target-commit','crash-after-target-commit']:
  state=json.loads((O/(n+'.state.json')).read_text());assert state['ExitCode']==137 and not state['OOMKilled'];epochs.append(state['StartedAt'])
 assert len(set(epochs))==2;assert 'pipeline.task.done|state=DONE windows=1 published=1' in (O/'completed-async-audit.stdout.txt').read_text()
 assert json.loads((O/'get-recovered.stdout.txt').read_text())['data']['checkpointJson']==a.decode();assert json.loads((O/'repeat-done.stdout.txt').read_text())['data']['checkpointJson']==a.decode()
 negatives=[]
 for name,tt,cc,aa in [('wrong-operation',t.replace('|INSERT','|DELETE',1),c,a),('checkpoint-source',t,c.replace('"dbid":1276726821','"dbid":0',1),a),('ack-conflict',t,c,b'{}')]:
  (O/(name+'.derived-target.txt')).write_text(tt);(O/(name+'.derived-control.txt')).write_text(cc)
  try:verify(tt,cc,aa)
  except AssertionError:negatives.append(name)
  else:raise AssertionError('COMPARATOR_ACCEPTED_'+name)
 proof=dict(actual_http_mixed_dml=True,events=3,operations=['INSERT','UPDATE','DELETE'],real_sigkill_boundaries=2,exit_codes=[137,137],all_target_control_checkpoint_ack_fields_zero_differences=True,repeat_target_control_unchanged=True,ack_sha256=hashlib.sha256(a).hexdigest(),comparator_refusals=negatives,source_dml_replayed=False,independent_target_instance=False,site_accepted=False,release_accepted=False)
 (O/'independent-http-proof.json').write_text(json.dumps(proof,indent=2));print('HTTP_MIXED_AND_TWO_REAL_SIGKILL_INDEPENDENT_PROOF_VERIFIED')
