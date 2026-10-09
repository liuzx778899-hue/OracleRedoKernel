"""One normal two-window producer publication; frozen inputs, no source DML/acquisition."""
from pathlib import Path
import os,sys,json,hashlib,zipfile,shutil,time,secrets as random_secrets

R=Path('D:/work/vibecode/OracleRedoKernel')
label='producer-progress-product-r123'
sys.argv=[str(R/'lab/d3_stream_runtime.py'),'inspect',label]
exec((R/'lab/d3_stream_runtime.py').read_text().split("if action=='prepare':")[0])
OLD_CTL=U
N=N/label;N.mkdir(exist_ok=False)
shutil.copyfile(R/'lab/producer_progress_product.py',N/'executed.py')
U='ORKPROGRESSCTL_20261009';T='ORKTGT_CMT_20261005.RPROG1';TARGET='oracle-progress-RPROG1'
application=R/'lab/out/streamhouse-oracle-gate-worktree/server/streamhouse-app/target/streamhouse-app-0.1.0-SNAPSHOT.jar'
assert sha(application)=='19725963ac6146846268f73bf35355ba63aa8d38d5fd6ae7d03cad521406e2bf'
with zipfile.ZipFile(application) as jar:
 members=[p for p in jar.namelist() if p.startswith('BOOT-INF/lib/oracle-redo-kernel-')]
 assert len(members)==1 and hashlib.sha256(jar.read(members[0])).hexdigest()=='e604ac40b5716eba704dd0536822642fd273aeced2c4f1402805a12e6c77f7e9'
proof=dict(productCommit=run('product-commit',['git','-C',R/'lab/out/streamhouse-oracle-gate-worktree','rev-parse','HEAD']).strip(),applicationSha256=sha(application))
(N/'product-proof.json').write_text(json.dumps(proof,indent=2));shutil.copyfile(application,N/'application.jar')
projection=json.loads((R/'lab/out/d3-stream-runtime-20261009/producer-progress-r116/r122-projection.stdout').read_text())
assert (R/'lab/out/d3-stream-runtime-20261009/producer-progress-r116/r122-projection.rc').read_text().strip()=='0'
windows=projection['windows'];assert [len(p['envelopes']) for p in windows]==[44,2]
for folder in ['acks','prefix-state','failures','inputs']:(N/folder).mkdir()
inputs=[CAP/f'{i}.raw' if i<92 else R/f'lab/out/d3-stream-runtime-20261009/new-inputs/{i}.raw' for i in range(90,95)]
for p in inputs:os.link(p,N/'inputs'/p.name)
paths=['/evidence/inputs/'+p.name for p in inputs]
baseline=R/'lab/out/d2-blocks-runtime-20261008/snapshots/101673191742861312.snapshot'
baseline_sha='f85525d7aa20597a4f8eeaa9accd0358192bfed9175ad0ca1c52fc0f55c08f4c'
e=windows[0]['evidence']
def clob(value):
 text=json.dumps(value,separators=(',',':'))
 return '||\n'.join("to_clob('"+text[i:i+1800].replace("'","''")+"')" for i in range(0,len(text),1800))
def enroll(index,prior=None):
 p=windows[index];selected=inputs[:4] if index==0 else inputs;key='producer-first' if index==0 else 'producer-next'
 start,end=(1,4) if index==0 else (5,5)
 binding=dict(baselinePath=None,capturePaths=paths[:len(selected)],evidence=p['evidence'],envelopesSha256=p['envelopesSha256'],acquisitionId=101673191742861312,inputFormat='RAW_MEMBER')
 if index==0:binding['committedPrefix']=True
 else:binding['publication']=dict(previousTaskId=int(prior[0]),previousBatchId=prior[1]['lastBatchId'],publishedEvents=44,prefixSha256=projection['prefixSha256'])
 hashes='\n'.join(raw.name+' '+sha(raw) for raw in selected);bid=12301+index*2
 sql(key+'-enroll',f"alter session set container=ORKPDB;\ninsert into {U}.SH_ORACLE_INPUT_BINDING values({bid},'{SOURCE}','{key}',{start},{end},'v3-history-consumer','{e['schemaSha256']}','APPROVED_BOUNDED',"+clob(binding)+f",systimestamp,'owned-producer-normal');\ninsert into {U}.SH_CAP_WINDOW(ID,SOURCE_ID,ENGINE_TYPE,WINDOW_KEY,ISSUE_SEQ_START,ISSUE_SEQ_END,HEAD_PATH,FROZEN,FILE_SHA256,TOTAL_SIZE,DROPPED_SEGMENTS,STATUS,REGISTERED_AT) values({bid+1},'{SOURCE}','oracle','{key}',{start},{end},'{paths[0]}',1,'{hashes}',{sum(raw.stat().st_size for raw in selected)},0,'PENDING',systimestamp);\ncommit;")
