"""Owned control schema and authenticated HTTP acquisition; no source business DDL/DML or target writes."""
import hashlib,json,secrets,shutil,subprocess,sys
from pathlib import Path
R=Path(__file__).resolve().parent.parent;P=R/'lab/out/streamhouse-oracle-gate-worktree';O=R/'lab/out/v2-control-runtime-20261006';A=R/'lab/out/oracle-snapshot-acquisition-20261005';S='oraredo-19c-lab-20261004';NAME='ork-oracle-v2-acquisition-20261006';USER='ORKV2CTL_20261006'
assert subprocess.check_output(['docker','inspect',S,'--format','{{.Config.Labels.owner}}|{{.State.Status}}']).decode().strip()=='OracleRedoKernel|running'
def sql(name,text,private=False):
 body='whenever sqlerror exit failure rollback\nset pages 0 feedback off heading off lines 32767 long 200000 longchunksize 200000\nalter session set container=ORKPDB;\n'+text+'\nexit success\n';suffix='.secret' if private else ''
 assert not (O/(name+suffix+'.sql')).exists();(O/(name+suffix+'.sql')).write_text(body)
 p=subprocess.run(['docker','exec','-i',S,'sqlplus','-s','/','as','sysdba'],input=body.encode(),capture_output=True)
 (O/(name+suffix+'.stdout.txt')).write_bytes(p.stdout);(O/(name+suffix+'.stderr.txt')).write_bytes(p.stderr);(O/(name+'.rc.txt')).write_text(str(p.returncode));assert p.returncode==0 and b'SP2-' not in p.stdout;return p.stdout.decode().strip()
def record(name,args,input=None):
 assert not (O/(name+'.rc.txt')).exists();p=subprocess.run(args,input=input,capture_output=True,timeout=120)
 (O/(name+'.stdout.txt')).write_bytes(p.stdout);(O/(name+'.stderr.txt')).write_bytes(p.stderr);(O/(name+'.rc.txt')).write_text(str(p.returncode));assert p.returncode==0,name;return p.stdout.decode()
action=sys.argv[1]
if action=='prepare':
 assert not (O/'control.secret.json').exists();assert 'COUNT|0' in sql('control-preflight',"select 'COUNT|'||count(*) from dba_users where username='"+USER+"';")
 pwd='P'+secrets.token_hex(12)+'a1';sql('create-control',"create user "+USER+' identified by "'+pwd+'" default tablespace USERS quota unlimited on USERS;\ngrant create session,create table,create sequence to '+USER+';',True)
 (O/'control.secret.json').write_text(json.dumps(dict(user=USER,password=pwd,url='jdbc:oracle:thin:@//localhost:1521/ORKPDB')))
 sql('source-before',"select 'LOGGING|'||supplemental_log_data_min||'|'||supplemental_log_data_all from v$database;\nselect 'OLD|'||ID||'|'||TXT||'|'||NOTE from ORKLAB.T_BASELINE_20261004;")
