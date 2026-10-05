"""New exclusively owned fixture, real central acquisition, then one commit and two rollbacks. Never replay."""
import hashlib,json,secrets,shutil,subprocess,sys,time
from pathlib import Path
R=Path(__file__).resolve().parent.parent;O=R/'lab/out/acquired-dml-capture-20261005';A=R/'lab/out/oracle-snapshot-acquisition-20261005';P=R/'lab/out/streamhouse-oracle-gate-worktree';S='oraredo-19c-lab-20261004';NAME='ork-oracle-acquired-dml-20261005';U='ORKACDML_20261005';TABLE='T_ACQUIRED_DML_20261005';INDEX='PK_ACQUIRED_DML_20261005';sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
assert subprocess.check_output(['docker','inspect',S,'--format','{{.Config.Labels.owner}}|{{.State.Status}}']).decode().strip()=='OracleRedoKernel|running'
def run(name,args,data=None,expected=0):
 assert not (O/(name+'.command.json')).exists();(O/(name+'.command.json')).write_text(json.dumps(list(map(str,args))))
 p=subprocess.run(list(map(str,args)),input=data,capture_output=True,timeout=180)
 for suffix,value in [('stdout',p.stdout),('stderr',p.stderr)]:(O/(name+'.'+suffix+'.txt')).write_bytes(value)
 (O/(name+'.rc.txt')).write_text(str(p.returncode));assert expected is None or p.returncode==expected,name;return p
def sql(name,text,private=False):
 body='whenever sqlerror exit failure rollback\nwhenever oserror exit failure rollback\nset pages 0 lines 32767 feedback off heading off long 200000 longchunksize 200000\n'+text+'\nexit success\n';(O/(name+('.secret' if private else '')+'.sql')).write_text(body)
 if private:
  p=subprocess.run(['docker','exec','-i',S,'sqlplus','-s','/','as','sysdba'],input=body.encode(),capture_output=True)
  (O/(name+'.secret.stdout.txt')).write_bytes(p.stdout);(O/(name+'.secret.stderr.txt')).write_bytes(p.stderr);(O/(name+'.rc.txt')).write_text(str(p.returncode));assert p.returncode==0 and b'SP2-' not in p.stdout
 else:
  p=run(name,['docker','exec','-i',S,'sqlplus','-s','/','as','sysdba'],body.encode());assert b'SP2-' not in p.stdout
 return p.stdout.decode()
def http(name,path,data=None):
 remote="""import json,urllib.request
base='http://127.0.0.1:37219/api/v1'
def req(path,data=None,token=None):
 h={'Content-Type':'application/json'}
 if token:h['Authorization']='Bearer '+token
 with urllib.request.urlopen(urllib.request.Request(base+path,data=None if data is None else json.dumps(data).encode(),headers=h),timeout=60) as r:return json.loads(r.read())
l=req('/auth/login',LOGIN);assert l['code']==0
r=req(PATH,DATA,l['data']['token']);print(json.dumps(r));assert r['code']==0
""".replace('LOGIN',repr(json.loads((O/'admin.secret.json').read_text()))).replace('PATH',repr(path)).replace('DATA',repr(data))
 return json.loads(run(name,['docker','exec','-i',S,'python3','-'],remote.encode()).stdout)
action=sys.argv[1]
if action=='prepare':
 O.mkdir(exist_ok=False);(O/'executed-prepare-driver.py').write_bytes(Path(__file__).read_bytes());(O/'prepare-driver-sha256.txt').write_text(sha(Path(__file__)))
 check=sql('preflight',"declare n number;begin select count(*) into n from v$database where dbid=1276726821 and activation#=1276717861 and log_mode='ARCHIVELOG' and supplemental_log_data_min='NO' and supplemental_log_data_all='NO';if n!=1 then raise_application_error(-20001,'source mismatch');end if;end;\n/\nalter session set container=ORKPDB;\nselect 'OBJECTS|'||count(*) from dba_objects where owner='ORKLAB' and object_name in ('"+TABLE+"','"+INDEX+"');\nselect 'USER|'||count(*) from dba_users where username='"+U+"';\nselect 'OLD|'||ID||'|'||TXT||'|'||NOTE from ORKLAB.T_BASELINE_20261004;")
 assert 'OBJECTS|0' in check and 'USER|0' in check and 'OLD|11|blocking-closed|new-note' in check
 sql('fixture-once',"alter session set container=ORKPDB;\ncreate table ORKLAB."+TABLE+"(ID number not null,TXT varchar2(128),NOTE varchar2(128) invisible,constraint "+INDEX+" primary key(ID));\ninsert into ORKLAB."+TABLE+"(ID,TXT,NOTE) values(0,'acquired-seed','anchor');\ncommit;\ngrant select,flashback on ORKLAB."+TABLE+" to ORKACQ_20261005;")
 pwd='P'+secrets.token_hex(12)+'a1';sql('control-once',"alter session set container=ORKPDB;\ncreate user "+U+' identified by "'+pwd+'" default tablespace USERS quota unlimited on USERS;\ngrant create session,create table,create sequence to '+U+';',True)
 (O/'control.secret.json').write_text(json.dumps(dict(user=U,password=pwd,url='jdbc:oracle:thin:@//localhost:1521/ORKPDB')))
