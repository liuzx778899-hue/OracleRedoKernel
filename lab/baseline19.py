"""One-shot no-supplemental SCN baseline/invisible-column experiment, dedicated 19c only."""
import datetime,hashlib,json,re,subprocess,sys
from pathlib import Path

ROOT=Path(__file__).resolve().parent.parent;CONTAINER='oraredo-19c-lab-20261004'
OUT=ROOT/'lab/out/oracle19-baseline-20261004'
resume=sys.argv[1:]==['--resume-baseline-read']
if sys.argv[1:] not in (['--execute'],['--resume-baseline-read']):raise RuntimeError('explicit mode required; never replay DML')
if resume:
    if 'ORA-01466' not in (OUT/'setup.stdout.txt').read_text(encoding='utf-8') or (OUT/'changes.stdout.txt').exists() or (OUT/'setup-resume.stdout.txt').exists():raise RuntimeError('RESUME_NOT_A_FRESH_READ_FAILURE_RECOVERY')
else:OUT.mkdir(parents=True,exist_ok=False)
def run(name,args):
    started=datetime.datetime.now(datetime.timezone.utc).isoformat();p=subprocess.run(args,capture_output=True)
    (OUT/(name+'.stdout.txt')).write_bytes(p.stdout);(OUT/(name+'.stderr.txt')).write_bytes(p.stderr);(OUT/(name+'.rc.txt')).write_text(str(p.returncode))
    with (OUT/'commands.jsonl').open('a',encoding='utf-8') as f:f.write(json.dumps({'name':name,'started_utc':started,'command':args,'rc':p.returncode})+'\n')
    if p.returncode:raise RuntimeError(name+': failed; preserve evidence, never replay DML')
    return p.stdout.decode('utf-8')
def sql(name,body):
    path=OUT/(name+'.sql');path.write_text('whenever sqlerror exit failure rollback\nwhenever oserror exit failure rollback\nset pages 0 lines 32767 trimspool on feedback off verify off\n'+body+'\nexit success\n',encoding='utf-8')
    remote='/tmp/ork-baseline-'+name+'.sql';run('copy-'+name,['docker','cp',str(path),CONTAINER+':'+remote])
    return run(name,['docker','exec','-e','NLS_LANG=.AL32UTF8',CONTAINER,'sqlplus','-s','/','as','sysdba','@'+remote])
binding=run('binding-resume' if resume else 'binding',['docker','inspect',CONTAINER,'--format','{{.Config.Labels.owner}}|{{.Config.Image}}|{{.State.Status}}'])
if binding.strip()!='OracleRedoKernel|container-registry.oracle.com/database/enterprise:19.3.0.0|running':raise RuntimeError('DEDICATED_SOURCE_MISMATCH')
preflight=sql('preflight-resume' if resume else 'preflight',"""
select 'SOURCE|'||name||'|'||dbid||'|'||log_mode||'|'||supplemental_log_data_min||'|'||supplemental_log_data_all from v$database;
declare id number; mode_ varchar2(30); ml varchar2(30); al varchar2(30);
begin select dbid,log_mode,supplemental_log_data_min,supplemental_log_data_all into id,mode_,ml,al from v$database;
if id!=1276726821 or mode_!='ARCHIVELOG' or ml!='NO' or al!='NO' then raise_application_error(-20001,'source mismatch');end if;end;
/
select 'ACTIVE_TRANSACTION|'||xidusn||'.'||xidslot||'.'||xidsqn from v$transaction;
alter session set container=ORKPDB;
select 'EXISTING_TABLE|'||table_name from dba_tables where owner='ORKLAB' and table_name='T_BASELINE_20261004';
""")
if ('EXISTING_TABLE|' in preflight)!=resume:raise RuntimeError('FIXTURE_TABLE_STATE_MISMATCH')
def metadata(label):return "select '"+label+"|'||object_name||'|'||object_id||'|'||data_object_id from dba_objects where owner='ORKLAB' and object_name in ('T_BASELINE_20261004','PK_BASELINE_20261004') order by object_name;\n"+"select 'COLUMN|"+label+"|'||column_name||'|'||nvl(to_char(column_id),'NULL')||'|'||segment_column_id||'|'||internal_column_id||'|'||data_type||'|'||nullable||'|'||hidden_column||'|'||virtual_column from dba_tab_cols where owner='ORKLAB' and table_name='T_BASELINE_20261004' order by segment_column_id;\n"
def rows(label,scn):return "select '"+label+"|'||dbms_rowid.rowid_object(rowid)||'|'||dbms_rowid.rowid_relative_fno(rowid)||'|'||dbms_rowid.rowid_block_number(rowid)||'|'||dbms_rowid.rowid_row_number(rowid)||'|'||rawtohex(utl_raw.cast_from_number(ID))||'|'||case when TXT is null then 'NULL' else rawtohex(utl_raw.cast_to_raw(TXT)) end||'|'||case when NOTE is null then 'NULL' else rawtohex(utl_raw.cast_to_raw(NOTE)) end from ORKLAB.T_BASELINE_20261004 as of scn "+scn+" order by ID;\n"
setup_body="""
select 'START_SCN|'||to_char(current_scn,'FM99999999999999999999') from v$database;
alter session set container=ORKPDB;
create table ORKLAB.T_BASELINE_20261004 (ID number,TXT varchar2(100),NOTE varchar2(100) invisible,constraint PK_BASELINE_20261004 primary key(ID));
insert into ORKLAB.T_BASELINE_20261004 (ID,TXT,NOTE) values(10,'alpha','secret');
select 'BASE_TX|'||dbms_transaction.local_transaction_id from dual;
commit;
column baseline_scn new_value BSCN noprint
select to_char(dbms_flashback.get_system_change_number,'FM99999999999999999999') baseline_scn from dual;
select 'BASELINE_SCN|'||'&BSCN' from dual;
"""+metadata('BEFORE')+rows('BASELINE','&BSCN')
if resume:
    setup_body="alter session set container=ORKPDB;\n"+setup_body.split('column baseline_scn',1)[1]
    setup_body=setup_body.replace(' new_value BSCN noprint','column baseline_scn new_value BSCN noprint',1)