def published(index):
 key='producer-first' if index==0 else 'producer-next';count=44 if index==0 else 2
 for attempt in range(60):
  status=sql(f'{key}-wait-{attempt}',f"alter session set container=ORKPDB;\nselect 'BATCH|'||STATE||'|'||RC||'|'||EVENT_COUNT from {U}.SH_PIPELINE_BATCH where WINDOW_KEY='{key}';")
  if f'BATCH|PUBLISHED|0|{count}' in status:break
  if '|FAILED|' in status:raise AssertionError(status)
  time.sleep(2)
 else:raise AssertionError('normal publication timeout')
 out=sql(key+'-checkpoint',f"""alter session set container=ORKPDB;
set serveroutput on size unlimited
begin for r in (select ID,STATE,CHECKPOINT_JSON from {U}.SH_TASK where CHECKPOINT_JSON is not null and json_value(CHECKPOINT_JSON,'$.lastWindowKey')='{key}') loop
dbms_output.put_line('TASK|'||r.ID||'|'||r.STATE);
for i in 0..trunc((dbms_lob.getlength(r.CHECKPOINT_JSON)-1)/1000) loop
dbms_output.put_line('CPHEX|'||r.ID||'|'||i||'|'||rawtohex(utl_raw.cast_to_raw(dbms_lob.substr(r.CHECKPOINT_JSON,1000,1+i*1000))));
end loop;end loop;end;
/
""")
 tasks=[line.split('|')[1:] for line in out.splitlines() if line.startswith('TASK|')];assert len(tasks)==1 and tasks[0][1]=='DONE'
 task=tasks[0][0];parts=sorted((int(line.split('|')[2]),line.split('|')[3]) for line in out.splitlines() if line.startswith('CPHEX|'+task+'|'))
 assert [i for i,_ in parts]==list(range(len(parts)))
 raw=bytes.fromhex(''.join(data for _,data in parts));cp=json.loads(raw)
 ack=N/'acks'/(hashlib.sha256((SOURCE+'\0'+key).encode()).hexdigest()+'.ack.json')
 for attempt in range(30):
  if ack.exists():break
  time.sleep(1)
 assert ack.read_bytes()==raw and cp['oracleEvidence']==windows[index]['evidence']
 state=cp['producerState'];file=N/'prefix-state'/'producer'/Path(state['path']).name
 expected=R/'lab/out/d3-stream-runtime-20261009/producer-recovery-r112'/('business.state' if index==0 else 'business.state.extended')
 assert file.read_bytes()==expected.read_bytes() and sha(file)==state['sha256']
 assert (state['sourceCount'],state['decodedThroughSequence'],state['requiredRawFirstSequence'],state['pendingTransactions'],state['publishedEvents'])==((4,93,90,1,44) if index==0 else (5,94,90,0,46))
 (N/(key+'-proof.json')).write_text(json.dumps(dict(taskId=task,checkpoint=cp,cpAckSha256=sha(ack)),indent=2))
 return task,cp,ack.read_bytes()
