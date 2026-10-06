"""Owned V3 product acquisition runtime. No source fixture DDL/DML in this driver."""
import hashlib,json,secrets,subprocess,sys
from pathlib import Path
R=Path(__file__).resolve().parent.parent;P=R/'lab/out/streamhouse-oracle-gate-worktree';N=R/'lab/out/v3-product-runtime-20261006'
S='oraredo-19c-lab-20261004';NAME='ork-oracle-v3-product-20261006';USER='ORKV3CTL_20261006';PORT=37261
action=sys.argv[1];name=sys.argv[2] if len(sys.argv)>2 else action
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
assert not (N/('executed-'+name+'-runtime-driver.py')).exists()
(N/('executed-'+name+'-runtime-driver.py')).write_bytes(Path(__file__).read_bytes())

def run(label,args,data=None):
    args=list(map(str,args));assert not (N/(label+'.command.json')).exists(),label
    (N/(label+'.command.json')).write_text(json.dumps(args));p=subprocess.run(args,input=data,capture_output=True,timeout=180)
    passwords=[]
    for file in [N/'control.secret.json',N/'admin.secret.json']:
        if file.exists():passwords.append(json.loads(file.read_text())['password'].encode())
    reader=R/'lab/out/oracle-snapshot-acquisition-20261005/source.secret'
    passwords.append(reader.read_text().splitlines()[1].encode())
    passwords.append((R/'lab/out/oracle-target-20261005/target.secret').read_text().splitlines()[1].encode())
    for suffix,value in [('stdout.txt',p.stdout),('stderr.txt',p.stderr)]:
        for password in passwords:value=value.replace(password,b'<redacted-secret>')
        (N/(label+'.'+suffix)).write_bytes(value)
    (N/(label+'.rc.txt')).write_text(str(p.returncode));assert p.returncode==0,label
    return p.stdout.decode()

def sql(label,text,private=False):
    body='whenever sqlerror exit failure rollback\nwhenever oserror exit failure rollback\nset pages 0 feedback off heading off lines 32767 long 200000 longchunksize 200000 trimspool on\nalter session set container=ORKPDB;\n'+text+'\nexit success\n'
    (N/(label+('.secret.sql' if private else '.sql'))).write_text(body)
    out=run(label,['docker','exec','-i',S,'sqlplus','-s','/','as','sysdba'],body.encode());assert 'SP2-' not in out;return out

def ownership():
    label=name+'-ownership'
    assert run(label,['docker','inspect',NAME,'--format','{{.Config.Labels.owner}}|{{.Config.Labels.scope}}']).strip()=='OracleRedoKernel|oracle-v3-product'

if action=='prepare':
    assert not (N/'control.secret.json').exists()
    assert run('source-ownership',['docker','inspect',S,'--format','{{.Config.Labels.owner}}|{{.State.Status}}']).strip()=='OracleRedoKernel|running'
    pre=sql('control-preflight',"select 'COUNT|'||count(*) from dba_users where username='"+USER+"';\nselect 'LOGGING|'||supplemental_log_data_min||'|'||supplemental_log_data_all from v$database;\nselect 'OLD|'||ID||'|'||TXT||'|'||NOTE from ORKLAB.T_BASELINE_20261004;\nselect 'BASE|'||ID||'|'||TXT||'|'||NOTE from ORKLAB.T_V2_HIST_20261006;")
    assert 'COUNT|0' in pre and 'LOGGING|NO|NO' in pre and 'OLD|11|blocking-closed|new-note' in pre and 'BASE|0|history-v3-restored|anchor' in pre
    pwd='P'+secrets.token_hex(12)+'a1'
    (N/'control.secret.json').write_text(json.dumps(dict(user=USER,password=pwd,url='jdbc:oracle:thin:@//localhost:1521/ORKPDB')))
    sql('create-owned-control',"create user "+USER+' identified by "'+pwd+'" default tablespace USERS quota unlimited on USERS;\ngrant create session,create table,create sequence to '+USER+';',True)