elif action=='start':
 assert not (O/'runtime.secret.env').exists();proof=json.loads((R/'lab/out/oracle-acquisition-controller-20261005/acquired-input-build-proof.json').read_text());jar=P/'server/streamhouse-app/target/streamhouse-app-0.1.0-SNAPSHOT.jar';assert sha(jar)==proof['app_sha256'];assert not subprocess.check_output(['git','status','--porcelain'],cwd=P)
 control=json.loads((O/'control.secret.json').read_text());reader=(A/'source.secret').read_text().splitlines();target=(R/'lab/out/oracle-target-20261005/target.secret').read_text().splitlines();source=json.loads((A/'cases.json').read_text())[0]['source'];password='H'+secrets.token_hex(12)+'a1'
 props=dict(SH_DB_PROFILE='oracle',SPRING_DATASOURCE_URL=control['url'],SPRING_DATASOURCE_USERNAME=control['user'],SPRING_DATASOURCE_PASSWORD=control['password'],SERVER_ADDRESS='127.0.0.1',SERVER_PORT='37219',SH_WORKER_ID='19',SH_ADMIN_INITIAL_PASSWORD=password,SH_PIPELINE_AUTO_CONSUME_ENABLED='false',SH_ORACLE_CONSUMER_ID='acquired-consumer',SH_ORACLE_ACQUISITION_ENABLED='true',SH_ORACLE_ACQUISITION_SOURCE_ID='oracle-acquired-new-DML',SH_ORACLE_ACQUISITION_AUTHORITY='owned-acquired-dml-20261005',SH_ORACLE_ACQUISITION_SOURCE_JSON=json.dumps(source,separators=(',',':')),SH_ORACLE_ACQUISITION_OWNER='ORKLAB',SH_ORACLE_ACQUISITION_TABLE=TABLE,SH_ORACLE_ACQUISITION_INDEX=INDEX,SH_ORACLE_ACQUISITION_DIRECTORY='/evidence/snapshots',SH_ORACLE_ACQUISITION_URL=reader[2],SH_ORACLE_ACQUISITION_USER=reader[0],SH_ORACLE_ACQUISITION_PASSWORD=reader[1],SH_ORACLE_BOUNDED_ENABLED='true',SH_ORACLE_BOUNDED_TARGET_URL=target[2],SH_ORACLE_BOUNDED_TARGET_USER=target[0],SH_ORACLE_BOUNDED_TARGET_PASSWORD=target[1],SH_ORACLE_BOUNDED_TARGET_OWNER=target[0],SH_ORACLE_BOUNDED_TARGET_PREFIX='ACDML',SH_ORACLE_BOUNDED_TARGET_ID='oracle-dml-ACDML-20261005',SH_ORACLE_BOUNDED_ACK_DIRECTORY='/evidence/acks')
 (O/'runtime.secret.env').write_text(''.join(k+'='+v+'\n' for k,v in props.items()));(O/'admin.secret.json').write_text(json.dumps(dict(username='admin',password=password)));shutil.copyfile(jar,O/'application.jar');(O/'runtime-pin.json').write_text(json.dumps(proof,indent=2))
 run('start',['docker','run','-d','--name',NAME,'--label','owner=OracleRedoKernel','--label','scope=oracle-acquired-dml','--user','0','--network','container:'+S,'--workdir','/evidence','--env-file',O/'runtime.secret.env','--mount','type=bind,source='+str(O)+',target=/evidence','--entrypoint','java','flink:1.18-java17','-Xmx1536m','-jar','/evidence/application.jar'])
elif action=='acquire':
 result=http('actual-source-acquire','/oracle/acquisitions',dict(sourceId='oracle-acquired-new-DML'));row=result['data'];assert row['state']=='ACQUIRED_ONLY';identifier=str(row['id']);(O/'acquisition-id.txt').write_text(identifier);snapshot=O/'snapshots'/(identifier+'.snapshot');assert sha(snapshot)==row['snapshotSha256'];(O/'baseline-reference.json').write_text(json.dumps(dict(acquisition_id=identifier,path=str(snapshot),sha256=row['snapshotSha256'],scn=row['snapshotScn']),indent=2))
