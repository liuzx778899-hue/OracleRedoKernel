"""One normal frozen-input product recovery run; no source DML or acquisition."""
from pathlib import Path
import os,sys,json,hashlib,zipfile,shutil,time,secrets as random_secrets

R=Path('D:/work/vibecode/OracleRedoKernel')
label='producer-product-r109'
sys.argv=[str(R/'lab/d3_stream_runtime.py'),'inspect',label]
exec((R/'lab/d3_stream_runtime.py').read_text().split("if action=='prepare':")[0])
OLD_CTL=U
N=N/'producer-product-r109';N.mkdir(exist_ok=False)
shutil.copyfile(R/'lab/producer_recovery_product_v5.py',N/'executed.py')
U='ORKPRODUCER5CTL_20261009';T='ORKTGT_CMT_20261005.RPROD5';TARGET='oracle-recovery-RPROD5'
application=R/'lab/out/streamhouse-oracle-gate-worktree/server/streamhouse-app/target/streamhouse-app-0.1.0-SNAPSHOT.jar'
expected_kernel='66fd6b2299f3157983bc63c74b2f0957f532f3fc418cc09ca71a0214001cc385'
with zipfile.ZipFile(application) as jar:
 embedded={}
 for prefix in ['oracle-redo-kernel-','streamhouse-engine-oracle-']:
  members=[p for p in jar.namelist() if p.startswith('BOOT-INF/lib/'+prefix)]
  assert len(members)==1
  embedded[prefix]=dict(path=members[0],sha256=hashlib.sha256(jar.read(members[0])).hexdigest())
 assert embedded['oracle-redo-kernel-']['sha256']==expected_kernel
 assert embedded['streamhouse-engine-oracle-']['sha256']=='4273857ee75313ffde2b34a4b70ee2dbe9e7ee4962c28d8bfb0700d452e404d9'
 libs=N/'libs';libs.mkdir()
 for p in jar.namelist():
  if p.startswith('BOOT-INF/lib/') and Path(p).name.startswith(('oracle-redo-kernel-','streamhouse-engine-oracle-','streamhouse-spi-','jackson-')):
   (libs/Path(p).name).write_bytes(jar.read(p))
proof=dict(productCommit=run('product-commit',['git','-C',R/'lab/out/streamhouse-oracle-gate-worktree','rev-parse','HEAD']).strip(),kernelCommit='b0ffa10',applicationSha256=sha(application),embedded=embedded)
(N/'product-proof.json').write_text(json.dumps(proof,indent=2))
shutil.copyfile(application,N/'application.jar')
for folder in ['acks','prefix-state','failures']: (N/folder).mkdir()
inputs=[CAP/f'{i}.raw' if i<92 else R/f'lab/out/d3-stream-runtime-20261009/new-inputs/{i}.raw' for i in range(90,95)]
baseline=R/'lab/out/d2-blocks-runtime-20261008/snapshots/101673191742861312.snapshot'
baseline_sha='f85525d7aa20597a4f8eeaa9accd0358192bfed9175ad0ca1c52fc0f55c08f4c'
classes=N/'classes';classes.mkdir();cp=str(classes)+';'+str(libs/'*')
run('projection-compile',['D:/work/Java/jdk-17.0.7/bin/javac.exe','-cp',cp,'-d',classes,R/'lab/out/d2-blocks-runtime-20261008/product/UsageProjectionProbe.java'])
args=[baseline,baseline_sha,50359491]
for p in inputs:args.extend([p,sha(p)])
projection=json.loads(run('projection',['D:/work/Java/jdk-17.0.7/bin/java.exe','-Xmx768m','-cp',cp,'UsageProjectionProbe',*args]),parse_float=Decimal)
assert len(projection['envelopes'])==46
directory=N/'inputs';directory.mkdir()
for p in inputs:os.link(p,directory/p.name)
e=projection['evidence'];paths=['/evidence/inputs/'+p.name for p in inputs]
def clob(value):
 text=json.dumps(value,separators=(',',':'))
 return '||\n'.join("to_clob('"+text[i:i+1800].replace("'","''")+"')" for i in range(0,len(text),1800))
def image(values):
 return ';'.join(k+'='+('NULL' if v is None else 'NUMBER:'+format(v.normalize(),'f') if isinstance(v,Decimal) else 'NUMBER:'+str(v) if isinstance(v,(int,float)) else 'VARCHAR2:'+base64.urlsafe_b64encode(v.encode()).decode().rstrip('=')) for k,v in values.items())
