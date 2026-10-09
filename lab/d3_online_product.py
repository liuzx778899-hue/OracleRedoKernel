"""One normal owned online-member run, two switches; source actions never replay."""
from pathlib import Path
import os,sys,json,hashlib,shutil,time,secrets as random_secrets,subprocess,struct
R=Path(__file__).resolve().parent.parent
sys.argv=[str(R/'lab/d3_stream_runtime.py'),'inspect','online-product-20261010']
exec((R/'lab/d3_stream_runtime.py').read_text().split("if action=='prepare':")[0])
N=R/'lab/out/d3-online-product-20261010';U='ORKONLINECTL_20261010';SOURCE='oracle-online-normal';GRANT=21001
T='ORKTGT_CMT_20261005.RONL1';TARGET='oracle-online-RONL1';PORT=37301
assert not (N/'executed-runtime.py').exists();shutil.copyfile(__file__,N/'executed-runtime.py')
assert (N/'build-driver-r2.rc').read_text().strip()=='0'
candidate=json.loads((N/'candidate.json').read_text());assert sha(N/'application.jar')==candidate['applicationSha256']
binary=R/'lab/out/d3-stream-runtime-20261009/sh_agent'
assert sha(binary)=='f477b4826a9abd07667a325b2bb37303b1cf0cb8a70249be3b371966741378f2'
shutil.copyfile(binary,N/'sh_agent');cp=os.pathsep.join(map(str,(N/'libs').glob('*.jar')))
run('probe-compile',['D:/work/Java/jdk-17.0.7/bin/javac.exe','-cp',cp,'-d',N,R/'lab/OnlineProductProbe.java'])
cp=str(N)+os.pathsep+cp
reader=(R/'lab/out/dictionary-baseline-candidate-20261006/source.secret').read_text().splitlines();assert len(reader)==3;secrets.append(reader[1].encode())
source=dict(dbid=1276726821,activationId=1276717861,compatibility=318767104,thread=1,resetlogsId=1245693159,resetlogsScn=1920977,containerUid=677737149,containerId=3)
for name in ['acks','prefix-state','failures','snapshots']:(N/name).mkdir()
REMOTE='/tmp/ork-online-product-20261010';started=False;helper=False;collector=None
def start_app(label):
 global helper
 (N/'runtime.secret.env').write_text(''.join(k+'='+v+'\n' for k,v in props.items()))
 run(label+'-start',['docker','run','-d','--name',HELPER,'--label','owner=OracleRedoKernel','--label','scope=oracle-online-product','--user','0','--network','container:'+S,'--workdir','/evidence','--env-file',N/'runtime.secret.env','--mount','type=bind,source='+str(N)+',target=/evidence','--entrypoint','java','flink:1.18-java17','-Xmx1536m','-jar','/evidence/application.jar']);helper=True
 for attempt in range(40):
  try:http(label+'-health-'+str(attempt),'/system/info');break
  except (AssertionError,ValueError):
   if attempt==39:raise
   time.sleep(3)
 assert run(label+'-running-sha',['docker','exec',HELPER,'sha256sum','/evidence/application.jar']).split()[0]==candidate['applicationSha256']
def stop_app(label):
 global helper
 run(label+'-logs',['docker','logs',HELPER]);run(label+'-stop',['docker','stop','--timeout','30',HELPER]);run(label+'-remove',['docker','rm',HELPER]);helper=False
def clob(value):
 text=json.dumps(value,separators=(',',':'));return '||\n'.join("to_clob('"+text[i:i+1800].replace("'","''")+"')" for i in range(0,len(text),1800))
def state(label):
 return sql(label,f"alter session set container=ORKPDB;\nselect 'WINDOW|'||WINDOW_KEY||'|'||STATUS from {U}.SH_CAP_WINDOW;\nselect 'BATCH|'||WINDOW_KEY||'|'||STATE||'|'||RC||'|'||EVENT_COUNT from {U}.SH_PIPELINE_BATCH;\nselect 'TRANSFER|'||STATUS||'|'||FILE_NAME from {U}.SH_TRANSFER_SESSION where SOURCE_ID='{SOURCE}';\nselect 'FAIL|'||REASON||'|'||DETAIL from {U}.SH_FAILCLOSED_RECORD;")
def closed_files():
 return sorted((N/'raw-ready/raw-members'/SOURCE).glob('*.raw'))
