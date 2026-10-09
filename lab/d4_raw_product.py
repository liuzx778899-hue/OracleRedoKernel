"""One isolated RAW product run, importing the actual early acquisition record; no late acquisition or source DML."""
from pathlib import Path
import os,sys,json,hashlib,shutil,time,secrets as random_secrets
R=Path('D:/work/vibecode/OracleRedoKernel');label='d4-raw-product-20261009'
sys.argv=[str(R/'lab/d3_stream_runtime.py'),'inspect',label]
exec((R/'lab/d3_stream_runtime.py').read_text().split("if action=='prepare':")[0])
N=R/'lab/out'/label;assert not (N/'executed-product.py').exists();shutil.copyfile(__file__,N/'executed-product.py')
A=R/'lab/out/d4-raw-source-20261009';SOURCE='oracle-d4-raw';U='ORKRAWCTL_20261009';T='ORKTGT_CMT_20261005.RRAW1';TARGET='oracle-raw-RRAW1';ACQ=1239501
assert (N/'preparation.rc').read_text().strip()=='0' and (A/'resume-acquisition.rc').read_text()=='0'
assert (A/'parser-r4/normal-proof.json').exists()
prepared=json.loads((N/'preparation.stdout').read_text());e=prepared['evidence'];metadata=prepared['metadata']
# Match the product's boxed-Long JSON convention for the acquisition metadata map.
metadata['scn']=str(metadata['scn'])
reference=json.loads((A/'baseline-reference.json').read_text());assert e['baselineSha256']==reference['sha256'] and e['baselineScn']==reference['scn']==3021645
assert sha(reference['path'])==reference['sha256'] and e['captures'][0]['journalSha256']==sha(A/'95.raw')
application=R/'lab/out/d4-raw-20261009/raw-application.jar';assert sha(application)=='073c95364a9a15305527a139e846bdd6ac754839930af8cb705cfd6cab1f23c9'
shutil.copyfile(application,N/'application.jar')
for folder in ['acks','prefix-state','failures','inputs','snapshots']:(N/folder).mkdir()
os.link(A/'95.raw',N/'inputs/95.raw');shutil.copyfile(reference['path'],N/'snapshots'/f'{ACQ}.snapshot')
(N/'actual-early-acquisition-import.json').write_text(json.dumps(dict(acquisitionId=ACQ,registeredBy='actual-source-acquirer-early-record',mode='lab-import-of-real-acquisition-not-HTTP-acquire',originalCommand=str(A/'resume-acquisition.command.json'),originalRc=0,originalCommandSha256=sha(A/'resume-acquisition.command.json'),originalReceipt=reference),indent=2))
def clob(value):
 text=json.dumps(value,separators=(',',':'))
 return '||\n'.join("to_clob('"+text[i:i+1800].replace("'","''")+"')" for i in range(0,len(text),1800))