elif action=='start':
 assert not (O/'runtime.secret.env').exists();assert not subprocess.check_output(['git','status','--porcelain'],cwd=P)
 control=json.loads((O/'control.secret.json').read_text());reader=(A/'source.secret').read_text().splitlines();case=json.loads((A/'cases.json').read_text())[0];case=dict(case,owner='ORKLAB',table='T_DICT_ANCHOR_20261005',index='PK_DICT_ANCHOR_20261005');password='H'+secrets.token_hex(12)+'a1'
 props=dict(SH_DB_PROFILE='oracle',SPRING_DATASOURCE_URL=control['url'],SPRING_DATASOURCE_USERNAME=control['user'],SPRING_DATASOURCE_PASSWORD=control['password'],SERVER_ADDRESS='127.0.0.1',SERVER_PORT='37221',SH_WORKER_ID='18',SH_ADMIN_INITIAL_PASSWORD=password,SH_PIPELINE_AUTO_CONSUME_ENABLED='false',SH_ORACLE_ACQUISITION_ENABLED='true',SH_ORACLE_ACQUISITION_SOURCE_ID='oracle-v2-DICT',SH_ORACLE_ACQUISITION_AUTHORITY='owned-v2-acquisition-20261006',SH_ORACLE_ACQUISITION_SOURCE_JSON=json.dumps(case['source'],separators=(',',':')),SH_ORACLE_ACQUISITION_OWNER=case['owner'],SH_ORACLE_ACQUISITION_TABLE=case['table'],SH_ORACLE_ACQUISITION_INDEX=case['index'],SH_ORACLE_ACQUISITION_DIRECTORY='/evidence/snapshots',SH_ORACLE_ACQUISITION_URL=reader[2],SH_ORACLE_ACQUISITION_USER=reader[0],SH_ORACLE_ACQUISITION_PASSWORD=reader[1])
 (O/'runtime.secret.env').write_text(''.join(k+'='+v+'\n' for k,v in props.items()));(O/'admin.secret.json').write_text(json.dumps(dict(username='admin',password=password)))
 jar=P/'server/streamhouse-app/target/streamhouse-app-0.1.0-SNAPSHOT.jar';proof=json.loads((O/(sys.argv[2]+'-proof.json')).read_text());assert hashlib.sha256(jar.read_bytes()).hexdigest()==proof['app_sha256'];shutil.copyfile(jar,O/'application.jar')
 (O/'runtime-pin.json').write_text(json.dumps(dict(commit=proof['commit'],app_sha256=proof['app_sha256'],container=NAME,scope='oracle-acquisition-controller',port=37221,control=USER,source_business_dml=0,target_writes=0),indent=2))
 args=['docker','run','-d','--name',NAME,'--label','owner=OracleRedoKernel','--label','scope=oracle-v2-acquisition','--user','0','--network','container:'+S,'--workdir','/evidence','--env-file',str(O/'runtime.secret.env'),'--mount','type=bind,source='+str(O)+',target=/evidence','--entrypoint','java','flink:1.18-java17','-Xmx1536m','-jar','/evidence/application.jar'];record('start',args)
elif action in ['acquire','verify','inject','unauthorized']:
 data=dict(sourceId='oracle-v2-DICT') if action in ['acquire','unauthorized'] else (dict(sourceId='oracle-v2-DICT',authority='client') if action=='inject' else None)
 path='/oracle/acquisitions'+('/'+(O/'acquisition-id.txt').read_text() if action=='verify' else '')
 name=sys.argv[2] if len(sys.argv)>2 else action
 remote="""import json,urllib.request,urllib.error
base='http://127.0.0.1:37221/api/v1'
def req(path,data=None,token=None):
 h={'Content-Type':'application/json'}
 if token:h['Authorization']='Bearer '+token
 try:
  with urllib.request.urlopen(urllib.request.Request(base+path,data=None if data is None else json.dumps(data).encode(),headers=h),timeout=60) as r:return [r.status,json.loads(r.read())]
 except urllib.error.HTTPError as ex:return [ex.code,json.loads(ex.read())]
token=None
if ACTION!='unauthorized':
 login=req('/auth/login',LOGIN);assert login[1]['code']==0;token=login[1]['data']['token']
r=req(PATH,DATA,token);print(json.dumps(r))
""".replace('ACTION',repr(action)).replace('LOGIN',repr(json.loads((O/'admin.secret.json').read_text()))).replace('PATH',repr(path)).replace('DATA',repr(data))
 result=json.loads(record(name,['docker','exec','-i',S,'python3','-'],remote.encode()))
 if action=='acquire':
  assert result[1]['code']==0 and result[1]['data']['state']=='ACQUIRED_ONLY';(O/'acquisition-id.txt').write_text(str(result[1]['data']['id']))
 elif action=='inject':assert result[0]==400 or result[1]['code']!=0
 elif action=='unauthorized':assert result[0]==401
 print('HTTP_ACQUISITION_RESULT|'+name+'|'+str(result[0])+'|'+str(result[1]['code']))
elif action=='audit':
 sql(sys.argv[2],"select 'ACQ|'||to_char(ID,'FM99999999999999999999')||'|'||SOURCE_ID||'|'||AUTHORITY||'|'||SNAPSHOT_SHA256||'|'||SNAPSHOT_SCN||'|'||STATE from "+USER+".SH_ORACLE_ACQUISITION;\nselect 'BINDINGS|'||count(*) from "+USER+".SH_ORACLE_INPUT_BINDING;\nselect 'EVENTS|'||count(*) from "+USER+".SH_EVENT_LEDGER;\nselect 'FAIL|'||STAGE||'|'||REASON||'|'||DETAIL from "+USER+".SH_FAILCLOSED_RECORD;\nselect 'LOGGING|'||supplemental_log_data_min||'|'||supplemental_log_data_all from v$database;\nselect 'OLD|'||ID||'|'||TXT||'|'||NOTE from ORKLAB.T_BASELINE_20261004;")
else:raise ValueError(action)
