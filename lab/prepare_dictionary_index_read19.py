"""Owned lab reader-only permission experiment. No shared source or business schema changes."""
import json,subprocess
from pathlib import Path
R=Path(__file__).resolve().parent.parent;O=R/'lab/out/dictionary-baseline-candidate-20261006';S='oraredo-19c-lab-20261004'
assert subprocess.check_output(['docker','inspect',S,'--format','{{.Config.Labels.owner}}|{{.State.Status}}']).decode().strip()=='OracleRedoKernel|running'
assert not (O/'owned-reader-index-grant.command.json').exists()
reader=(O/'source.secret').read_text().splitlines()[0];assert reader=='ORKACQ_20261005'
body="""whenever sqlerror exit failure rollback
whenever oserror exit failure rollback
set pages 0 lines 32767 feedback off heading off trimspool on
declare n number;begin select count(*) into n from v$database where dbid=1276726821 and activation#=1276717861 and supplemental_log_data_min='NO' and supplemental_log_data_all='NO';if n!=1 then raise_application_error(-20001,'source boundary mismatch');end if;end;
/
alter session set container=ORKPDB;
declare n number;begin select count(*) into n from v$containers where con_id=3 and con_uid=677737149 and name='ORKPDB';if n!=1 then raise_application_error(-20002,'container boundary mismatch');end if;end;
/
select 'BEFORE_PRIV|'||privilege from dba_tab_privs where grantee='ORKACQ_20261005' and owner='SYS' and table_name='IND$' order by privilege;
grant select,flashback on SYS.IND$ to ORKACQ_20261005;
select 'AFTER_PRIV|'||privilege from dba_tab_privs where grantee='ORKACQ_20261005' and owner='SYS' and table_name='IND$' order by privilege;
select 'OLD|'||ID||'|'||TXT||'|'||NOTE from ORKLAB.T_BASELINE_20261004;
select 'LOGGING|'||supplemental_log_data_min||'|'||supplemental_log_data_all from v$database;
exit success
"""
(O/'owned-reader-index-grant.sql').write_text(body);(O/'executed-owned-reader-index-grant.py').write_bytes(Path(__file__).read_bytes())
args=['docker','exec','-i',S,'sqlplus','-s','/','as','sysdba'];(O/'owned-reader-index-grant.command.json').write_text(json.dumps(args));p=subprocess.run(args,input=body.encode(),capture_output=True,timeout=60)
for suffix,data in [('stdout.txt',p.stdout),('stderr.txt',p.stderr),('rc.txt',str(p.returncode).encode())]:(O/('owned-reader-index-grant.'+suffix)).write_bytes(data)
assert p.returncode==0 and b'SP2-' not in p.stdout
assert b'BEFORE_PRIV' not in p.stdout and b'AFTER_PRIV|FLASHBACK' in p.stdout and b'AFTER_PRIV|SELECT' in p.stdout
assert b'OLD|11|blocking-closed|new-note' in p.stdout and b'LOGGING|NO|NO' in p.stdout
(O/'owned-reader-index-grant-proof.json').write_text(json.dumps(dict(source_lab_permission_change=True,business_ddl_dml=0,logging_change=False,shared_source_actions=0,production_zero_intrusion_proven=False),indent=2))
print('Owned lab reader IND$ SELECT/FLASHBACK recorded; business rows and logging retained.')
