"""Two finite normal LOB product batches. Every source change executes once."""
from pathlib import Path
import os,sys,json,hashlib,shutil,time,secrets as random_secrets,zipfile
secure_retry=sys.argv[1:]==['--resume-secure-verified'];secure_resume=secure_retry or sys.argv[1:]==['--resume-secure'];verified_resume=sys.argv[1:]==['--resume-verified'];resume=secure_resume or verified_resume or sys.argv[1:]==['--resume-acquisition'];assert resume or len(sys.argv)==1
revision='r16' if secure_retry else 'r15' if secure_resume else 'r14' if verified_resume else 'r13'
R=Path('D:/work/vibecode/OracleRedoKernel');label='d4-lob-product-20261010'+('-'+revision if resume else '')
sys.argv=[str(R/'lab/d3_stream_runtime.py'),'inspect',label]
exec((R/'lab/d3_stream_runtime.py').read_text().split("if action=='prepare':")[0])
N=R/'lab/out/d4-lob-product-20261010';execution=N/('executed-product-'+revision+'.py' if resume else 'executed-product.py');assert not execution.exists();shutil.copyfile(__file__,execution)
if resume:
 assert (N/('runtime-r15.rc' if secure_retry else 'runtime-r14.rc' if secure_resume else 'runtime-r13.rc' if verified_resume else 'runtime-r10.rc')).read_text().strip()=='1'
 assert not (N/('S-source-action.started' if secure_resume else 'B-source-action.started')).exists()
 props=dict(line.split('=',1) for line in (N/'runtime.secret.env').read_text().splitlines())
 secrets.extend(v.encode() for k,v in props.items() if 'PASSWORD' in k and v)
 prior_run=run
 def run(name,args,data=None,private=False):return prior_run(revision+'-'+name,args,data,private)
app=R/'lab/out/streamhouse-oracle-gate-worktree/server/streamhouse-app/target/streamhouse-app-0.1.0-SNAPSHOT.jar'
assert (N/('product-package-r13.rc' if resume else 'product-package-r9.rc')).read_text().strip()=='0';shutil.copyfile(app,N/'application.jar')
libs=N/('libs-'+revision if resume else 'libs');libs.mkdir()
with zipfile.ZipFile(app) as archive:
 for name in archive.namelist():
  if name.startswith('BOOT-INF/lib/') and name.endswith('.jar'):
   (libs/Path(name).name).write_bytes(archive.read(name))
candidate=json.loads((N/'candidate-r13.json').read_text()) if resume else dict(kernel='13898b1750cea9ee7691427b0b690c78a837ee5f74346abba6969bfe3a594018',adapter='a14cc2d369092b312daa068add3579db14363c24032ae958ab0b9742b4192835')
assert sha(libs/('oracle-redo-kernel-0.1.0-lob-trust-'+candidate['kernel'][:12]+'.jar'))==candidate['kernel']
assert sha(libs/('streamhouse-engine-oracle-0.1.0-lob-trust-'+candidate['adapter'][:12]+'.jar'))==candidate['adapter']
cp=os.pathsep.join(map(str,libs.glob('*.jar')))
run('prep-compile',['D:/work/Java/jdk-17.0.7/bin/javac.exe','-cp',cp,'-d',N,R/'lab/LobProductPreparationProbe.java'])
cp=str(N)+os.pathsep+cp
source=dict(dbid=1276726821,activationId=1276717861,compatibility=318767104,thread=1,resetlogsId=1245693159,resetlogsScn=1920977,containerUid=677737149,containerId=3)
reader=(R/'lab/out/dictionary-baseline-candidate-20261006/source.secret').read_text().splitlines()
assert len(reader)==3;secrets.append(reader[1].encode())
for folder in ['acks','prefix-state','failures','inputs','snapshots']:(N/folder).mkdir(exist_ok=resume)
old=R/'lab/out/d4-lob-source-20261009/continue-r2/97.raw'
if not resume:os.link(old,N/'inputs/97.raw')
def clob(value):
 text=json.dumps(value,separators=(',',':'))
 return '||\n'.join("to_clob('"+text[i:i+1800].replace("'","''")+"')" for i in range(0,len(text),1800))