elif action=='capture':
 assert not (O/'capture-execution-started.txt').exists();(O/'capture-execution-started.txt').write_text('SOURCE_ACTIONS_MUST_NOT_BE_REPLAYED');(O/'executed-capture-driver.py').write_bytes(Path(__file__).read_bytes());(O/'capture-driver-sha256.txt').write_text(sha(Path(__file__)))
 b=json.loads((O/'baseline-reference.json').read_text());snapshot=Path(b['path']);assert sha(snapshot)==b['sha256']
 info=sql('members',"select 'MEMBER|'||member from v$logfile order by group#;\nselect 'CURRENT|'||sequence# from v$log where status='CURRENT';")
 members=[x.strip().split('|')[1] for x in info.splitlines() if x.strip().startswith('MEMBER|')];start=int(next(x.strip().split('|')[1] for x in info.splitlines() if x.strip().startswith('CURRENT|')));assert len(members)==3
 for name in ['resume_online_observation.py','follow_closed_members.py']:
  actual=O/('executed-'+name);actual.write_bytes((R/'lab'/name).read_bytes());run('copy-'+name,['docker','cp',actual,S+':/tmp/'+name]);assert sha(actual) in run('sha-'+name,['docker','exec',S,'sha256sum','/tmp/'+name]).stdout.decode()
 remote='/tmp/ork-acquired-dml-20261005';collector=['docker','exec',S,'python3','/tmp/follow_closed_members.py',remote,start,300,.25]+members;(O/'collector.command.json').write_text(json.dumps(collector))
 with (O/'collector.stdout.txt').open('wb') as out,(O/'collector.stderr.txt').open('wb') as err:
  process=subprocess.Popen(list(map(str,collector)),stdout=out,stderr=err);(O/'collector-host-pid.txt').write_text(str(process.pid))
  def wait(key,value):
   deadline=time.time()+55
   while time.time()<deadline:
    if any(json.loads(line).get(key)==value for line in (O/'collector.stdout.txt').read_text().splitlines()):return
    if process.poll() is not None:raise RuntimeError('collector terminal; no replay')
    time.sleep(.1)
   raise RuntimeError('collector observation timeout; inspect the same handle, no replay')
  wait('ready',True);archives=[];paths=[]
  for offset,operation in enumerate(['MIXED_COMMIT','INSERT_ROLLBACK','DELETE_ROLLBACK']):
   seq=start+offset;t='ORKLAB.'+TABLE
   statements={'MIXED_COMMIT':["insert into "+t+"(ID,TXT,NOTE) values(11,'acquired-before','acquired-note')","update "+t+" set TXT='acquired-after' where ID=11 and TXT='acquired-before'","delete from "+t+" where ID=11 and TXT='acquired-after' and NOTE='acquired-note'"], 'INSERT_ROLLBACK':["insert into "+t+"(ID,TXT,NOTE) values(12,'acquired-undo','undo-note')"], 'DELETE_ROLLBACK':["delete from "+t+" where ID=0 and TXT='acquired-seed' and NOTE='anchor'"]}
   body="declare s number;begin select sequence# into s from v$log where status='CURRENT';if s!="+str(seq)+" then raise_application_error(-20003,'sequence changed');end if;end;\n/\nalter session set container=ORKPDB;\n"
   for step,statement in enumerate(statements[operation],1):body+='begin '+statement+";if sql%rowcount!=1 then raise_application_error(-20004,'row count');end if;end;\n/\nselect 'STEP|"+operation+'|'+str(step)+"|'||ID||'|'||nvl(TXT,'<NULL>')||'|'||nvl(NOTE,'<NULL>') from "+t+' order by ID;\n'
   body+="select 'XID|'||xidusn||'.'||xidslot||'.'||xidsqn from v$transaction where ses_addr=(select saddr from v$session where sid=sys_context('USERENV','SID'));\n"+('commit;' if operation=='MIXED_COMMIT' else 'rollback;')+"\nselect 'TRUTH|"+operation+"|'||ID||'|'||TXT||'|'||NOTE from "+t+" order by ID;\nalter session set container=CDB$ROOT;\nalter system archive log current;\nselect 'ARCHIVE|'||sequence#||'|'||name from v$archived_log where thread#=1 and sequence#="+str(seq)+' and name is not null order by recid;'
   truth=sql(operation+'-once',body);wait('captured_sequence',seq);sourcepaths=[x.strip().split('|')[2] for x in truth.splitlines() if x.strip().startswith('ARCHIVE|')];assert len(sourcepaths)==1
   archive=O/('archive-'+str(seq)+'.dbf');run('copy-archive-'+str(seq),['docker','cp',S+':'+sourcepaths[0],archive]);archives.append(archive);paths.append(sourcepaths[0])
  rc=process.wait(timeout=55);(O/'collector.rc.txt').write_text(str(rc));assert rc==0
 run('copy-capture',['docker','cp',S+':'+remote,O/'capture'])
 for seq,archive in zip(range(start,start+3),archives):run('capture-archive-'+str(seq),[sys.executable,R/'lab/compare_observation_archive.py',O/'capture'/('sequence-'+str(seq)+'.oracap'),archive])
 trace=sql('independent-dump',"alter session set tracefile_identifier='ORK_ACQUIRED_DML';\n"+''.join("alter system dump logfile '"+p+"';\n" for p in paths)+"select value from v$diag_info where name='Default Trace File';")
 traces=[x.strip() for x in trace.splitlines() if x.strip().startswith('/opt/oracle/') and x.strip().endswith('.trc')];assert len(traces)==1;run('copy-independent-dump',['docker','cp',S+':'+traces[0],O/'dump.trc'])
 (O/'capture-reference.json').write_text(json.dumps(dict(sequences=list(range(start,start+3)),baseline=b,capture_archive_differences=0),indent=2))
else:raise ValueError(action)
print('ACQUIRED_DML_PHASE_COMPLETE|'+action)
