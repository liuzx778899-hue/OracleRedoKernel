"""One-shot concurrent index position experiment in the dedicated 19c lab only."""
import datetime, hashlib, json, re, subprocess, sys, time
from pathlib import Path

ROOT=Path(__file__).resolve().parent.parent
CONTAINER='oraredo-19c-lab-20261004'
OUT=ROOT/'lab/out/oracle19-index-slot-shift-20261004-r2'
if sys.argv[1:]!=['--execute']:raise RuntimeError('explicit --execute required; never retry DML')
OUT.mkdir(parents=True,exist_ok=False)
def stamp():return datetime.datetime.now(datetime.timezone.utc).isoformat()
def record(name,args,rc,started):
    (OUT/(name+'.rc.txt')).write_text(str(rc)+'\n')
    with (OUT/'commands.jsonl').open('a',encoding='utf-8') as f:
        f.write(json.dumps({'name':name,'started_utc':started,'finished_utc':stamp(),'command':args,'rc':rc})+'\n')
def run(name,args):
    started=stamp();p=subprocess.run(args,capture_output=True)
    (OUT/(name+'.stdout.txt')).write_bytes(p.stdout);(OUT/(name+'.stderr.txt')).write_bytes(p.stderr)
    record(name,args,p.returncode,started)
    if p.returncode:raise RuntimeError(name+': failed; preserve evidence, never replay DML')
    return p.stdout.decode('utf-8')
def sql(name,body):
    path=OUT/(name+'.sql')
    path.write_text('whenever sqlerror exit failure rollback\nwhenever oserror exit failure rollback\nset pages 0 lines 32767 trimspool on feedback off\n'+body+'\nexit success\n',encoding='utf-8')
    remote='/tmp/ork-index-shift-'+name+'.sql'
    run('copy-'+name,['docker','cp',str(path),CONTAINER+':'+remote])
    return ['docker','exec','-e','NLS_LANG=.AL32UTF8',CONTAINER,'sqlplus','-s','/','as','sysdba','@'+remote]
binding=run('binding',['docker','inspect',CONTAINER,'--format','{{.Config.Labels.owner}}|{{.Config.Image}}|{{.State.Status}}'])
if binding.strip()!='OracleRedoKernel|container-registry.oracle.com/database/enterprise:19.3.0.0|running':raise RuntimeError('DEDICATED_SOURCE_MISMATCH')
run('fresh-marker',['docker','exec',CONTAINER,'sh','-c','test ! -e /tmp/ork-index-shift-A-ready'])
setup=run('setup',sql('setup',"""
select 'START_SCN|'||to_char(current_scn,'FM99999999999999999999') from v$database;
select 'SOURCE|'||name||'|'||dbid||'|'||log_mode||'|'||supplemental_log_data_min||'|'||supplemental_log_data_all from v$database;
declare id number; lab_mode varchar2(30); ml varchar2(30); al varchar2(30);
begin
  select dbid,log_mode,supplemental_log_data_min,supplemental_log_data_all into id,lab_mode,ml,al from v$database;
  if id!=1276726821 or lab_mode!='ARCHIVELOG' or ml!='NO' or al!='NO' then raise_application_error(-20001,'source mismatch'); end if;
end;
/
alter session set container=ORKPDB;
create table ORKLAB.T_INDEX_SHIFT_20261004 (ID number,TXT varchar2(100),constraint PK_INDEX_SHIFT_20261004 primary key(ID));
insert into ORKLAB.T_INDEX_SHIFT_20261004 values (1,'one');
insert into ORKLAB.T_INDEX_SHIFT_20261004 values (3,'three');
select 'BASE_TX|'||dbms_transaction.local_transaction_id from dual;
commit;
"""))
a_args=sql('session-a',"""
alter session set container=ORKPDB;
delete from ORKLAB.T_INDEX_SHIFT_20261004 where ID=3;
select 'A_TX|'||dbms_transaction.local_transaction_id from dual;
select 'A_DELETED_SCN|'||to_char(current_scn,'FM99999999999999999999') from v$database;
host touch /tmp/ork-index-shift-A-ready
begin dbms_session.sleep(20); end;
/
select 'A_BEFORE_ROLLBACK_SCN|'||to_char(current_scn,'FM99999999999999999999') from v$database;
rollback;
select 'A_AFTER_TX|'||nvl(dbms_transaction.local_transaction_id,'NONE') from dual;
""")
b_args=sql('session-b',"""
alter session set container=ORKPDB;
insert into ORKLAB.T_INDEX_SHIFT_20261004 values (2,'two');
select 'B_TX|'||dbms_transaction.local_transaction_id from dual;
commit;
select 'B_COMMITTED_SCN|'||to_char(current_scn,'FM99999999999999999999') from v$database;
""")
started=stamp()
with (OUT/'session-a.stdout.txt').open('wb') as stdout,(OUT/'session-a.stderr.txt').open('wb') as stderr:
    a=subprocess.Popen(a_args,stdout=stdout,stderr=stderr)
    ready=False
    for poll in range(30):
        probe=subprocess.run(['docker','exec',CONTAINER,'test','-f','/tmp/ork-index-shift-A-ready'],capture_output=True)
        with (OUT/'coordination.jsonl').open('a') as f:f.write(json.dumps({'utc':stamp(),'poll':poll,'a_rc':a.poll(),'ready_rc':probe.returncode})+'\n')
        if probe.returncode==0 and a.poll() is None:ready=True;break
        if a.poll() is not None:break
        time.sleep(.2)
    if not ready:raise RuntimeError('A_NOT_CONFIRMED_ACTIVE; no B executed; never replay A')
    run('session-b',b_args)
    live=a.poll() is None
    (OUT/'b-finished-a-live.json').write_text(json.dumps({'utc':stamp(),'a_live':live}))
    rc=a.wait(timeout=45);record('session-a',a_args,rc,started)
    if rc or not live:raise RuntimeError('A_FAILURE_OR_NO_OVERLAP; never replay DML')