setup=sql('setup-resume' if resume else 'setup',setup_body)
original_setup=(OUT/'setup.stdout.txt').read_text(encoding='utf-8')
baseline=int(re.search(r'^BASELINE_SCN\|(\d+)',setup,re.M)[1]);start=int(re.search(r'^START_SCN\|(\d+)',original_setup,re.M)[1])
changes=sql('changes',"""
alter session set container=ORKPDB;
update ORKLAB.T_BASELINE_20261004 set TXT='beta' where ID=10;
select 'UPDATE_TX|'||dbms_transaction.local_transaction_id from dual;
update ORKLAB.T_BASELINE_20261004 set NOTE=null where ID=10;
update ORKLAB.T_BASELINE_20261004 set TXT='gamma' where ID=10;
commit;
column end_scn new_value ESCN noprint
select to_char(dbms_flashback.get_system_change_number,'FM99999999999999999999') end_scn from dual;
select 'FINAL_SNAPSHOT_SCN|'||'&ESCN' from dual;
"""+metadata('AFTER')+rows('OLD_BASELINE',str(baseline))+rows('FINAL','&ESCN'))
captured=sql('capture',"""
alter session set container=CDB$ROOT;
select 'END_SCN|'||to_char(current_scn,'FM99999999999999999999') from v$database;
alter system archive log current;
select 'ARCHIVE|'||thread#||'|'||sequence#||'|'||to_char(first_change#,'FM99999999999999999999')||'|'||to_char(next_change#,'FM99999999999999999999')||'|'||name from v$archived_log where name is not null order by recid;
""")
end=int(re.search(r'^END_SCN\|(\d+)',captured,re.M)[1]);files=[]
for line in captured.splitlines():
    p=line.strip().split('|')
    if len(p)!=6 or p[0]!='ARCHIVE' or int(p[4])<=start or int(p[3])>end:continue
    local=OUT/('archive-'+p[1]+'-'+p[2]+'.dbf');run('capture-'+p[2],['docker','cp',CONTAINER+':'+p[5],str(local)])
    files.append({'sequence':int(p[2]),'source':p[5],'path':local.name,'bytes':local.stat().st_size,'sha256':hashlib.sha256(local.read_bytes()).hexdigest()})
if not files:raise RuntimeError('ZERO_ARCHIVES')
dump=sql('dump',"alter session set tracefile_identifier='ORK_BASELINE';\n"+''.join("alter system dump logfile '"+f['source']+"' scn min "+str(start)+" scn max "+str(end)+";\n" for f in files)+"select value from v$diag_info where name='Default Trace File';")
traces=[line.strip() for line in dump.splitlines() if line.strip().startswith('/opt/oracle/') and line.strip().endswith('.trc')]
if len(traces)!=1:raise RuntimeError('TRACE_PATH_AMBIGUOUS')
run('copy-trace',['docker','cp',CONTAINER+':'+traces[0],str(OUT/'dump.trc')])
manifest={'source':CONTAINER,'start_scn':start,'baseline_scn':baseline,'end_scn':end,'files':files,'source_actions_rc':0,'scope':'controlled fixed-schema SCN baseline and invisible physical-column research; historical dictionary and DML unaccepted'}
(OUT/'manifest.json').write_text(json.dumps(manifest,indent=2),encoding='utf-8');print(json.dumps(manifest,indent=2))