started=False;helper=False
try:
 assert HELPER not in run('containers',['docker','ps','-a','--format','{{.Names}}']).splitlines()
 assert run('source-state',['docker','inspect',S,'--format','{{.State.Status}}']).strip()=='exited'
 run('source-start',['docker','start',S]);started=True
 for attempt in range(30):
  try:
   out=sql(f'ready-{attempt}',"select 'CURRENT|'||sequence# from v$log where status='CURRENT';\nselect 'LOGGING|'||supplemental_log_data_min||'|'||supplemental_log_data_all from v$database;\nalter session set container=ORKPDB;\nselect 'ANCHORS|'||count(*) from ORKLAB.T_D2_BLOCKS_20261008 where NOTE='anchor';")
   if 'ANCHORS|' in out:break
  except AssertionError:
   if attempt==29:raise
  time.sleep(5)
 assert 'CURRENT|95' in out and 'LOGGING|NO|NO' in out and 'ANCHORS|20' in out
 assert 'FREE|0' in sql('schema-free',f"alter session set container=ORKPDB;\nselect 'FREE|'||count(*) from dba_users where username='{U}';")
 password='P'+random_secrets.token_hex(12)+'a1';secrets.append(password.encode())
 sql('provision.secret',f'alter session set container=ORKPDB;\ncreate user {U} identified by "{password}" default tablespace USERS quota unlimited on USERS;\ngrant create session,create table,create sequence to {U};')
 props.update(SPRING_DATASOURCE_USERNAME=U,SPRING_DATASOURCE_PASSWORD=password,SERVER_PORT=str(PORT),SH_WORKER_ID='59',SH_ORACLE_RAW_DISCOVERY_ENABLED='false',SH_ORACLE_RAW_PLAN_ENABLED='false',SH_PIPELINE_AUTO_CONSUME_ENABLED='true',SH_PIPELINE_AUTO_CONSUME_SOURCE_IDS=SOURCE,SH_ORACLE_PREFIX_INPUT_DIRECTORY='/evidence/inputs',SH_ORACLE_PREFIX_CHECKPOINT_DIRECTORY='/evidence/prefix-state',SH_ORACLE_BOUNDED_ACK_DIRECTORY='/evidence/acks',SH_ORACLE_BOUNDED_TARGET_ID=TARGET,SH_ORACLE_BOUNDED_TARGET_PREFIX='RPROG1',SH_PIPELINE_TARGET_APPLY_FAIL_DIR='/evidence/failures')
 (N/'runtime.secret.env').write_text(''.join(k+'='+v+'\n' for k,v in props.items()))
 run('helper-start',['docker','run','-d','--name',HELPER,'--label','owner=OracleRedoKernel','--label','scope=oracle-product-runtime','--user','0','--network','container:'+S,'--workdir','/evidence','--env-file',N/'runtime.secret.env','--mount','type=bind,source='+str(N)+',target=/evidence','--mount','type=bind,source='+str(R)+',target=/workspace,readonly','--mount','type=bind,source='+str(baseline.parent)+',target=/evidence/snapshots,readonly','--entrypoint','java','flink:1.18-java17','-Xmx1536m','-jar','/evidence/application.jar']);helper=True
 for attempt in range(40):
  try:http(f'health-{attempt}','/system/info');break
  except (AssertionError,ValueError):
   if attempt==39:raise
   time.sleep(3)
 assert run('running-sha',['docker','exec',HELPER,'sha256sum','/evidence/application.jar']).split()[0]==proof['applicationSha256']
 sql('trusted-acquisition-copy',f"alter session set container=ORKPDB;\ninsert into {U}.SH_ORACLE_ACQUISITION select * from {OLD_CTL}.SH_ORACLE_ACQUISITION where ID=101673191742861312 and SNAPSHOT_SHA256='{baseline_sha}';\ncommit;")
 source=':'.join(str(e['source'][k]) for k in ['dbid','activationId','compatibility','thread','resetlogsId','resetlogsScn','containerUid','containerId'])
 sql('target-create',f"alter session set container=ORKPDB;\ncreate table {T}_IDENT(ID number primary key,TARGET_ID varchar2(128) not null,SOURCE_BINDING varchar2(512) not null,SCHEMA_SHA varchar2(64) not null);\ninsert into {T}_IDENT values(1,'{TARGET}','{source}','{e['schemaSha256']}');\ncreate table {T}_ROWS(ID number primary key,TXT varchar2(128),NOTE varchar2(128));\ninsert into {T}_ROWS select level-1,rpad('block-seed',100,'x'),'anchor' from dual connect by level<=20;\ncreate table {T}_APPLIED(EVENT_ID varchar2(80) primary key,PAYLOAD_SHA varchar2(64) not null,BEFORE_IMAGE varchar2(4000),AFTER_IMAGE varchar2(4000),OPERATION varchar2(16) not null);\ncreate table {T}_MEMBERS(BATCH_SHA varchar2(64),ORDINAL number,EVENT_ID varchar2(80) references {T}_APPLIED(EVENT_ID),primary key(BATCH_SHA,ORDINAL),unique(BATCH_SHA,EVENT_ID));\ncreate table {T}_COMMITS(BATCH_SHA varchar2(64) primary key,TARGET_ID varchar2(128) not null,SCHEMA_SHA varchar2(64) not null,EVENTS_COUNT number not null);\ncommit;")
 enroll(0);first=published(0);enroll(1,first);second=published(1)
 def image(values):
  return ';'.join(k+'='+('NULL' if v is None else 'NUMBER:'+format(v.normalize(),'f') if isinstance(v,Decimal) else 'NUMBER:'+str(v) if isinstance(v,(int,float)) else 'VARCHAR2:'+base64.urlsafe_b64encode(v.encode()).decode().rstrip('=')) for k in ['ID','TXT','NOTE'] for v in [values[k]])
 actual=sql('actual',f"""alter session set container=ORKPDB;
select 'COUNTS|'||(select count(*) from {T}_APPLIED)||'|'||(select count(*) from {T}_MEMBERS)||'|'||(select count(*) from {T}_COMMITS)||'|'||(select count(*) from {U}.SH_EVENT_LEDGER)||'|'||(select count(*) from {U}.SH_PUBLISH_GATE where VERDICT='PASS') from dual;
select 'EVENT|'||m.BATCH_SHA||'|'||m.ORDINAL||'|'||m.EVENT_ID||'|'||a.PAYLOAD_SHA||'|'||a.BEFORE_IMAGE||'|'||a.AFTER_IMAGE||'|'||a.OPERATION from {T}_MEMBERS m join {T}_APPLIED a on a.EVENT_ID=m.EVENT_ID order by m.BATCH_SHA,m.ORDINAL;
select 'COMMIT|'||BATCH_SHA||'|'||TARGET_ID||'|'||SCHEMA_SHA||'|'||EVENTS_COUNT from {T}_COMMITS;
select 'ROWS|'||count(*) from {T}_ROWS where NOTE='anchor' and TXT=rpad('block-seed',100,'x');
select 'SOURCE|'||count(*) from ORKLAB.T_D2_BLOCKS_20261008 where NOTE='anchor' and TXT=rpad('block-seed',100,'x');
select 'OLD_TARGET|'||(select count(*) from ORKTGT_CMT_20261005.SHPBL1_APPLIED)||'|'||(select count(*) from ORKTGT_CMT_20261005.SHPBL1_MEMBERS)||'|'||(select count(*) from ORKTGT_CMT_20261005.SHPBL1_COMMITS) from dual;
select 'PRIOR_RECOVERY|'||(select count(*) from ORKTGT_CMT_20261005.RPROD5_APPLIED)||'|'||(select count(*) from ORKTGT_CMT_20261005.RPROD5_MEMBERS)||'|'||(select count(*) from ORKTGT_CMT_20261005.RPROD5_COMMITS) from dual;
select 'WINDOW|'||WINDOW_KEY||'|'||STATUS from {U}.SH_CAP_WINDOW;
""")
 assert 'COUNTS|46|46|2|46|2' in actual and 'ROWS|20' in actual and 'SOURCE|20' in actual and 'OLD_TARGET|46|46|4' in actual and 'PRIOR_RECOVERY|46|46|1' in actual
 expected=[]
 numeric=json.loads((R/'lab/out/d3-stream-runtime-20261009/producer-progress-r116/r122-projection.stdout').read_text(),parse_float=Decimal)
 for p in numeric['windows']:
  ee=p['evidence'];assert f"COMMIT|{ee['batchSha256']}|{TARGET}|{ee['schemaSequenceSha256']}|{len(p['envelopes'])}" in actual
  expected.extend([[ee['batchSha256'],str(i),event['eventId'],ee['events'][i]['payloadSha256'],image(event['before']),image(event['after']),event['operation']] for i,event in enumerate(p['envelopes'])])
 expected.sort(key=lambda row:(row[0],int(row[1])))
 assert [line.split('|')[1:] for line in actual.splitlines() if line.startswith('EVENT|')]==expected
 for key in ['producer-first','producer-next']:assert 'WINDOW|'+key+'|CONSUMED' in actual
 assert (N/'acks'/(hashlib.sha256((SOURCE+'\0producer-first').encode()).hexdigest()+'.ack.json')).read_bytes()==first[2]
 (N/'normal-published.json').write_text(json.dumps(dict(product=proof,events=46,windows=[44,2],target=[46,46,2],mismatches=0,sourceDml=0,newAcquisition=0,priorProducerUsed=True,independentTargetAccepted=False),indent=2))
finally:
 if helper:
  run('helper-logs',['docker','logs',HELPER]);run('helper-stop',['docker','stop','-t','20',HELPER]);run('helper-remove',['docker','rm',HELPER])
 if started:run('source-stop',['docker','stop','-t','60',S])