started=False;helper=False
try:
 assert HELPER not in run('containers',['docker','ps','-a','--format','{{.Names}}']).splitlines()
 state=run('source-state',['docker','inspect',S,'--format','{{.Config.Labels.owner}}|{{.State.Status}}']).strip()
 assert state in ['OracleRedoKernel|running','OracleRedoKernel|exited']
 if state.endswith('|exited'):run('source-start',['docker','start',S])
 started=True
 for attempt in range(30):
  try:
   out=sql(f'ready-{attempt}',"select 'LOGGING|'||supplemental_log_data_min||'|'||supplemental_log_data_all from v$database;\nalter session set container=ORKPDB;\nselect 'READY|'||count(*) from ORKLAB.T_D4_LOBB_20261009;")
   if 'READY|1' in out:break
  except AssertionError:
   if attempt==29:raise
  time.sleep(5)
 assert 'LOGGING|NO|NO' in out
 proofs=[]
 if secure_resume:
  actual=(N/'r14-B-actual.stdout').read_text();assert all(v in actual for v in ['COUNTS|1|1|1|1','MATCH|1','WINDOW|CONSUMED','|DONE'])
  prepared=json.loads((N/'r14-B-preparation.stdout').read_text());e=prepared['evidence']
  assert f"EVENT|{e['events'][0]['eventId']}|{e['events'][0]['payloadSha256']}" in actual
  assert f"COMMIT|{e['batchSha256']}|{e['schemaSha256']}|1" in actual
  parts=sorted((int(line.split('|')[1]),line.split('|')[2]) for line in actual.splitlines() if line.startswith('CPHEX|'));assert [i for i,_ in parts]==list(range(len(parts)))
  raw=bytes.fromhex(''.join(data for _,data in parts));checkpoint=json.loads(raw);ack=N/'acks'/(hashlib.sha256(b'oracle-d4-lob-b\0lob-b').hexdigest()+'.ack.json')
  assert e['schemaSequenceSha256'] is None and e['eventSchemas']==[]
  assert ack.read_bytes()==raw and dict(checkpoint['oracleEvidence'],eventSchemas=[],schemaSequenceSha256=None)==e
  acquired=json.loads((N/'r13-B-acquire.stdout').read_text())['data']
  proof=dict(storage='B',acquisition=acquired,events=1,target=[1,1,1],mismatches=0,cpAckSha256=sha(ack),checkpoint=checkpoint,independentTargetAccepted=False)
  (N/'B-normal-proof.json').write_text(json.dumps(proof,indent=2));proofs.append(proof)
 for ordinal,kind in [(1,'S')] if secure_resume else enumerate(['B','S']):
  U='ORKLOB'+kind+'CTL_20261010';SOURCE='oracle-d4-lob-'+kind.lower();prefix='RLOB'+kind+'1';T='ORKTGT_CMT_20261005.'+prefix;TARGET='oracle-lob-'+prefix;table='T_D4_LOB'+kind+'_20261009'
  if resume and kind=='B':password=props['SPRING_DATASOURCE_PASSWORD']
  else:
   assert 'FREE|0' in sql(kind+'-free',f"alter session set container=ORKPDB;\nselect 'FREE|'||count(*) from dba_users where username='{U}';")
   password='P'+random_secrets.token_hex(12)+'a1';secrets.append(password.encode())
   sql(kind+'-provision.secret',f'alter session set container=ORKPDB;\ncreate user {U} identified by "{password}" default tablespace USERS quota unlimited on USERS;\ngrant create session,create table,create sequence to {U};')
  props.update(SPRING_DATASOURCE_USERNAME=U,SPRING_DATASOURCE_PASSWORD=password,SERVER_PORT=str(PORT),SH_WORKER_ID=str(61+ordinal),SH_ORACLE_RAW_DISCOVERY_ENABLED='false',SH_ORACLE_RAW_PLAN_ENABLED='false',SH_PIPELINE_AUTO_CONSUME_ENABLED='true',SH_PIPELINE_AUTO_CONSUME_SOURCE_IDS=SOURCE,SH_ORACLE_PREFIX_INPUT_DIRECTORY='/evidence/inputs',SH_ORACLE_PREFIX_CHECKPOINT_DIRECTORY='/evidence/prefix-state',SH_ORACLE_BOUNDED_ACK_DIRECTORY='/evidence/acks',SH_ORACLE_BOUNDED_TARGET_ID=TARGET,SH_ORACLE_BOUNDED_TARGET_PREFIX=prefix,SH_PIPELINE_TARGET_APPLY_FAIL_DIR='/evidence/failures',SH_ORACLE_ACQUISITION_ENABLED='true',SH_ORACLE_ACQUISITION_SOURCE_ID=SOURCE,SH_ORACLE_ACQUISITION_AUTHORITY='owned-LOB-normal-acquisition',SH_ORACLE_ACQUISITION_SOURCE_JSON=json.dumps(source,separators=(',',':')),SH_ORACLE_ACQUISITION_OWNER='ORKLAB',SH_ORACLE_ACQUISITION_TABLE=table,SH_ORACLE_ACQUISITION_INDEX='PK_'+table[2:],SH_ORACLE_ACQUISITION_DIRECTORY='/evidence/snapshots',SH_ORACLE_ACQUISITION_USER=reader[0],SH_ORACLE_ACQUISITION_PASSWORD=reader[1],SH_ORACLE_ACQUISITION_URL=reader[2])
  (N/'runtime.secret.env').write_text(''.join(k+'='+v+'\n' for k,v in props.items()))
  run(kind+'-helper-start',['docker','run','-d','--name',HELPER,'--label','owner=OracleRedoKernel','--label','scope=oracle-lob-product','--user','0','--network','container:'+S,'--workdir','/evidence','--env-file',N/'runtime.secret.env','--mount','type=bind,source='+str(N)+',target=/evidence','--entrypoint','java','flink:1.18-java17','-Xmx1536m','-jar','/evidence/application.jar']);helper=True
  for attempt in range(40):
   try:http(f'{kind}-health-{attempt}','/system/info');break
   except (AssertionError,ValueError):
    if attempt==39:raise
    time.sleep(3)
  # Finish owned target/control segment initialization before the static-schema baseline.
  if not (verified_resume and kind=='B'):sql(kind+'-target',f"alter session set container=ORKPDB;\ncreate table {T}_ROWS(ID number primary key,B blob,C clob);\ninsert into {T}_ROWS select ID,B,C from ORKLAB.{table};\nalter table {U}.SH_ORACLE_ACQUISITION allocate extent;\ncommit;")
  acquired=json.loads((N/'r13-B-acquire.stdout').read_text())['data'] if verified_resume and kind=='B' else http(kind+'-acquire','/oracle/acquisitions/historical',dict(sourceId=SOURCE));ACQ=int(acquired['id'])
  verified=http(kind+'-verified',f'/oracle/acquisitions/{ACQ}');assert {k:v for k,v in verified.items() if k!='registeredAt'}=={k:v for k,v in acquired.items() if k!='registeredAt'}
  baseline=N/'snapshots'/f'{ACQ}.snapshot';assert sha(baseline)==acquired['snapshotSha256']
  assert 'ANCHOR|1' in sql(kind+'-target-anchor',f"alter session set container=ORKPDB;\nselect 'ANCHOR|'||count(*) from {T}_ROWS t join ORKLAB.{table} as of scn {acquired['snapshotScn']} s on s.ID=t.ID where dbms_lob.compare(t.B,s.B)=0 and dbms_lob.getlength(t.B)=dbms_lob.getlength(s.B) and dbms_lob.compare(t.C,s.C)=0 and dbms_lob.getlength(t.C)=dbms_lob.getlength(s.C);")
  (N/(kind+'-source-action.started')).write_text('one new post-baseline UPDATE; never replay\n')
  sql(kind+'-source-action',f"alter session set container=ORKPDB;\nbegin update ORKLAB.{table} set B=hextoraw('0010FF80C328A5'),C=to_clob(unistr('\\6536\\5C3E-20261010')) where ID=1; if sql%rowcount<>1 then raise_application_error(-20001,'ROW_COUNT');end if;end;\n/\ncommit;")
  sql(kind+'-archive','alter system archive log current;')
  paths=sql(kind+'-archive-paths',"select 'PATH|'||sequence#||'|'||name from v$archived_log where sequence#>=98 and archived='YES' and deleted='NO' and name is not null order by sequence#,recid;")
  byseq={int(line.split('|')[1]):line.split('|')[2] for line in paths.splitlines() if line.startswith('PATH|')}
  assert list(sorted(byseq))==list(range(98,max(byseq)+1))
  for seq,path in sorted(byseq.items()):
   local=N/'inputs'/f'{seq}.raw'
   if not local.exists():run(f'copy-{seq}',['docker','cp',S+':'+path,local])
  captures=[N/'inputs'/f'{seq}.raw' for seq in range(97,max(byseq)+1)]
  prepared=json.loads(run(kind+'-preparation',['D:/work/Java/jdk-17.0.7/bin/java.exe','-Xmx1536m','-cp',cp,'LobProductPreparationProbe',baseline,acquired['snapshotSha256'],*captures]));e=prepared['evidence'];assert len(e['events'])==1
  identity=':'.join(str(e['source'][k]) for k in ['dbid','activationId','compatibility','thread','resetlogsId','resetlogsScn','containerUid','containerId'])
  sql(kind+'-target-ledger',f"alter session set container=ORKPDB;\ncreate table {T}_IDENT(ID number primary key,TARGET_ID varchar2(128) not null,SOURCE_BINDING varchar2(512) not null,SCHEMA_SHA varchar2(64) not null);\ninsert into {T}_IDENT values(1,'{TARGET}','{identity}','{e['schemaSha256']}');\ncreate table {T}_APPLIED(EVENT_ID varchar2(80) primary key,PAYLOAD_SHA varchar2(64) not null,BEFORE_IMAGE varchar2(4000),AFTER_IMAGE varchar2(4000),OPERATION varchar2(16) not null);\ncreate table {T}_MEMBERS(BATCH_SHA varchar2(64),ORDINAL number,EVENT_ID varchar2(80) references {T}_APPLIED(EVENT_ID),primary key(BATCH_SHA,ORDINAL),unique(BATCH_SHA,EVENT_ID));\ncreate table {T}_COMMITS(BATCH_SHA varchar2(64) primary key,TARGET_ID varchar2(128) not null,SCHEMA_SHA varchar2(64) not null,EVENTS_COUNT number not null);\ncommit;")
  window='lob-'+kind.lower();binding=dict(baselinePath=None,capturePaths=['/evidence/inputs/'+p.name for p in captures],evidence=e,envelopesSha256=prepared['envelopesSha256'],acquisitionId=ACQ,inputFormat='RAW_MEMBER')
  hashes='\n'.join(p.name+' '+sha(p) for p in captures)
  sql(kind+'-enroll',f"alter session set container=ORKPDB;\ninsert into {U}.SH_ORACLE_INPUT_BINDING values(19601,'{SOURCE}','{window}',1,{len(captures)},'v3-history-consumer','{e['schemaSha256']}','APPROVED_BOUNDED',"+clob(binding)+f",systimestamp,'owned-normal-LOB');\ninsert into {U}.SH_CAP_WINDOW(ID,SOURCE_ID,ENGINE_TYPE,WINDOW_KEY,ISSUE_SEQ_START,ISSUE_SEQ_END,HEAD_PATH,FROZEN,FILE_SHA256,TOTAL_SIZE,DROPPED_SEGMENTS,STATUS,REGISTERED_AT) values(19602,'{SOURCE}','oracle','{window}',1,{len(captures)},'/evidence/inputs/97.raw',1,'{hashes}',{sum(p.stat().st_size for p in captures)},0,'PENDING',systimestamp);\ncommit;")
  for attempt in range(60):
   status=sql(f'{kind}-wait-{attempt}',f"alter session set container=ORKPDB;\nselect 'BATCH|'||STATE||'|'||RC||'|'||EVENT_COUNT from {U}.SH_PIPELINE_BATCH where WINDOW_KEY='{window}';")
   if 'BATCH|PUBLISHED|0|1' in status:break
   if 'FAILED' in status:raise AssertionError(status)
   time.sleep(2)
  else:raise AssertionError('LOB publication timeout')
  actual=sql(kind+'-actual',f"""alter session set container=ORKPDB;
select 'COUNTS|'||(select count(*) from {T}_APPLIED)||'|'||(select count(*) from {T}_MEMBERS)||'|'||(select count(*) from {T}_COMMITS)||'|'||(select count(*) from {U}.SH_EVENT_LEDGER) from dual;
select 'MATCH|'||count(*) from {T}_ROWS t join ORKLAB.{table} s on s.ID=t.ID where dbms_lob.getlength(t.B)=dbms_lob.getlength(s.B) and dbms_lob.compare(t.B,s.B)=0 and dbms_lob.getlength(t.C)=dbms_lob.getlength(s.C) and dbms_lob.compare(t.C,s.C)=0;
select 'EVENT|'||EVENT_ID||'|'||PAYLOAD_SHA from {T}_APPLIED;
select 'COMMIT|'||BATCH_SHA||'|'||SCHEMA_SHA||'|'||EVENTS_COUNT from {T}_COMMITS;
select 'WINDOW|'||STATUS from {U}.SH_CAP_WINDOW;
set serveroutput on size unlimited
begin for r in (select ID,STATE,CHECKPOINT_JSON from {U}.SH_TASK where CHECKPOINT_JSON is not null) loop
dbms_output.put_line('TASK|'||r.ID||'|'||r.STATE);
for i in 0..trunc((dbms_lob.getlength(r.CHECKPOINT_JSON)-1)/1000) loop dbms_output.put_line('CPHEX|'||i||'|'||rawtohex(utl_raw.cast_to_raw(dbms_lob.substr(r.CHECKPOINT_JSON,1000,1+i*1000))));end loop;end loop;end;
/
""")
  assert 'COUNTS|1|1|1|1' in actual and 'MATCH|1' in actual and 'WINDOW|CONSUMED' in actual
  assert f"EVENT|{e['events'][0]['eventId']}|{e['events'][0]['payloadSha256']}" in actual
  assert f"COMMIT|{e['batchSha256']}|{e['schemaSha256']}|1" in actual
  tasks=[line.split('|')[1:] for line in actual.splitlines() if line.startswith('TASK|')];assert len(tasks)==1 and tasks[0][1]=='DONE'
  parts=sorted((int(line.split('|')[1]),line.split('|')[2]) for line in actual.splitlines() if line.startswith('CPHEX|'));assert [i for i,_ in parts]==list(range(len(parts)))
  raw=bytes.fromhex(''.join(data for _,data in parts));checkpoint=json.loads(raw)
  ack=N/'acks'/(hashlib.sha256((SOURCE+'\0'+window).encode()).hexdigest()+'.ack.json')
  for attempt in range(30):
   if ack.exists():break
   time.sleep(1)
  assert e['schemaSequenceSha256'] is None and e['eventSchemas']==[]
  assert ack.read_bytes()==raw and dict(checkpoint['oracleEvidence'],eventSchemas=[],schemaSequenceSha256=None)==e
  proof=dict(storage=kind,acquisition=acquired,events=1,target=[1,1,1],mismatches=0,cpAckSha256=sha(ack),checkpoint=checkpoint,independentTargetAccepted=False)
  (N/(kind+'-normal-proof.json')).write_text(json.dumps(proof,indent=2));proofs.append(proof)
  run(kind+'-helper-logs',['docker','logs',HELPER]);run(kind+'-helper-stop',['docker','stop','-t','20',HELPER]);run(kind+'-helper-remove',['docker','rm',HELPER]);helper=False
 (N/'normal-product-proof.json').write_text(json.dumps(dict(applicationSha256=sha(app),storageProofs=proofs,sourceDml=2,oldEventsPublished=0,mode='authenticated-HTTP-acquisition-static-bounded',independentTargetAccepted=False),indent=2))
finally:
 try:
  if helper:
   run('failure-helper-logs',['docker','logs',HELPER]);run('failure-helper-stop',['docker','stop','-t','20',HELPER]);run('failure-helper-remove',['docker','rm',HELPER])
 finally:
  if started:run('source-stop',['docker','stop','-t','60',S])
