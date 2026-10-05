"""Dedicated control schema and fixed mixed-DML target; source fixture DML is never repeated."""
import json,secrets,subprocess
from pathlib import Path
R=Path(__file__).resolve().parent.parent;O=R/'lab/out/oracle-ack-pending-20261005';A=R/'lab/out/oracle-bounded-runtime-20261005';SOURCE='oraredo-19c-lab-20261004'
assert O.is_dir() and not (O/'control.secret.json').exists()
assert subprocess.check_output(['docker','inspect',SOURCE,'--format','{{.Config.Labels.owner}}|{{.State.Status}}']).decode().strip()=='OracleRedoKernel|running'
def sql(name,text,private=False):
    body='whenever sqlerror exit failure rollback\nset pages 0 feedback off heading off lines 32767 long 200000 longchunksize 200000\nalter session set container=ORKPDB;\n'+text+'\nexit success\n'
    suffix='.secret' if private else '';(O/(name+suffix+'.sql')).write_text(body)
    p=subprocess.run(['docker','exec','-i',SOURCE,'sqlplus','-s','/','as','sysdba'],input=body.encode(),capture_output=True)
    (O/(name+'.stdout'+suffix+'.txt')).write_bytes(p.stdout);(O/(name+'.stderr'+suffix+'.txt')).write_bytes(p.stderr);(O/(name+'.rc.txt')).write_text(str(p.returncode));assert p.returncode==0,name
    return p.stdout.decode()
user='ORKACK_20261005';password='P'+secrets.token_hex(12)+'a1';original=json.loads((A/'runtime-control.secret.json').read_text())[0]
assert 'COUNT|0' in sql('preflight',"select 'COUNT|'||count(*) from dba_users where username='"+user+"';\nselect 'TARGETS|'||count(*) from dba_tables where owner='ORKTGT_CMT_20261005' and table_name like 'AKMIX\\_%' escape '\\';")
sql('create-control',"create user "+user+' identified by "'+password+'" default tablespace USERS quota unlimited on USERS;\ngrant create session,create table,create sequence to '+user+';',True)
control=dict(original);control.update(user=user,password=password,database=user);(O/'control.secret.json').write_text(json.dumps(control));(O/'control.secret').write_text(user+'\n'+password+'\n'+control['url']+'\n')
binding=json.loads(sql('approved-mixed-binding',"select BINDING_JSON from ORKRUNTIME_20261005.SH_ORACLE_INPUT_BINDING where SOURCE_ID='oracle-runtime-SMIX' and WINDOW_KEY='runtime-SMIX';").strip());(O/'approved-binding.json').write_text(json.dumps(binding))
t='ORKTGT_CMT_20261005.AKMIX';old='ORKTGT_CMT_20261005.RTMIX'
ddl='create table '+t+'_IDENT(ID number primary key,TARGET_ID varchar2(128) not null,SOURCE_BINDING varchar2(512) not null,SCHEMA_SHA varchar2(64) not null);\n'
ddl+="insert into "+t+"_IDENT select ID,'oracle-dml-AKMIX-20261005',SOURCE_BINDING,SCHEMA_SHA from "+old+'_IDENT;\n'
ddl+='create table '+t+'_ROWS(ID number primary key,TXT varchar2(128),NOTE varchar2(128));\ninsert into '+t+'_ROWS select ID,TXT,NOTE from '+old+'_ROWS;\n'
ddl+='create table '+t+'_APPLIED(EVENT_ID varchar2(80) primary key,PAYLOAD_SHA varchar2(64) not null,BEFORE_IMAGE varchar2(4000),AFTER_IMAGE varchar2(4000),OPERATION varchar2(16) not null);\n'
ddl+='create table '+t+'_MEMBERS(BATCH_SHA varchar2(64),ORDINAL number,EVENT_ID varchar2(80) references '+t+'_APPLIED(EVENT_ID),primary key(BATCH_SHA,ORDINAL),unique(BATCH_SHA,EVENT_ID));\n'
ddl+='create table '+t+'_COMMITS(BATCH_SHA varchar2(64) primary key,TARGET_ID varchar2(128) not null,SCHEMA_SHA varchar2(64) not null,EVENTS_COUNT number not null);\n'
sql('create-mixed-target',ddl+'commit;');print('OWNED_HTTP_MIXED_RESOURCES_PREPARED')
