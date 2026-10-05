"""Prepare one new owned Oracle control schema; reuse only owned containers and the prior UPG controls."""
import json,secrets,subprocess
from pathlib import Path
R=Path(__file__).resolve().parent.parent;O=R/'lab/out/oracle-control-publication-20261005';OLD=R/'lab/out/oracle-registry-20261005';SOURCE='oraredo-19c-lab-20261004'
assert O.is_dir() and not (O/'control.secret.json').exists()
def run(n,args,data=None,private=False):
    args=list(map(str,args));(O/(n+'.command.json')).write_text(json.dumps(args));p=subprocess.run(args,input=data,capture_output=True,timeout=120)
    for ext,value in [('stdout',p.stdout),('stderr',p.stderr)]:(O/(n+('.secret' if private else '')+'.'+ext+'.txt')).write_bytes(value)
    (O/(n+'.rc.txt')).write_text(str(p.returncode));assert p.returncode==0,n
    return p.stdout
for n in [SOURCE,'orkctrl-mysql-20261005','orkctrl-sqlserver-20261005']:assert run(n+'-owner',['docker','inspect',n,'--format','{{.Config.Labels.owner}}|{{.State.Status}}']).decode().strip()=='OracleRedoKernel|running'
user='ORKPUB_NEW_20261005';sql="whenever sqlerror exit failure\nset pages 0 feedback off heading off\nalter session set container=ORKPDB;\nselect 'COUNT|'||count(*) from dba_users where username='"+user+"';\nexit success\n"
assert b'COUNT|0' in run('new-oracle-schema-preflight',['docker','exec','-i',SOURCE,'sqlplus','-s','/','as','sysdba'],sql.encode())
password='Ork_'+secrets.token_hex(10)+'_A9';body='whenever sqlerror exit failure\nset echo off feedback off\nalter session set container=ORKPDB;\ncreate user '+user+' identified by "'+password+'" default tablespace USERS quota unlimited on USERS;\ngrant create session,create table,create sequence,create view to '+user+';\nexit success\n'
(O/'new-oracle-schema.secret.sql').write_text(body);run('new-oracle-schema-create',['docker','exec','-i',SOURCE,'sqlplus','-s','/','as','sysdba'],body.encode(),private=True)
targets=json.loads((OLD/'control.secret.json').read_text())
for t in targets:
    if t['stage']=='NEW':
        t['url']=t['url'].replace('orkctrl_new','orkpub_new');t['database']='orkpub_new'
        if t['dialect']=='oracle':t['user']=user;t['password']=password
(O/'control.secret.json').write_text(json.dumps(targets));(O/'resource-proof.json').write_text(json.dumps({'owned_new_oracle_schema':user,'new_mysql_sqlserver_database':'orkpub_new','owned_upgrade_controls':'prior orkctrl_upg / ORKCTRLR_UPG_20261005','shared_control_used':False,'source_business_dml':0,'source_restarted':False},indent=2));print('ORACLE_CONTROL_PUBLICATION_RESOURCES_PREPARED')