end_text=run('truth-capture',sql('truth-capture',"""
alter session set container=ORKPDB;
select 'FINAL_TRUTH|'||ID||'|'||TXT||'|'||dump(ID,16)||'|'||dump(TXT,16) from ORKLAB.T_INDEX_SHIFT_20261004 order by ID;
select 'OBJECT|'||object_name||'|'||object_id||'|'||data_object_id from dba_objects where owner='ORKLAB' and object_name in ('T_INDEX_SHIFT_20261004','PK_INDEX_SHIFT_20261004') order by object_name;
select 'HEAP_ROW|'||ID||'|'||dbms_rowid.rowid_relative_fno(rowid)||'|'||dbms_rowid.rowid_block_number(rowid)||'|'||dbms_rowid.rowid_row_number(rowid) from ORKLAB.T_INDEX_SHIFT_20261004 order by ID;
alter session set container=CDB$ROOT;
select 'END_SCN|'||to_char(current_scn,'FM99999999999999999999') from v$database;
alter system archive log current;
select 'ARCHIVE|'||thread#||'|'||sequence#||'|'||to_char(first_change#,'FM99999999999999999999')||'|'||to_char(next_change#,'FM99999999999999999999')||'|'||blocks||'|'||block_size||'|'||name from v$archived_log where name is not null order by recid;
"""))
start=int(re.search(r'^START_SCN\|(\d+)',setup,re.M)[1]);end=int(re.search(r'^END_SCN\|(\d+)',end_text,re.M)[1]);files=[]
for line in end_text.splitlines():
    p=line.strip().split('|')
    if len(p)!=8 or p[0]!='ARCHIVE' or int(p[4])<=start or int(p[3])>end:continue
    local=OUT/('archive-'+p[1]+'-'+p[2]+'.dbf')
    run('capture-'+p[2],['docker','cp',CONTAINER+':'+p[7],str(local)])
    files.append({'sequence':int(p[2]),'source':p[7],'path':local.name,'bytes':local.stat().st_size,'sha256':hashlib.sha256(local.read_bytes()).hexdigest()})
if not files:raise RuntimeError('ZERO_ARCHIVES')
dump=run('dump',sql('dump',"alter session set tracefile_identifier='ORK_INDEX_SHIFT';\n"+''.join("alter system dump logfile '"+x['source']+"' scn min "+str(start)+" scn max "+str(end)+";\n" for x in files)+"select value from v$diag_info where name='Default Trace File';"))
paths=[s.strip() for s in dump.splitlines() if s.strip().startswith('/opt/oracle/') and s.strip().endswith('.trc')]
if len(paths)!=1:raise RuntimeError('TRACE_PATH_AMBIGUOUS')
run('copy-trace',['docker','cp',CONTAINER+':'+paths[0],str(OUT/'dump.trc')])
manifest={'source':CONTAINER,'start_scn':start,'end_scn':end,'files':files,'source_actions_rc':0,'a_live_after_b':live,'scope':'concurrent fixture only; parser/complete DML acceptance pending'}
(OUT/'manifest.json').write_text(json.dumps(manifest,indent=2))
print(json.dumps(manifest,indent=2))