def freeze_sequence(sequence,label):
 # Read only the authenticated transport outputs, never an archive or dump as event input.
 paths=[]
 for p in closed_files():
  with p.open('rb') as f:f.seek(512);h=f.read(512)
  if struct.unpack_from('<I',h,8)[0]==sequence and struct.unpack_from('<I',h,156)[0]!=0xffffffff:paths.append(p)
 assert len(paths)<=1,paths
 return paths[0] if paths else None
def projection(label,inputs):
 return json.loads(run(label,['D:/work/Java/jdk-17.0.7/bin/java.exe','-Xmx1536m','-cp',cp,'OnlineProductProbe',baseline,acquired['snapshotSha256'],'50359491',*inputs]))
def checkpoint(label,key):
 out=sql(label,f"""alter session set container=ORKPDB;
set serveroutput on size unlimited
begin for r in (select ID,STATE,CHECKPOINT_JSON from {U}.SH_TASK where json_value(CHECKPOINT_JSON,'$.lastWindowKey')='{key}') loop
dbms_output.put_line('TASK|'||r.ID||'|'||r.STATE);
for i in 0..trunc((dbms_lob.getlength(r.CHECKPOINT_JSON)-1)/1000) loop dbms_output.put_line('CPHEX|'||i||'|'||rawtohex(utl_raw.cast_to_raw(dbms_lob.substr(r.CHECKPOINT_JSON,1000,1+i*1000))));end loop;end loop;end;
/
""")
 tasks=[l.split('|')[1:] for l in out.splitlines() if l.startswith('TASK|')];assert len(tasks)==1 and tasks[0][1]=='DONE'
 parts=sorted((int(l.split('|')[1]),l.split('|')[2]) for l in out.splitlines() if l.startswith('CPHEX|'));assert [i for i,_ in parts]==list(range(len(parts)))
 raw=bytes.fromhex(''.join(data for _,data in parts));cpstate=json.loads(raw);ack=N/'acks'/(hashlib.sha256((SOURCE+'\0'+key).encode()).hexdigest()+'.ack.json')
 for attempt in range(30):
  if ack.exists():break
  time.sleep(1)
 assert ack.read_bytes()==raw
 producer=cpstate['producerState'];file=N/'prefix-state/producer'/Path(producer['path']).name;assert sha(file)==producer['sha256']
 return dict(taskId=tasks[0][0],checkpoint=cpstate,cpAckSha256=sha(ack))
