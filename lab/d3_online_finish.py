"""Finish the same two-window online proof from authenticated frozen arrivals; zero new source actions."""
from pathlib import Path
R=Path(__file__).resolve().parent.parent;N=R/'lab/out/d3-online-product-20261010'
assert (N/'source-0.started').exists() and (N/'source-1.started').exists()
assert (N/'controlled-stop.rc').read_text().strip()=='0' and (N/'build-final-driver.rc').read_text().strip()=='0'
script=(R/'lab/d3_online_product.py').read_text();prefix=script.split('try:\n assert HELPER')[0]
prefix=prefix.replace("'executed-runtime.py'","'executed-runtime-r8.py'").replace("'online-product-20261010'","'online-product-20261010-r8'").replace("'candidate.json'","'candidate-final.json'")
prefix='\n'.join(line for line in prefix.splitlines() if not line.startswith("run('probe-compile'"));prefix=prefix.replace("(N/name).mkdir()","(N/name).mkdir(exist_ok=True)")
exec(prefix)
prior_run=run
def run(name,args,data=None,private=False):return prior_run('r8-'+name,args,data,private)
props=dict(line.split('=',1) for line in (N/'runtime.secret.env').read_text().splitlines());secrets.extend(v.encode() for k,v in props.items() if 'PASSWORD' in k and v)
acquired=json.loads((N/'acquire.stdout').read_text())['data'];ACQ=int(acquired['id']);baseline=N/'snapshots'/f'{ACQ}.snapshot';assert sha(baseline)==acquired['snapshotSha256']
plan=json.loads(props['SH_ORACLE_RAW_PLAN_PLAN_JSON']);first=plan['firstSequence']
members=[line.split('=',1)[1] for line in (N/'collector.ini').read_text().splitlines() if line.startswith(('member_1=','member_2=','member_3='))]
candidate=json.loads((N/'candidate-final.json').read_text());assert sha(N/'application-final.jar')==candidate['applicationSha256']
if not (N/'application-initial.jar').exists():shutil.copyfile(N/'application.jar',N/'application-initial.jar')
shutil.copyfile(N/'application-final.jar',N/'application.jar')
cp=str(N)+os.pathsep+os.pathsep.join(map(str,(N/'libs-final').glob('*.jar')))
old_ack_files=list((N/'acks').glob('*.ack.json'));assert len(old_ack_files)==1;old_ack=old_ack_files[0].read_bytes();old_cp=json.loads(old_ack)
raws=[N/'raw-ready/raw-members'/SOURCE/(old_cp['oracleEvidence']['captures'][0]['journalSha256']+'.raw'),freeze_sequence(first+1,'next')];assert all(raws)
try:
 assert HELPER not in run('containers',['docker','ps','-a','--format','{{.Names}}']).splitlines()
 assert run('source-owner',['docker','inspect',S,'--format','{{.Config.Labels.owner}}|{{.State.Status}}']).strip()=='OracleRedoKernel|exited'
 run('source-start',['docker','start',S]);started=True
 for attempt in range(30):
  try:
   pre=sql('ready-'+str(attempt),f"alter session set container=ORKPDB;\nselect 'SOURCE|'||count(*) from ORKLAB.T_D2_BLOCKS_20261008 where ID=0 and NOTE='online-next';\nselect 'TARGET|'||count(*) from {T}_ROWS where ID=0 and NOTE='online-first';\nselect 'COMMITS|'||count(*) from {T}_COMMITS;")
   if 'SOURCE|1' in pre and 'TARGET|1' in pre and 'COMMITS|1' in pre:break
  except AssertionError:
   if attempt==29:raise
  time.sleep(5)
 start_app('final-app')
 key='raw-stream-'+str(GRANT)+'-2'
 for attempt in range(90):
  status=state('wait-'+str(attempt))
  if f'BATCH|{key}|PUBLISHED|0|1' in status:break
  if '|FAILED|' in status:raise AssertionError(status)
  time.sleep(2)
 else:raise AssertionError('frozen online continuation timeout')
 proofs=[dict(checkpoint=old_cp,cpAckSha256=hashlib.sha256(old_ack).hexdigest()),checkpoint('checkpoint-next',key)]
 assert old_ack_files[0].read_bytes()==old_ack
 final=projection('final-projection',raws);assert len(final['events'])==2 and final['pendingTransactions']==0
 actual=sql('actual',f"""alter session set container=ORKPDB;
select 'COUNTS|'||(select count(*) from {T}_APPLIED)||'|'||(select count(*) from {T}_MEMBERS)||'|'||(select count(*) from {T}_COMMITS)||'|'||(select count(*) from {U}.SH_EVENT_LEDGER)||'|'||(select count(*) from {U}.SH_PUBLISH_GATE where VERDICT='PASS') from dual;
select 'MATCH|'||count(*) from {T}_ROWS t join ORKLAB.T_D2_BLOCKS_20261008 s on s.ID=t.ID where t.TXT=s.TXT and t.NOTE=s.NOTE;
select 'EVENT|'||EVENT_ID||'|'||PAYLOAD_SHA||'|'||BEFORE_IMAGE||'|'||AFTER_IMAGE||'|'||OPERATION from {T}_APPLIED order by EVENT_ID;
select 'BINDING|'||WINDOW_KEY||'|'||REGISTERED_BY from {U}.SH_ORACLE_INPUT_BINDING where STATE='APPROVED_BOUNDED';
select 'COMMIT|'||BATCH_SHA||'|'||TARGET_ID||'|'||SCHEMA_SHA||'|'||EVENTS_COUNT from {T}_COMMITS;
select 'WINDOW|'||WINDOW_KEY||'|'||STATUS from {U}.SH_CAP_WINDOW;
select 'OLD_TARGET|'||(select count(*) from ORKTGT_CMT_20261005.SHPBL1_APPLIED)||'|'||(select count(*) from ORKTGT_CMT_20261005.SHPBL1_MEMBERS)||'|'||(select count(*) from ORKTGT_CMT_20261005.SHPBL1_COMMITS) from dual;
select 'LOGGING|'||supplemental_log_data_min||'|'||supplemental_log_data_all from v$database;
""")
 assert 'COUNTS|2|2|2|2|2' in actual and 'MATCH|20' in actual and 'OLD_TARGET|46|46|4' in actual and 'LOGGING|NO|NO' in actual
 def image(values):return ';'.join(k+'='+('NULL' if v is None else 'NUMBER:'+str(v) if isinstance(v,(int,float)) else 'VARCHAR2:'+base64.urlsafe_b64encode(v.encode()).decode().rstrip('=')) for k,v in values.items())
 expected=sorted('|'.join([event['eventId'],meta['payloadSha256'],image(event['before']),image(event['after']),event['operation']]) for event,meta in zip(final['events'],final['evidence']['events']))
 assert sorted(l[len('EVENT|'):] for l in actual.splitlines() if l.startswith('EVENT|'))==expected
 for ordinal,proof in enumerate(proofs):
  cpstate=proof['checkpoint'];producer=cpstate['producerState'];assert (producer['sourceCount'],producer['decodedThroughSequence'],producer['publishedEvents'],producer['pendingTransactions'])==(ordinal+1,first+ordinal,ordinal+1,0)
  assert len(cpstate['oracleEvidence']['events'])==1 and cpstate['oracleEvidence']['events'][0]==final['evidence']['events'][ordinal]
  assert f"COMMIT|{cpstate['oracleEvidence']['batchSha256']}|{TARGET}|{cpstate['oracleEvidence']['schemaSequenceSha256']}|1" in actual
 assert actual.count('|oracle-raw-discovery')==2
 result=dict(candidate,initialApplicationSha256=json.loads((N/'candidate.json').read_text())['applicationSha256'],agentSha256=sha(binary),sourceActions=2,newSourceActionsThisFinish=0,newAcquisitions=1,onlinePaths=members,closedSequences=[first,first+1],automaticRegistration=True,events=2,target=[2,2,2],matchedRows=20,mismatches=0,proofs=proofs,priorAckUnchanged=True,collectorRestartForHarness=True,retentionLowWatermarkVerified=False,spoolFullObserved=True,independentTargetAccepted=False)
 (N/'normal-online-proof.json').write_text(json.dumps(result,indent=2))
finally:
 try:
  if helper:stop_app('final-app')
 finally:
  if started:run('source-stop',['docker','stop','--timeout','60',S])
