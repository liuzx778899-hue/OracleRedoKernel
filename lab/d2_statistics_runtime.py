"""New, owned normal-path fixture and HTTP baseline; each action runs once."""
from pathlib import Path
import hashlib,json,secrets,shutil,subprocess,sys

R=Path(__file__).resolve().parent.parent;N=R/'lab/out/d2-statistics-runtime-20261008'
C=R/'lab/out/d2-statistics-candidate-20261008/application.jar'
S='oraredo-19c-lab-20261004';H='ork-d2-statistics-20261008';U='ORKD2STATCTL_20261008';PORT=37294
TABLE='T_D2_STATS_20261008';INDEX='PK_D2_STATS_20261008'
usage='--usage' in sys.argv
if usage:
    sys.argv.remove('--usage');N=R/'lab/out/d2-usage-runtime-20261008';C=R/'lab/out/d2-usage-candidate-20261008/application.jar'
    H='ork-d2-usage-20261008';U='ORKD2USAGECTL_20261008';PORT=37295;TABLE='T_D2_USAGE_20261008';INDEX='PK_D2_USAGE_20261008'
scope='oracle-d2-usage' if usage else 'oracle-d2-statistics'
action=sys.argv[1];assert action in ['check','provision','start','health','health-r2','acquire','acquire-r2','stop']
N.mkdir(exist_ok=True);marker=N/('executed-'+action+'.py');assert not marker.exists(),'inspect failed action; never replay'
shutil.copyfile(__file__,marker)
props=dict(line.split('=',1) for line in (R/'lab/out/d2-index-savepoint-20261008/runtime.secret.env').read_text().splitlines())
redactions=[v for k,v in props.items() if 'PASSWORD' in k and v]
control=N/'control.secret.json'
if control.exists():redactions.append(json.loads(control.read_text())['password'])
def run(label,args,data=None):
    assert not (N/(label+'.rc')).exists()
    p=subprocess.run(list(map(str,args)),input=data,capture_output=True,timeout=180)
    for ext,value in [('stdout',p.stdout),('stderr',p.stderr)]:
        for secret in sorted(redactions,key=len,reverse=True):value=value.replace(secret.encode(),b'<redacted-secret>')
        (N/(label+'.'+ext)).write_bytes(value)
    (N/(label+'.rc')).write_text(str(p.returncode));assert p.returncode==0,(label,p.returncode)
    return p.stdout.decode('utf-8')
def sql(label,body):
    text='whenever sqlerror exit failure rollback\nwhenever oserror exit failure rollback\nset pages 0 heading off feedback off lines 32767 long 200000\nalter session set container=ORKPDB;\n'+body+'\nexit success\n'
    value=run(label,['docker','exec','-i',S,'sqlplus','-s','/','as','sysdba'],text.encode());assert 'SP2-' not in value
    return value
def owned():
    assert run('source-owner-'+action,['docker','inspect',S,'--format','{{.Config.Labels.owner}}|{{.State.Running}}']).strip()=='OracleRedoKernel|true'
def http(label,path,body=None):
    login=json.loads((R/'lab/out/product-active-20261007/admin.secret.json').read_text());redactions.append(login['password'])
    script='''import json,urllib.request
def req(path,body=None,token=None):
 h={'Content-Type':'application/json'}
 if token:h['Authorization']='Bearer '+token
 with urllib.request.urlopen(urllib.request.Request(BASE+path,data=None if body is None else json.dumps(body).encode(),headers=h),timeout=120) as r:return json.loads(r.read())
l=req('/auth/login',LOGIN);assert l['code']==0
print(json.dumps(req(PATH,BODY,l['data']['token'])))
'''.replace('BASE',repr(f'http://127.0.0.1:{PORT}/api/v1')).replace('LOGIN',repr(login)).replace('PATH',repr(path)).replace('BODY',repr(body))
    result=json.loads(run(label,['docker','exec','-i',S,'python3','-'],script.encode()));assert result['code']==0,result
    return result['data']
if action in ['check','provision']:owned()
if action=='check':
    assert H not in run('helpers',['docker','ps','-a','--format','{{.Names}}']).splitlines()
    result=sql('resources',f"select 'CONTROL|'||count(*) from dba_users where username='{U}';\nselect 'FIXTURE|'||count(*) from dba_objects where owner='ORKLAB' and object_name in ('{TABLE}','{INDEX}');\nselect 'LOGGING|'||supplemental_log_data_min||'|'||supplemental_log_data_all from v$database;")
    assert all(s in result for s in ['CONTROL|0','FIXTURE|0','LOGGING|NO|NO'])
    assert run('port',['docker','exec',S,'python3','-c',f"import socket;s=socket.socket();print(s.connect_ex(('127.0.0.1',{PORT})));s.close()"]).strip()=='111'
