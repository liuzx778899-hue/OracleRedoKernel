"""Owned control fixtures only. No shared matrix schema, business DML or source restart."""
import json,secrets,subprocess,sys
from pathlib import Path
R=Path(__file__).resolve().parent.parent;O=R/'lab/out/oracle-registry-20261005';SOURCE='oraredo-19c-lab-20261004'
assert O.is_dir() and not (O/'control.secret.json').exists()
resume=sys.argv[1:]==['--resume-owned-oracle-schemas']
def run(n,args,data=None):
    args=list(map(str,args));(O/(n+'.command.json')).write_text(json.dumps(args));p=subprocess.run(args,input=data,capture_output=True,timeout=120)
    for ext,v in [('stdout',p.stdout),('stderr',p.stderr)]:(O/(n+'.'+ext+'.txt')).write_bytes(v)
    (O/(n+'.rc.txt')).write_text(str(p.returncode));assert p.returncode==0,(n,p.returncode)
    return p.stdout
assert run('source-owner-r2' if resume else 'source-owner',['docker','inspect',SOURCE,'--format','{{.Config.Labels.owner}}|{{.State.Status}}']).decode().strip()=='OracleRedoKernel|running'
names={'mysql':'orkctrl-mysql-20261005','sqlserver':'orkctrl-sqlserver-20261005'}
if not resume:
    for name in names.values():assert not subprocess.check_output(['docker','ps','-a','--filter','name=^/'+name+'$','--format','{{.Names}}']).strip()
    password='Ork_'+secrets.token_hex(16)+'_A9!'
    (O/'mysql.secret.env').write_text('MYSQL_ROOT_PASSWORD='+password+'\n')
    (O/'sqlserver.secret.env').write_text('ACCEPT_EULA=Y\nMSSQL_SA_PASSWORD='+password+'\n')
    run('create-mysql',['docker','run','-d','--pull=never','--name',names['mysql'],'--label','owner=OracleRedoKernel','--label','scope=oracle-registry-control','--memory','768m','-p','127.0.0.1:33307:3306','--env-file',O/'mysql.secret.env','mysql:8.0'])
    run('create-sqlserver',['docker','run','-d','--pull=never','--name',names['sqlserver'],'--label','owner=OracleRedoKernel','--label','scope=oracle-registry-control','--memory','2g','-p','127.0.0.1:31444:1433','--env-file',O/'sqlserver.secret.env','mcr.microsoft.com/mssql/server:2022-latest'])
else:
    password=(O/'mysql.secret.env').read_text().strip().split('=',1)[1]
    for name in names.values():assert run(name+'-owned-ready',['docker','inspect',name,'--format','{{.Config.Labels.owner}}|{{.State.Status}}']).decode().strip()=='OracleRedoKernel|running'
    assert (O/'create-oracle-owned-schemas.rc.txt').read_text()=='1'
    count=run('oracle-schema-preflight-r2',['docker','exec','-i',SOURCE,'sqlplus','-s','/','as','sysdba'],b"whenever sqlerror exit failure\nset pages 0 feedback off heading off\nalter session set container=ORKPDB;\nselect 'COUNT|'||count(*) from dba_users where username in ('ORKCTRLR_NEW_20261005','ORKCTRLR_UPG_20261005');\nexit success\n")
    assert b'COUNT|0' in count
oracle_password='Ork_'+secrets.token_hex(10)+'_A9'
body='whenever sqlerror exit failure rollback\nwhenever oserror exit failure rollback\nset echo off feedback off\nalter session set container=ORKPDB;\n'
for user in ['ORKCTRLR_NEW_20261005','ORKCTRLR_UPG_20261005']:
    body+='create user '+user+' identified by "'+oracle_password+'" default tablespace USERS quota unlimited on USERS;\n'
    body+='grant create session, create table, create sequence, create view to '+user+';\n'
body+='exit success\n';(O/('oracle-schema-r2.secret.sql' if resume else 'oracle-schema.secret.sql')).write_text(body)
output=run('create-oracle-owned-schemas-r2' if resume else 'create-oracle-owned-schemas',['docker','exec','-i',SOURCE,'sqlplus','-s','/','as','sysdba'],body.encode())
assert oracle_password.encode() not in output
targets=[]
addresses={}
for dialect,name in names.items():
    inspection=json.loads(subprocess.check_output(['docker','inspect',name]))[0]
    assert inspection['Config']['Labels']['owner']=='OracleRedoKernel'
    addresses[dialect]=inspection['NetworkSettings']['Networks']['bridge']['IPAddress']
    assert addresses[dialect]
source_inspection=json.loads(subprocess.check_output(['docker','inspect',SOURCE]))[0]
assert 'bridge' in source_inspection['NetworkSettings']['Networks']
for dialect in ['mysql','sqlserver','oracle']:
    for stage in ['NEW','UPG']:
        db='orkctrl_'+stage.lower()
        if dialect=='mysql':url='jdbc:mysql://'+addresses[dialect]+':3306/'+db+'?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC';user='root';pw=password
        elif dialect=='sqlserver':url='jdbc:sqlserver://'+addresses[dialect]+':1433;databaseName='+db+';encrypt=false;trustServerCertificate=true';user='sa';pw=password
        else:url='jdbc:oracle:thin:@localhost:1521/ORKPDB';user='ORKCTRLR_'+stage+'_20261005';pw=oracle_password
        targets.append(dict(dialect=dialect,stage=stage,database=db,url=url,user=user,password=pw))
(O/'control.secret.json').write_text(json.dumps(targets))
(O/'network-proof.json').write_text(json.dumps({'source_network_unchanged':True,'owned_network':'bridge','owned_control_addresses':addresses},indent=2))
(O/'resource-proof.json').write_text(json.dumps({'owned_containers':names,'owned_oracle_schemas':['ORKCTRLR_NEW_20261005','ORKCTRLR_UPG_20261005'],'shared_matrix_used':False,'business_source_dml':0,'source_restarted':False},indent=2));print('OWNED_CONTROL_FIXTURES_PREPARED')