started=False;helper=False
try:
 assert HELPER not in run('containers',['docker','ps','-a','--format','{{.Names}}']).splitlines()
 assert run('source-state',['docker','inspect',S,'--format','{{.Config.Labels.owner}}|{{.State.Status}}']).strip()=='OracleRedoKernel|exited'
 run('source-start',['docker','start',S]);started=True
 for attempt in range(30):
  try:
   out=sql(f'ready-{attempt}',"select 'LOGGING|'||supplemental_log_data_min||'|'||supplemental_log_data_all from v$database;\nalter session set container=ORKPDB;\nselect 'SOURCE|'||ID||'|'||rawtohex(BIN) from ORKLAB.T_D4_RAW_20261009;")
   if 'SOURCE|' in out:break
  except AssertionError:
   if attempt==29:raise
  time.sleep(5)
 assert 'LOGGING|NO|NO' in out and 'SOURCE|1|010080FF' in out
 assert 'FREE|0' in sql('schema-free',f"alter session set container=ORKPDB;\nselect 'FREE|'||count(*) from dba_users where username='{U}';")
 password='P'+random_secrets.token_hex(12)+'a1';secrets.append(password.encode())
 sql('provision.secret',f'alter session set container=ORKPDB;\ncreate user {U} identified by "{password}" default tablespace USERS quota unlimited on USERS;\ngrant create session,create table,create sequence to {U};')
 props.update(SPRING_DATASOURCE_USERNAME=U,SPRING_DATASOURCE_PASSWORD=password,SERVER_PORT=str(PORT),SH_WORKER_ID='60',SH_ORACLE_RAW_DISCOVERY_ENABLED='false',SH_ORACLE_RAW_PLAN_ENABLED='false',SH_PIPELINE_AUTO_CONSUME_ENABLED='true',SH_PIPELINE_AUTO_CONSUME_SOURCE_IDS=SOURCE,SH_ORACLE_PREFIX_INPUT_DIRECTORY='/evidence/inputs',SH_ORACLE_PREFIX_CHECKPOINT_DIRECTORY='/evidence/prefix-state',SH_ORACLE_BOUNDED_ACK_DIRECTORY='/evidence/acks',SH_ORACLE_BOUNDED_TARGET_ID=TARGET,SH_ORACLE_BOUNDED_TARGET_PREFIX='RRAW1',SH_PIPELINE_TARGET_APPLY_FAIL_DIR='/evidence/failures',SH_ORACLE_ACQUISITION_SOURCE_ID=SOURCE,SH_ORACLE_ACQUISITION_AUTHORITY='lab-import-real-early-V3-record',SH_ORACLE_ACQUISITION_SOURCE_JSON=json.dumps(e['source'],separators=(',',':')),SH_ORACLE_ACQUISITION_OWNER='ORKLAB',SH_ORACLE_ACQUISITION_TABLE='T_D4_RAW_20261009',SH_ORACLE_ACQUISITION_INDEX='PK_D4_RAW_20261009',SH_ORACLE_ACQUISITION_DIRECTORY='/evidence/snapshots')
 (N/'runtime.secret.env').write_text(''.join(k+'='+v+'\n' for k,v in props.items()))
 run('helper-start',['docker','run','-d','--name',HELPER,'--label','owner=OracleRedoKernel','--label','scope=oracle-raw-product','--user','0','--network','container:'+S,'--workdir','/evidence','--env-file',N/'runtime.secret.env','--mount','type=bind,source='+str(N)+',target=/evidence','--mount','type=bind,source='+str(N/'snapshots')+',target=/evidence/snapshots,readonly','--entrypoint','java','flink:1.18-java17','-Xmx1536m','-jar','/evidence/application.jar']);helper=True
 for attempt in range(40):
  try:http(f'health-{attempt}','/system/info');break
  except (AssertionError,ValueError):
   if attempt==39:raise
   time.sleep(3)
 assert run('running-sha',['docker','exec',HELPER,'sha256sum','/evidence/application.jar']).split()[0]==sha(application)
 sql('early-acquisition-import',f"alter session set container=ORKPDB;\ninsert into {U}.SH_ORACLE_ACQUISITION values({ACQ},'{SOURCE}','lab-import-real-early-V3-record','{reference['sha256']}',{reference['scn']},"+clob(metadata)+",'ACQUIRED_ONLY',systimestamp,'actual-source-acquirer-early-record');\ncommit;")
 verified=http('acquisition-verified',f'/oracle/acquisitions/{ACQ}')
 assert verified['snapshotSha256']==reference['sha256'] and int(verified['snapshotScn'])==reference['scn']
 source=':'.join(str(e['source'][k]) for k in ['dbid','activationId','compatibility','thread','resetlogsId','resetlogsScn','containerUid','containerId'])
 sql('target-create',f"alter session set container=ORKPDB;\ncreate table {T}_IDENT(ID number primary key,TARGET_ID varchar2(128) not null,SOURCE_BINDING varchar2(512) not null,SCHEMA_SHA varchar2(64) not null);\ninsert into {T}_IDENT values(1,'{TARGET}','{source}','{e['schemaSha256']}');\ncreate table {T}_ROWS(ID number primary key,BIN raw(256));\ninsert into {T}_ROWS values(1,hextoraw('00FF80C328'));\ncreate table {T}_APPLIED(EVENT_ID varchar2(80) primary key,PAYLOAD_SHA varchar2(64) not null,BEFORE_IMAGE varchar2(4000),AFTER_IMAGE varchar2(4000),OPERATION varchar2(16) not null);\ncreate table {T}_MEMBERS(BATCH_SHA varchar2(64),ORDINAL number,EVENT_ID varchar2(80) references {T}_APPLIED(EVENT_ID),primary key(BATCH_SHA,ORDINAL),unique(BATCH_SHA,EVENT_ID));\ncreate table {T}_COMMITS(BATCH_SHA varchar2(64) primary key,TARGET_ID varchar2(128) not null,SCHEMA_SHA varchar2(64) not null,EVENTS_COUNT number not null);\ncommit;")
 binding=dict(baselinePath=None,capturePaths=['/evidence/inputs/95.raw'],evidence=e,envelopesSha256=prepared['envelopesSha256'],acquisitionId=ACQ,inputFormat='RAW_MEMBER')
 sql('enroll',f"alter session set container=ORKPDB;\ninsert into {U}.SH_ORACLE_INPUT_BINDING values(19501,'{SOURCE}','raw95',1,1,'v3-history-consumer','{e['schemaSha256']}','APPROVED_BOUNDED',"+clob(binding)+f",systimestamp,'approved-original-early-receipt');\ninsert into {U}.SH_CAP_WINDOW(ID,SOURCE_ID,ENGINE_TYPE,WINDOW_KEY,ISSUE_SEQ_START,ISSUE_SEQ_END,HEAD_PATH,FROZEN,FILE_SHA256,TOTAL_SIZE,DROPPED_SEGMENTS,STATUS,REGISTERED_AT) values(19502,'{SOURCE}','oracle','raw95',1,1,'/evidence/inputs/95.raw',1,'95.raw {sha(A/'95.raw')}',{(A/'95.raw').stat().st_size},0,'PENDING',systimestamp);\ncommit;")
 for attempt in range(60):
  status=sql(f'wait-{attempt}',f"alter session set container=ORKPDB;\nselect 'BATCH|'||STATE||'|'||RC||'|'||EVENT_COUNT from {U}.SH_PIPELINE_BATCH where WINDOW_KEY='raw95';")
  if 'BATCH|PUBLISHED|0|4' in status:break
  if '|FAILED|' in status:raise AssertionError(status)
  time.sleep(2)
 else:raise AssertionError('RAW normal publication timeout')
 actual=sql('actual',f"""alter session set container=ORKPDB;
select 'COUNTS|'||(select count(*) from {T}_APPLIED)||'|'||(select count(*) from {T}_MEMBERS)||'|'||(select count(*) from {T}_COMMITS)||'|'||(select count(*) from {U}.SH_EVENT_LEDGER)||'|'||(select count(*) from {U}.SH_PUBLISH_GATE where VERDICT='PASS') from dual;
select 'EVENT|'||m.ORDINAL||'|'||m.EVENT_ID||'|'||a.PAYLOAD_SHA||'|'||a.BEFORE_IMAGE||'|'||a.AFTER_IMAGE||'|'||a.OPERATION from {T}_MEMBERS m join {T}_APPLIED a on a.EVENT_ID=m.EVENT_ID order by m.ORDINAL;
select 'COMMIT|'||BATCH_SHA||'|'||TARGET_ID||'|'||SCHEMA_SHA||'|'||EVENTS_COUNT from {T}_COMMITS;
select 'ROW|'||ID||'|'||rawtohex(BIN)||'|'||dump(BIN,16) from {T}_ROWS;
select 'ROW_COUNT|'||count(*) from {T}_ROWS;
select 'SOURCE|'||ID||'|'||rawtohex(BIN) from ORKLAB.T_D4_RAW_20261009;
select 'OLD_TARGET|'||(select count(*) from ORKTGT_CMT_20261005.RPROG1_APPLIED)||'|'||(select count(*) from ORKTGT_CMT_20261005.RPROG1_MEMBERS)||'|'||(select count(*) from ORKTGT_CMT_20261005.RPROG1_COMMITS) from dual;
select 'WINDOW|'||WINDOW_KEY||'|'||STATUS from {U}.SH_CAP_WINDOW;
set serveroutput on size unlimited
begin for r in (select ID,STATE,CHECKPOINT_JSON from {U}.SH_TASK where CHECKPOINT_JSON is not null) loop
dbms_output.put_line('TASK|'||r.ID||'|'||r.STATE);
for i in 0..trunc((dbms_lob.getlength(r.CHECKPOINT_JSON)-1)/1000) loop
dbms_output.put_line('CPHEX|'||r.ID||'|'||i||'|'||rawtohex(utl_raw.cast_to_raw(dbms_lob.substr(r.CHECKPOINT_JSON,1000,1+i*1000))));
end loop;end loop;end;
/
""")
 assert 'COUNTS|4|4|1|4|1' in actual and 'ROW|1|010080FF|' in actual and 'ROW_COUNT|1' in actual and 'SOURCE|1|010080FF' in actual and 'OLD_TARGET|46|46|2' in actual and 'WINDOW|raw95|CONSUMED' in actual
 def image(values):
  if values is None:return ''
  return ';'.join(k+'='+('NULL' if v is None else 'RAW:'+v['hex'] if isinstance(v,dict) else 'NUMBER:'+format(v.normalize(),'f') if isinstance(v,Decimal) else 'NUMBER:'+str(v)) for k,v in values.items())
 numeric=json.loads((N/'preparation.stdout').read_text(),parse_float=Decimal)
 expected=[[str(i),event['eventId'],e['events'][i]['payloadSha256'],image(event['before']),image(event['after']),event['operation']] for i,event in enumerate(numeric['events'])]
 assert [line.split('|')[1:] for line in actual.splitlines() if line.startswith('EVENT|')]==expected
 assert f"COMMIT|{e['batchSha256']}|{TARGET}|{e['schemaSequenceSha256']}|4" in actual
 tasks=[line.split('|')[1:] for line in actual.splitlines() if line.startswith('TASK|')];assert len(tasks)==1 and tasks[0][1]=='DONE';task=tasks[0][0]
 parts=sorted((int(line.split('|')[2]),line.split('|')[3]) for line in actual.splitlines() if line.startswith('CPHEX|'+task+'|'));assert [i for i,_ in parts]==list(range(len(parts)))
 raw=bytes.fromhex(''.join(data for _,data in parts));checkpoint=json.loads(raw)
 ack=N/'acks'/(hashlib.sha256((SOURCE+'\0raw95').encode()).hexdigest()+'.ack.json')
 for attempt in range(30):
  if ack.exists():break
  time.sleep(1)
 assert ack.read_bytes()==raw and checkpoint['oracleEvidence']==e
 producer=checkpoint['producerState'];state=N/'prefix-state/producer'/Path(producer['path']).name
 assert state.read_bytes()==(N/'expected.state').read_bytes() and sha(state)==producer['sha256']
 assert (producer['sourceCount'],producer['decodedThroughSequence'],producer['requiredRawFirstSequence'],producer['pendingTransactions'],producer['publishedEvents'])==(1,95,95,0,4)
 (N/'normal-product-proof.json').write_text(json.dumps(dict(taskId=task,applicationSha256=sha(application),events=4,target=[4,4,1],mismatches=0,checkpoint=checkpoint,cpAckSha256=sha(ack),acquisitionMode='lab-import-of-real-early-acquisition',sourceDml=0,newAcquisition=0,independentTargetAccepted=False),indent=2))
finally:
 try:
  if helper:
   run('helper-logs',['docker','logs',HELPER]);run('helper-stop',['docker','stop','-t','20',HELPER]);run('helper-remove',['docker','rm',HELPER])
 finally:
  if started:run('source-stop',['docker','stop','-t','60',S])
