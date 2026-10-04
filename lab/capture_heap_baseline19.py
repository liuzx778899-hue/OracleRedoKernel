"""Read-only fixed-SCN baseline acquisition on the dedicated project-owned 19c fixture; never replay DML."""
import datetime,hashlib,json,subprocess,sys
from pathlib import Path
ROOT=Path(__file__).resolve().parent.parent;CONTAINER='oraredo-19c-lab-20261004'
if len(sys.argv)!=3 or sys.argv[1]!='--execute':raise RuntimeError('usage: --execute fresh-output-directory')
out=Path(sys.argv[2]);out.mkdir(parents=True,exist_ok=False)
def run(name,args):
 p=subprocess.run(args,capture_output=True)
 for ext,data in [('stdout',p.stdout),('stderr',p.stderr)]: (out/(name+'.'+ext+'.txt')).write_bytes(data)
 (out/(name+'.rc.txt')).write_text(str(p.returncode));(out/(name+'-command.json')).write_text(json.dumps(args))
 if p.returncode:raise RuntimeError(name+': preserve failure; no retry or configuration fallback')
 return p.stdout
binding=run('binding',['docker','inspect',CONTAINER,'--format','{{.Config.Labels.owner}}|{{.Config.Image}}|{{.State.Status}}'])
if binding.decode().strip()!='OracleRedoKernel|container-registry.oracle.com/database/enterprise:19.3.0.0|running':raise RuntimeError('DEDICATED_SOURCE_MISMATCH')
source="""select '{label}|'||d.dbid||'|'||d.activation#||'|'||i.resetlogs_id||'|'||d.resetlogs_change#||'|'||v.thread#||'|'||p.value from v$database d cross join v$database_incarnation i cross join v$instance v cross join v$parameter p where i.status='CURRENT' and p.name='compatible';"""
def dictionary(phase):
 return """select 'HEAP|{phase}|ORKLAB|T_BASELINE_20261004|'||object_id||'|'||data_object_id from dba_objects where owner='ORKLAB' and object_name='T_BASELINE_20261004' and object_type='TABLE';
select 'INDEX|{phase}|ORKLAB|PK_BASELINE_20261004|'||object_id||'|'||data_object_id from dba_objects where owner='ORKLAB' and object_name='PK_BASELINE_20261004' and object_type='INDEX';
select 'COLUMN|{phase}|'||segment_column_id||'|'||internal_column_id||'|'||column_name||'|'||data_type||'|'||nullable||'|'||hidden_column||'|'||virtual_column from dba_tab_cols where owner='ORKLAB' and table_name='T_BASELINE_20261004' order by segment_column_id;
""".format(phase=phase)
sql="""whenever sqlerror exit failure rollback
whenever oserror exit failure rollback
set pages 0 lines 32767 trimspool on feedback off verify off heading off
select 'FORMAT|ORK_HEAP_SNAPSHOT_V1' from dual;
declare n number;begin select count(*) into n from v$database where dbid=1276726821 and log_mode='ARCHIVELOG' and supplemental_log_data_min='NO' and supplemental_log_data_all='NO';if n!=1 then raise_application_error(-20001,'dedicated source mismatch');end if;end;
/
"""+source.format(label='SOURCE')+"""
select 'PDB|'||con_uid||'|'||con_id||'|'||name from v$containers where name='ORKPDB';
alter session set container=ORKPDB;
select 'CHARSET|'||value from nls_database_parameters where parameter='NLS_CHARACTERSET';
column baseline_scn new_value BSCN noprint
select to_char(dbms_flashback.get_system_change_number,'FM99999999999999999999') baseline_scn from dual;
select 'SNAPSHOT_SCN|'||'&BSCN' from dual;
"""+dictionary('BEFORE')+"""
select 'ROW|'||dbms_rowid.rowid_object(rowid)||'|'||dbms_rowid.rowid_relative_fno(rowid)||'|'||dbms_rowid.rowid_block_number(rowid)||'|'||dbms_rowid.rowid_row_number(rowid)||'|'||case when ID is null then 'NULL' else rawtohex(utl_raw.cast_from_number(ID)) end||'|'||case when TXT is null then 'NULL' else rawtohex(utl_raw.cast_to_raw(TXT)) end||'|'||case when NOTE is null then 'NULL' else rawtohex(utl_raw.cast_to_raw(NOTE)) end from ORKLAB.T_BASELINE_20261004 as of scn &BSCN order by rowid;
select 'ROW_COUNT|'||count(*) from ORKLAB.T_BASELINE_20261004 as of scn &BSCN;
"""+dictionary('AFTER')+"""
alter session set container=CDB$ROOT;
"""+source.format(label='SOURCE_AFTER')+"""
select 'END|ORK_HEAP_SNAPSHOT_V1' from dual;
exit success
"""
path=out/'snapshot.sql';path.write_text(sql,encoding='utf-8');remote='/tmp/ork-read-only-baseline-'+out.name+'.sql'
run('copy-query',['docker','cp',str(path),CONTAINER+':'+remote])
data=run('snapshot',['docker','exec','-e','NLS_LANG=.AL32UTF8',CONTAINER,'sqlplus','-s','/','as','sysdba','@'+remote])
lines=[x.strip() for x in data.decode('utf-8').splitlines() if x.strip()]
if not lines or lines[0]!='FORMAT|ORK_HEAP_SNAPSHOT_V1' or lines[-1]!='END|ORK_HEAP_SNAPSHOT_V1':raise RuntimeError('INCOMPLETE_SOURCE_QUERY_OUTPUT')
receipt={'kind':'dedicated-read-only-acquisition','acquired_at_utc':datetime.datetime.now(datetime.timezone.utc).isoformat(),'container':CONTAINER,'query_sha256':hashlib.sha256(path.read_bytes()).hexdigest(),'snapshot_sha256':hashlib.sha256(data).hexdigest(),'binding_sha256':hashlib.sha256(binding).hexdigest(),'query_rc':0,'trust_boundary':'digest handed off by this actual acquisition process; an editable adjacent manifest is not an authenticity authority','historical_dictionary_accepted':False,'cdc_events':0}
(out/'receipt.json').write_text(json.dumps(receipt,indent=2)+'\n',encoding='utf-8');print(json.dumps(receipt));print(data.decode())