elif action=='provision':
    assert (N/'resources.rc').read_text()=='0'
    free=sql('provision-recheck',f"select 'FREE|'||count(*) from dba_users where username='{U}' union all select 'FREE|'||count(*) from dba_objects where owner='ORKLAB' and object_name in ('{TABLE}','{INDEX}');")
    assert [s.strip() for s in free.splitlines() if s.startswith('FREE|')]==['FREE|0','FREE|0']
    password='P'+secrets.token_hex(12)+'a1';control.write_text(json.dumps(dict(user=U,password=password)));redactions.append(password)
    sql('create-control',f'create user {U} identified by "{password}" default tablespace USERS quota unlimited on USERS;\ngrant create session,create table,create sequence to {U};')
    sql('create-fixture',f"create table ORKLAB.{TABLE}(ID number not null,TXT varchar2(120),NOTE varchar2(80),constraint {INDEX} primary key(ID));\ninsert into ORKLAB.{TABLE} values(0,'statistics-seed','anchor');\ncommit;")
    reader=props['SH_ORACLE_ACQUISITION_USER'];assert reader.replace('_','').isalnum()
    sql('fixture-read-access',f'grant select,flashback on ORKLAB.{TABLE} to {reader};\ngrant select,flashback on SYS.ICOL$ to {reader};')
    sql('fixture-state',f"select 'FIXTURE|'||ID||'|'||TXT||'|'||NOTE from ORKLAB.{TABLE};\nselect 'IDENTITY|'||object_name||'|'||object_id||'|'||data_object_id from dba_objects where owner='ORKLAB' and object_name in ('{TABLE}','{INDEX}') order by object_name;")
elif action=='start':
    assert (N/'create-fixture.rc').read_text()=='0' and C.exists()
    assert H not in run('helper-recheck',['docker','ps','-a','--format','{{.Names}}']).splitlines()
    for folder in ['snapshots','prefix-state','prefix-input','acks','failures']:(N/folder).mkdir(exist_ok=True)
    props.update(SPRING_DATASOURCE_USERNAME=U,SPRING_DATASOURCE_PASSWORD=json.loads(control.read_text())['password'],SERVER_PORT=str(PORT),SH_WORKER_ID='51' if usage else '50',SH_PIPELINE_AUTO_CONSUME_ENABLED='false',SH_ORACLE_RAW_PLAN_ENABLED='false',SH_ORACLE_RAW_PLAN_REGISTRATION_ENABLED='false',SH_ORACLE_ACQUISITION_SOURCE_ID=scope,SH_ORACLE_ACQUISITION_OWNER='ORKLAB',SH_ORACLE_ACQUISITION_TABLE=TABLE,SH_ORACLE_ACQUISITION_INDEX=INDEX,SH_ORACLE_ACQUISITION_DIRECTORY='/evidence/snapshots',SH_ORACLE_PREFIX_INPUT_DIRECTORY='/evidence/prefix-input',SH_ORACLE_PREFIX_CHECKPOINT_DIRECTORY='/evidence/prefix-state',SH_ORACLE_BOUNDED_ACK_DIRECTORY='/evidence/acks',SH_PIPELINE_TARGET_APPLY_FAIL_DIR='/evidence/failures')
    env=N/'runtime.secret.env';assert not env.exists();env.write_bytes(''.join(k+'='+v+'\n' for k,v in props.items()).encode())
    (N/'application-sha.txt').write_text(hashlib.sha256(C.read_bytes()).hexdigest())
    run('start',['docker','run','-d','--name',H,'--label','owner=OracleRedoKernel','--label','scope='+scope,'--user','0','--network','container:'+S,'--workdir','/evidence','--env-file',env,'--mount','type=bind,source='+str(N)+',target=/evidence','--mount','type=bind,source='+str(C.parent)+',target=/candidate,readonly','--entrypoint','java','flink:1.18-java17','-Xmx1536m','-jar','/candidate/application.jar'])
elif action in ['health','health-r2']:
    http(action,'/system/info')
    assert run('running-sha',['docker','exec',H,'sha256sum','/candidate/application.jar']).split()[0]==(N/'application-sha.txt').read_text()
    result=sql('control-state',f"select 'ACQUISITIONS|'||count(*) from {U}.SH_ORACLE_ACQUISITION;\nselect 'TASKS|'||count(*) from {U}.SH_TASK;")
    assert 'ACQUISITIONS|0' in result and 'TASKS|0' in result
elif action in ['acquire','acquire-r2']:
    assert (N/'running-sha.rc').read_text()=='0'
    result=http('historical-'+action,'/oracle/acquisitions/historical',dict(sourceId=scope))
    assert result['state']=='ACQUIRED_ONLY';snapshot=N/'snapshots'/(str(result['id'])+'.snapshot')
    assert hashlib.sha256(snapshot.read_bytes()).hexdigest()==result['snapshotSha256']
    text=snapshot.read_text();assert 'DICT_STATS_BEGIN|1' in text and 'DICT_STATS_END|22' in text
    if usage:assert 'DICT_USAGE_BEGIN|1' in text and 'DICT_USAGE_END|10' in text
    (N/'baseline-reference.json').write_text(json.dumps(dict(path=str(snapshot),scn=result['snapshotScn'],sha256=result['snapshotSha256'],acquisition_id=str(result['id']),sourceFixture=TABLE,trustedHistoricalHttp=True),indent=2))
elif action=='stop':
    assert run('helper-owner',['docker','inspect',H,'--format','{{.Config.Labels.owner}}|{{.Config.Labels.scope}}']).strip()=='OracleRedoKernel|'+scope
    run('stop',['docker','stop',H])
print(json.dumps(dict(action=action,automaticConsumption=False)))