def enroll(prefix):
 selected=inputs[:4] if prefix else inputs
 key='recovery-prefix' if prefix else 'recovery-closed';count=len(selected)
 binding=dict(acquisitionId=101673191742861312,leaf=50359491,captures=[dict(path=paths[i],sha256=sha(p),inputFormat='RAW_MEMBER') for i,p in enumerate(selected)],checkpoint=None) if prefix else dict(baselinePath=None,capturePaths=paths,evidence=e,envelopesSha256=projection['envelopesSha256'],acquisitionId=101673191742861312,recovery=dict(prefixBindingId=9801),inputFormat='RAW_MEMBER')
 hashes='\n'.join(p.name+' '+sha(p) for p in selected);bid=9801 if prefix else 9901;wid=bid+1
 sql(key+ '-enroll',f"alter session set container=ORKPDB;\ninsert into {U}.SH_ORACLE_INPUT_BINDING values({bid},'{SOURCE}','{key}',1,{count},'v3-history-consumer','{e['schemaSha256']}','{'APPROVED_PREFIX' if prefix else 'APPROVED_BOUNDED'}',"+clob(binding)+f",systimestamp,'owned-recovery-normal');\ninsert into {U}.SH_CAP_WINDOW(ID,SOURCE_ID,ENGINE_TYPE,WINDOW_KEY,ISSUE_SEQ_START,ISSUE_SEQ_END,HEAD_PATH,FROZEN,FILE_SHA256,TOTAL_SIZE,DROPPED_SEGMENTS,STATUS,REGISTERED_AT) values({wid},'{SOURCE}','oracle','{key}',1,{count},'{paths[0]}',1,'{hashes}',{sum(p.stat().st_size for p in selected)},0,'PENDING',systimestamp);\ncommit;")
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
 props.update(SPRING_DATASOURCE_USERNAME=U,SPRING_DATASOURCE_PASSWORD=password,SERVER_PORT=str(PORT),SH_WORKER_ID='59',SH_ORACLE_RAW_DISCOVERY_ENABLED='false',SH_ORACLE_RAW_PLAN_ENABLED='false',SH_PIPELINE_AUTO_CONSUME_ENABLED='true',SH_PIPELINE_AUTO_CONSUME_SOURCE_IDS=SOURCE,SH_ORACLE_PREFIX_INPUT_DIRECTORY='/evidence/inputs',SH_ORACLE_PREFIX_CHECKPOINT_DIRECTORY='/evidence/prefix-state',SH_ORACLE_BOUNDED_ACK_DIRECTORY='/evidence/acks',SH_ORACLE_BOUNDED_TARGET_ID=TARGET,SH_ORACLE_BOUNDED_TARGET_PREFIX='RPROD5',SH_PIPELINE_TARGET_APPLY_FAIL_DIR='/evidence/failures')
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
 enroll(True)
 receipt=http('checkpoint','/oracle/prefixes/9801/checkpoint',{})
 state=N/'prefix-state'/Path(receipt['checkpoint']['path']).name
 assert state.read_bytes().startswith(b'ORK_HISTORICAL_RECOVERY_V5\n') and sha(state)==receipt['checkpoint']['sha256']
 enroll(False)
 for attempt in range(60):
  status=sql(f'closed-{attempt}',f"alter session set container=ORKPDB;\nselect 'CLOSED|'||STATE||'|'||RC||'|'||EVENT_COUNT from {U}.SH_PIPELINE_BATCH where WINDOW_KEY='recovery-closed';")
  if 'CLOSED|PUBLISHED|0|46' in status:break
  if '|FAILED|' in status:raise AssertionError(status)
  time.sleep(2)
 else:raise AssertionError('normal publication timeout')
 actual=sql('actual',f"""alter session set container=ORKPDB;
select 'COUNTS|'||(select count(*) from {T}_APPLIED)||'|'||(select count(*) from {T}_MEMBERS)||'|'||(select count(*) from {T}_COMMITS)||'|'||(select count(*) from {U}.SH_EVENT_LEDGER)||'|'||(select count(*) from {U}.SH_PUBLISH_GATE where VERDICT='PASS') from dual;
select 'EVENT|'||m.ORDINAL||'|'||m.EVENT_ID||'|'||a.PAYLOAD_SHA||'|'||a.BEFORE_IMAGE||'|'||a.AFTER_IMAGE||'|'||a.OPERATION from {T}_MEMBERS m join {T}_APPLIED a on a.EVENT_ID=m.EVENT_ID order by m.ORDINAL;
select 'COMMIT|'||BATCH_SHA||'|'||TARGET_ID||'|'||SCHEMA_SHA||'|'||EVENTS_COUNT from {T}_COMMITS;
select 'ROWS|'||count(*) from {T}_ROWS where NOTE='anchor' and TXT=rpad('block-seed',100,'x');
select 'OLD_TARGET|'||(select count(*) from ORKTGT_CMT_20261005.SHPBL1_APPLIED)||'|'||(select count(*) from ORKTGT_CMT_20261005.SHPBL1_MEMBERS)||'|'||(select count(*) from ORKTGT_CMT_20261005.SHPBL1_COMMITS) from dual;
select 'PRIOR_RECOVERY|'||(select count(*) from ORKTGT_CMT_20261005.RPROD1_APPLIED)||'|'||(select count(*) from ORKTGT_CMT_20261005.RPROD1_MEMBERS)||'|'||(select count(*) from ORKTGT_CMT_20261005.RPROD1_COMMITS) from dual;
select 'SOURCE|'||count(*) from ORKLAB.T_D2_BLOCKS_20261008 where NOTE='anchor' and TXT=rpad('block-seed',100,'x');
select 'TASK|'||ID||'|'||STATE from {U}.SH_TASK;
select 'WINDOW|'||WINDOW_KEY||'|'||STATUS from {U}.SH_CAP_WINDOW;
set serveroutput on size unlimited
begin for r in (select ID,CHECKPOINT_JSON from {U}.SH_TASK where CHECKPOINT_JSON is not null) loop
for i in 0..trunc((dbms_lob.getlength(r.CHECKPOINT_JSON)-1)/1000) loop
dbms_output.put_line('CPHEX|'||r.ID||'|'||i||'|'||rawtohex(utl_raw.cast_to_raw(dbms_lob.substr(r.CHECKPOINT_JSON,1000,1+i*1000))));
end loop;end loop;end;
/
""")
 assert 'COUNTS|46|46|1|46|1' in actual and 'ROWS|20' in actual and 'OLD_TARGET|46|46|4' in actual and 'SOURCE|20' in actual and 'PRIOR_RECOVERY|46|46|1' in actual
 events=[line.split('|')[1:] for line in actual.splitlines() if line.startswith('EVENT|')]
 expected=[[str(i),event['eventId'],e['events'][i]['payloadSha256'],image(event['before']),image(event['after']),event['operation']] for i,event in enumerate(projection['envelopes'])]
 assert events==expected,'target event images/order/id/payload mismatch'
 assert f"COMMIT|{e['batchSha256']}|{TARGET}|{e.get('schemaSequenceSha256') or e['schemaSha256']}|46" in actual
 chunks={}
 for line in actual.splitlines():
  if line.startswith('CPHEX|'):
   _,task,index,data=line.split('|');chunks.setdefault(task,[]).append((int(index),data))
 checkpoints=[]
 for task,parts in chunks.items():
  parts.sort();assert [i for i,_ in parts]==list(range(len(parts)))
  raw=bytes.fromhex(''.join(data for _,data in parts));checkpoint=json.loads(raw)
  if checkpoint.get('lastWindowKey')!='recovery-closed':continue
  ack=N/'acks'/(hashlib.sha256((SOURCE+'\0recovery-closed').encode()).hexdigest()+'.ack.json')
  assert checkpoint['oracleEvidence']==e and checkpoint['sourceId']==SOURCE and ack.read_bytes()==raw
  assert f'TASK|{task}|DONE' in actual and 'WINDOW|recovery-closed|CONSUMED' in actual
  checkpoints.append(dict(taskId=task,cpAckSha256=sha(ack)))
 assert len(checkpoints)==1
 (N/'candidate-normal-published.json').write_text(json.dumps(dict(product=proof,prefix=receipt,events=46,target=[46,46,1],ledger=46,gates=1,checkpoints=checkpoints,mismatches=0,sourceDml=0,newAcquisition=0,oldTargetUnchanged=True,independentTargetAccepted=False),indent=2))
finally:
 if helper:
  run('helper-logs',['docker','logs',HELPER]);run('helper-stop',['docker','stop','-t','20',HELPER]);run('helper-remove',['docker','rm',HELPER])
 if started:run('source-stop',['docker','stop','-t','60',S])