try:
 assert HELPER not in run('containers',['docker','ps','-a','--format','{{.Names}}']).splitlines()
 assert run('source-state',['docker','inspect',S,'--format','{{.Config.Labels.owner}}|{{.State.Status}}']).strip()=='OracleRedoKernel|exited'
 run('source-start',['docker','start',S]);started=True
 for attempt in range(30):
  try:
   pre=sql('ready-'+str(attempt),"select 'CURRENT|'||sequence# from v$log where status='CURRENT';\nselect 'MEMBER|'||member from v$logfile order by group#;\nselect 'LOGGING|'||supplemental_log_data_min||'|'||supplemental_log_data_all from v$database;\nalter session set container=ORKPDB;\nselect 'SEED|'||count(*) from ORKLAB.T_D2_BLOCKS_20261008 where NOTE='anchor';\nselect 'INDEX|'||index_name from dba_indexes where owner='ORKLAB' and table_name='T_D2_BLOCKS_20261008';")
   if 'SEED|20' in pre:break
  except AssertionError:
   if attempt==29:raise
  time.sleep(5)
 assert 'LOGGING|NO|NO' in pre
 first=int(next(l.split('|')[1] for l in pre.splitlines() if l.startswith('CURRENT|')));members=[l.split('|')[1] for l in pre.splitlines() if l.startswith('MEMBER|')];assert len(members)==3
 index=next(l.split('|')[1] for l in pre.splitlines() if l.startswith('INDEX|'))
 assert 'FREE|0|0' in sql('schema-free',f"alter session set container=ORKPDB;\nselect 'FREE|'||(select count(*) from dba_users where username='{U}')||'|'||(select count(*) from dba_tables where owner='ORKTGT_CMT_20261005' and table_name like 'RONL1%') from dual;")
 password='P'+random_secrets.token_hex(12)+'a1';secrets.append(password.encode())
 sql('provision.secret',f'alter session set container=ORKPDB;\ncreate user {U} identified by "{password}" default tablespace USERS quota unlimited on USERS;\ngrant create session,create table,create sequence to {U};')
 props.update(SPRING_DATASOURCE_USERNAME=U,SPRING_DATASOURCE_PASSWORD=password,SERVER_PORT=str(PORT),SH_WORKER_ID='63',SH_ORACLE_RAW_DISCOVERY_ENABLED='false',SH_ORACLE_RAW_PLAN_ENABLED='false',SH_PIPELINE_AUTO_CONSUME_ENABLED='true',SH_PIPELINE_AUTO_CONSUME_SOURCE_IDS=SOURCE,SH_ORACLE_PREFIX_INPUT_DIRECTORY='/evidence/raw-ready/raw-members/'+SOURCE,SH_ORACLE_PREFIX_CHECKPOINT_DIRECTORY='/evidence/prefix-state',SH_ORACLE_BOUNDED_ACK_DIRECTORY='/evidence/acks',SH_ORACLE_BOUNDED_TARGET_ID=TARGET,SH_ORACLE_BOUNDED_TARGET_PREFIX='RONL1',SH_PIPELINE_TARGET_APPLY_FAIL_DIR='/evidence/failures',SH_GATEWAY_STAGING_DIR='/evidence/staging',SH_GATEWAY_READY_ROOT_DIR='/evidence/raw-ready',SH_AUTH_MACHINE_TOKEN_REQUIRED='true',SH_ORACLE_ACQUISITION_ENABLED='true',SH_ORACLE_ACQUISITION_SOURCE_ID=SOURCE,SH_ORACLE_ACQUISITION_AUTHORITY='owned-online-normal',SH_ORACLE_ACQUISITION_SOURCE_JSON=json.dumps(source,separators=(',',':')),SH_ORACLE_ACQUISITION_OWNER='ORKLAB',SH_ORACLE_ACQUISITION_TABLE='T_D2_BLOCKS_20261008',SH_ORACLE_ACQUISITION_INDEX=index,SH_ORACLE_ACQUISITION_DIRECTORY='/evidence/snapshots',SH_ORACLE_ACQUISITION_USER=reader[0],SH_ORACLE_ACQUISITION_PASSWORD=reader[1],SH_ORACLE_ACQUISITION_URL=reader[2])
 start_app('acquisition-app')
 sql('target-create',f"alter session set container=ORKPDB;\ncreate table {T}_IDENT(ID number primary key,TARGET_ID varchar2(128) not null,SOURCE_BINDING varchar2(512) not null,SCHEMA_SHA varchar2(64) not null);\ncreate table {T}_ROWS(ID number primary key,TXT varchar2(128),NOTE varchar2(128));\ninsert into {T}_ROWS select ID,TXT,NOTE from ORKLAB.T_D2_BLOCKS_20261008;\ncreate table {T}_APPLIED(EVENT_ID varchar2(80) primary key,PAYLOAD_SHA varchar2(64) not null,BEFORE_IMAGE varchar2(4000),AFTER_IMAGE varchar2(4000),OPERATION varchar2(16) not null);\ncreate table {T}_MEMBERS(BATCH_SHA varchar2(64),ORDINAL number,EVENT_ID varchar2(80) references {T}_APPLIED(EVENT_ID),primary key(BATCH_SHA,ORDINAL),unique(BATCH_SHA,EVENT_ID));\ncreate table {T}_COMMITS(BATCH_SHA varchar2(64) primary key,TARGET_ID varchar2(128) not null,SCHEMA_SHA varchar2(64) not null,EVENTS_COUNT number not null);\nalter table {U}.SH_ORACLE_ACQUISITION allocate extent;\ncommit;")
 acquired=http('acquire','/oracle/acquisitions/historical',dict(sourceId=SOURCE));ACQ=int(acquired['id']);baseline=N/'snapshots'/f'{ACQ}.snapshot';assert sha(baseline)==acquired['snapshotSha256']
 assert 'ANCHOR|20' in sql('target-anchor',f"alter session set container=ORKPDB;\nselect 'ANCHOR|'||count(*) from {T}_ROWS t join ORKLAB.T_D2_BLOCKS_20261008 as of scn {acquired['snapshotScn']} s on t.ID=s.ID where t.TXT=s.TXT and t.NOTE=s.NOTE;")
 stop_app('acquisition-app')
 plan=dict(sourceId=SOURCE,transportSourceId=SOURCE,acquisitionId=ACQ,firstSequence=first,leaf=50359491,sessions=[1])
 props.update(SH_ORACLE_RAW_PLAN_ENABLED='true',SH_ORACLE_RAW_PLAN_PLAN_JSON=json.dumps(plan,separators=(',',':')),SH_ORACLE_RAW_PLAN_REGISTRATION_ENABLED='false',SH_ORACLE_RAW_DISCOVERY_ENABLED='true',SH_ORACLE_RAW_DISCOVERY_GRANT_ID=str(GRANT),SH_ORACLE_RAW_DISCOVERY_INTERVAL_MS='5000')
 start_app('online-app')
 machine=http('machine-register','/machines',dict(name='ORK-ONLINE-NORMAL',sourceId=SOURCE,engineType='oracle',host='127.0.0.1'))
 token=http('machine-token','/machines/'+str(machine['id'])+'/agent-token/rotate',{},True)['agentToken'];secrets.append(token.encode());(N/'machine.secret.env').write_bytes(('SH_AGENT_MACHINE_TOKEN='+token+'\n').encode())
 config='[agent]\nsource_id='+SOURCE+'\n[oracle]\nmember_count=3\n'+''.join('member_'+str(i+1)+'='+path+'\n' for i,path in enumerate(members))+'spool_dir='+REMOTE+'/spool\npoll_interval_ms=15000\nmax_spool_bytes=2147483647\n[transfer]\nbase_url=http://127.0.0.1:'+str(PORT)+'\n'
 (N/'collector.ini').write_text(config);(N/'run-collector.sh').write_bytes(('#!/bin/sh\nset -eu\nset -a\n. '+REMOTE+'/machine.secret.env\nset +a\necho $$ > '+REMOTE+'/collector.pid\nexec '+REMOTE+'/sh_agent --collect-oracle '+REMOTE+'/collector.ini\n').encode())
 run('collector-dir',['docker','exec',S,'mkdir',REMOTE])
 for name in ['sh_agent','collector.ini','machine.secret.env','run-collector.sh']:run('collector-copy-'+name.replace('.','-'),['docker','cp',N/name,S+':'+REMOTE+'/'+name])
 run('collector-owner',['docker','exec','--user','0',S,'chown','oracle:oinstall',*[REMOTE+'/'+name for name in ['sh_agent','collector.ini','machine.secret.env','run-collector.sh']]])
 run('collector-permissions',['docker','exec',S,'chmod','700',REMOTE+'/sh_agent',REMOTE+'/run-collector.sh',REMOTE+'/machine.secret.env'])
 cout=(N/'collector.stdout').open('xb');cerr=(N/'collector.stderr').open('xb');collector=subprocess.Popen(['docker','exec',S,REMOTE+'/run-collector.sh'],stdout=cout,stderr=cerr)
 # Before any new business DML, all fixed online paths are transported and the active member waits.
 for attempt in range(90):
  status=state('initial-wait-'+str(attempt))
  if status.count('TRANSFER|FINISHED|')>=3:break
  assert collector.poll() is None;time.sleep(2)
 else:raise AssertionError('initial online transport timeout')
 discovery=http('open-discovery','/oracle/raw-plan/discover',{});assert discovery['state']=='WAITING_MEMBER' and not discovery['members']
 (N/'open-wait-proof.json').write_text(json.dumps(discovery,indent=2))
 proofs=[];raws=[]
 for ordinal,note in enumerate(['online-first','online-next']):
  prior='anchor' if ordinal==0 else 'online-first';marker=N/('source-'+str(ordinal)+'.started');assert not marker.exists();marker.write_text('one new future UPDATE; never replay\n')
  sql('source-'+str(ordinal),f"alter session set container=ORKPDB;\nbegin update ORKLAB.T_D2_BLOCKS_20261008 set NOTE='{note}' where ID=0 and NOTE='{prior}';if sql%rowcount<>1 then raise_application_error(-20001,'ROWCOUNT');end if;end;\n/\ncommit;")
  sql('switch-'+str(ordinal),'alter system switch logfile;')
  key='raw-stream-'+str(GRANT)+'-'+str(ordinal+1)
  for attempt in range(90):
   status=state('window-'+str(ordinal)+'-wait-'+str(attempt))
   if ordinal==0 and status.count('TRANSFER|FINISHED|')>=4:
    # Initialize target identity once from the independently parsed frozen transport bytes, then grant only server scope.
    if not raws:
     raw=freeze_sequence(first,'first')
     if raw is None:time.sleep(2);continue
     raws.append(raw);projected=projection('first-projection',raws);e=projected['evidence'];assert len(projected['events'])==1
     identity=':'.join(str(e['source'][k]) for k in ['dbid','activationId','compatibility','thread','resetlogsId','resetlogsScn','containerUid','containerId'])
     grant={k:v for k,v in plan.items() if k!='sessions'}
     sql('grant',f"alter session set container=ORKPDB;\ninsert into {T}_IDENT values(1,'{TARGET}','{identity}','{e['schemaSha256']}');\ninsert into {U}.SH_ORACLE_INPUT_BINDING values({GRANT},'{SOURCE}','online-grant',1,2,'v3-history-consumer','{e['schemaSha256']}','APPROVED_RAW_STREAM',"+clob(grant)+",systimestamp,'owned-online-normal');\ncommit;")
   if f'BATCH|{key}|PUBLISHED|0|1' in status:break
   if '|FAILED|' in status:raise AssertionError(status)
   assert collector.poll() is None;time.sleep(2)
  else:raise AssertionError('online normal publication timeout')
  if ordinal==1:raws.append(freeze_sequence(first+1,'next'))
  proofs.append(checkpoint('checkpoint-'+str(ordinal),key))
 final=projection('final-projection',raws);assert len(final['events'])==2 and final['pendingTransactions']==0
 actual=sql('actual',f"""alter session set container=ORKPDB;
select 'COUNTS|'||(select count(*) from {T}_APPLIED)||'|'||(select count(*) from {T}_MEMBERS)||'|'||(select count(*) from {T}_COMMITS)||'|'||(select count(*) from {U}.SH_EVENT_LEDGER)||'|'||(select count(*) from {U}.SH_PUBLISH_GATE where VERDICT='PASS') from dual;
select 'MATCH|'||count(*) from {T}_ROWS t join ORKLAB.T_D2_BLOCKS_20261008 s on s.ID=t.ID where t.TXT=s.TXT and t.NOTE=s.NOTE;
select 'EVENT|'||EVENT_ID||'|'||PAYLOAD_SHA||'|'||BEFORE_IMAGE||'|'||AFTER_IMAGE||'|'||OPERATION from {T}_APPLIED order by EVENT_ID;
select 'BINDING|'||WINDOW_KEY||'|'||REGISTERED_BY from {U}.SH_ORACLE_INPUT_BINDING where STATE='APPROVED_BOUNDED';
select 'OLD_TARGET|'||(select count(*) from ORKTGT_CMT_20261005.SHPBL1_APPLIED)||'|'||(select count(*) from ORKTGT_CMT_20261005.SHPBL1_MEMBERS)||'|'||(select count(*) from ORKTGT_CMT_20261005.SHPBL1_COMMITS) from dual;
""")
 assert 'COUNTS|2|2|2|2|2' in actual and 'MATCH|20' in actual and 'OLD_TARGET|46|46|4' in actual
 def image(values):return ';'.join(k+'='+('NULL' if v is None else 'NUMBER:'+str(v) if isinstance(v,(int,float)) else 'VARCHAR2:'+base64.urlsafe_b64encode(v.encode()).decode().rstrip('=')) for k,v in values.items())
 expected=sorted('|'.join([event['eventId'],meta['payloadSha256'],image(event['before']),image(event['after']),event['operation']]) for event,meta in zip(final['events'],final['evidence']['events']))
 assert sorted(l[len('EVENT|'):] for l in actual.splitlines() if l.startswith('EVENT|'))==expected
 for ordinal,proof in enumerate(proofs):
  producer=proof['checkpoint']['producerState'];assert (producer['sourceCount'],producer['decodedThroughSequence'],producer['publishedEvents'],producer['pendingTransactions'])==(ordinal+1,first+ordinal,ordinal+1,0)
 assert actual.count('|oracle-raw-discovery')==2
 pid=run('collector-pid',['docker','exec',S,'cat',REMOTE+'/collector.pid']).strip()
 result=dict(candidate,agentSha256=sha(binary),sourceActions=2,newAcquisitions=1,onlinePaths=members,firstSequence=first,closedSequences=[first,first+1],sameCollectorPid=pid,automaticRegistration=True,events=2,target=[2,2,2],matchedRows=20,mismatches=0,proofs=proofs,retentionLowWatermarkVerified=False,independentTargetAccepted=False)
 (N/'normal-online-proof.json').write_text(json.dumps(result,indent=2))
finally:
 try:
  if collector is not None and collector.poll() is None:
   pid=run('stop-collector-pid',['docker','exec',S,'cat',REMOTE+'/collector.pid']).strip();assert pid.isdecimal()
   cmd=run('stop-collector-owner',['docker','exec',S,'cat','/proc/'+pid+'/cmdline']);assert cmd.split('\0')[0]==REMOTE+'/sh_agent'
   run('stop-collector',['docker','exec',S,'kill','-TERM',pid]);collector.wait(timeout=60)
  if collector is not None:cout.close();cerr.close();(N/'collector.rc').write_text(str(collector.returncode))
 finally:
  try:
   if helper:stop_app('final-app')
  finally:
   if started:run('source-stop',['docker','stop','--timeout','60',S])