elif action=='start':
    assert not (N/'runtime.secret.env').exists()
    proof=json.loads((N/'build-proof.json').read_text());assert sha(N/'application.jar')==proof['product_sha256']
    for f in ['OracleAcquisitionService.java','OracleAcquisitionController.java','OracleAcquisitionServiceTest.java']:
        matches=list((P/'server/streamhouse-app/src').rglob(f));assert len(matches)==1 and matches[0].read_bytes()==(N/('build-'+f)).read_bytes()
    control=json.loads((N/'control.secret.json').read_text());reader=(R/'lab/out/oracle-snapshot-acquisition-20261005/source.secret').read_text().splitlines()
    source=dict(dbid=1276726821,activationId=1276717861,compatibility=318767104,thread=1,resetlogsId=1245693159,resetlogsScn=1920977,containerUid=677737149,containerId=3)
    pwd='H'+secrets.token_hex(12)+'a1'
    props=dict(SH_DB_PROFILE='oracle',SPRING_DATASOURCE_URL=control['url'],SPRING_DATASOURCE_USERNAME=control['user'],SPRING_DATASOURCE_PASSWORD=control['password'],SERVER_ADDRESS='127.0.0.1',SERVER_PORT=str(PORT),SH_WORKER_ID='21',SH_ADMIN_INITIAL_PASSWORD=pwd,SH_PIPELINE_AUTO_CONSUME_ENABLED='false',SH_ORACLE_ACQUISITION_ENABLED='true',SH_ORACLE_ACQUISITION_SOURCE_ID='oracle-v3-HIST',SH_ORACLE_ACQUISITION_AUTHORITY='owned-v3-acquisition-20261006',SH_ORACLE_ACQUISITION_SOURCE_JSON=json.dumps(source,separators=(',',':')),SH_ORACLE_ACQUISITION_OWNER='ORKLAB',SH_ORACLE_ACQUISITION_TABLE='T_V2_HIST_20261006',SH_ORACLE_ACQUISITION_INDEX='PK_V2_HIST_20261006',SH_ORACLE_ACQUISITION_DIRECTORY='/evidence/snapshots',SH_ORACLE_ACQUISITION_URL=reader[2],SH_ORACLE_ACQUISITION_USER=reader[0],SH_ORACLE_ACQUISITION_PASSWORD=reader[1])
    target=(R/'lab/out/oracle-target-20261005/target.secret').read_text().splitlines()
    props.update(SH_ORACLE_CONSUMER_ID='v3-history-consumer',SH_ORACLE_BOUNDED_ENABLED='true',
        SH_ORACLE_BOUNDED_TARGET_URL=target[2],SH_ORACLE_BOUNDED_TARGET_USER=target[0],SH_ORACLE_BOUNDED_TARGET_PASSWORD=target[1],
        SH_ORACLE_BOUNDED_TARGET_OWNER=target[0],SH_ORACLE_BOUNDED_TARGET_PREFIX='V3HP',
        SH_ORACLE_BOUNDED_TARGET_ID='oracle-history-V3HP-20261006',SH_ORACLE_BOUNDED_ACK_DIRECTORY='/evidence/acks')
    (N/'runtime.secret.env').write_text(''.join(k+'='+v+'\n' for k,v in props.items()));(N/'admin.secret.json').write_text(json.dumps(dict(username='admin',password=pwd)))
    (N/'runtime-pin.json').write_text(json.dumps(dict(product_sha256=proof['product_sha256'],container=NAME,scope='oracle-v3-product',port=PORT,control=USER,source=source,source_dml=0,target_writes=0)))
    run('start',['docker','run','-d','--name',NAME,'--label','owner=OracleRedoKernel','--label','scope=oracle-v3-product','--user','0','--network','container:'+S,'--workdir','/evidence','--env-file',N/'runtime.secret.env','--mount','type=bind,source='+str(N)+',target=/evidence','--entrypoint','java','flink:1.18-java17','-Xmx1536m','-jar','/evidence/application.jar'])
elif action in ['acquire','verify','inject','unauthorized','health']:
    data=dict(sourceId='oracle-v3-HIST') if action in ['acquire','unauthorized'] else (dict(sourceId='oracle-v3-HIST',authority='client') if action=='inject' else None)
    path='/oracle/acquisitions'+('/'+(N/'acquisition-id.txt').read_text() if action=='verify' else '/historical')
    if action=='health':path='/system/info'
    remote="""import json,urllib.request,urllib.error
base='http://127.0.0.1:37261/api/v1'
def req(path,data=None,token=None):
 h={'Content-Type':'application/json'}
 if token:h['Authorization']='Bearer '+token
 try:
  with urllib.request.urlopen(urllib.request.Request(base+path,data=None if data is None else json.dumps(data).encode(),headers=h),timeout=90) as r:return [r.status,json.loads(r.read())]
 except urllib.error.HTTPError as ex:return [ex.code,json.loads(ex.read())]
token=None
if ACTION!='unauthorized':
 login=req('/auth/login',LOGIN);assert login[1]['code']==0;token=login[1]['data']['token']
result=req(PATH,DATA,token);print(json.dumps(result))
""".replace('ACTION',repr(action)).replace('LOGIN',repr(json.loads((N/'admin.secret.json').read_text()))).replace('PATH',repr(path)).replace('DATA',repr(data))
    result=json.loads(run(name,['docker','exec','-i',S,'python3','-'],remote.encode()))
    if action=='acquire':
        assert result[1]['code']==0 and result[1]['data']['state']=='ACQUIRED_ONLY',result
        (N/'acquisition-id.txt').write_text(str(result[1]['data']['id']))
    elif action=='inject':assert result[0]==400 or result[1]['code']!=0
    elif action=='unauthorized':assert result[0]==401
    print('HTTP_V3_RESULT|'+name+'|'+str(result[0])+'|'+str(result[1]['code']))
elif action=='audit':
    sql(name,"select 'ACQ|'||to_char(ID,'FM99999999999999999999')||'|'||SOURCE_ID||'|'||AUTHORITY||'|'||SNAPSHOT_SHA256||'|'||SNAPSHOT_SCN||'|'||STATE from "+USER+".SH_ORACLE_ACQUISITION;\nselect 'BINDINGS|'||count(*) from "+USER+".SH_ORACLE_INPUT_BINDING;\nselect 'EVENTS|'||count(*) from "+USER+".SH_EVENT_LEDGER;\nselect 'FAIL|'||STAGE||'|'||REASON||'|'||DETAIL from "+USER+".SH_FAILCLOSED_RECORD;\nselect 'LOGGING|'||supplemental_log_data_min||'|'||supplemental_log_data_all from v$database;\nselect 'OLD|'||ID||'|'||TXT||'|'||NOTE from ORKLAB.T_BASELINE_20261004;")
elif action in ['stop','restart','logs','state']:
    ownership()
    command={'stop':['docker','stop','--time','30',NAME],'restart':['docker','start',NAME],'logs':['docker','logs',NAME],
        'state':['docker','inspect',NAME,'--format','{{.State.Running}}|{{.State.ExitCode}}|{{.State.Status}}']}[action]
    run(name,command)
else:raise ValueError(action)
