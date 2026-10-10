"""Owned temporal byte fixtures; SQL/native are independent rulers, never event inputs."""
from pathlib import Path
import hashlib,json,re,shutil,subprocess,sys,time

R=Path(__file__).resolve().parent.parent
N=R/'lab/out/temporal-variants-20261011/source-r2'
S='oraredo-19c-lab-20261004'
assert sys.argv[1:]==['--execute']
N.mkdir(parents=True,exist_ok=False);shutil.copyfile(__file__,N/'executed.py')
common=(R/'lab/common_fields_source.py').read_text()
exec(common[common.index('reader='):common.index('libs=N/')])
(N/'inputs').mkdir()
table='T_D4_VARIANTS_20261011';started=False
try:
    assert run('state',['docker','inspect',S,'--format','{{.Config.Labels.owner}}|{{.State.Status}}']).strip()=='OracleRedoKernel|exited'
    run('start',['docker','start',S]);started=True
    for attempt in range(30):
        try:
            out=sql('ready-'+str(attempt),"select 'READY|'||open_mode from v$database;")
            if 'READY|READ WRITE' in out:break
        except AssertionError:
            if attempt==29:raise
        time.sleep(3)
    pre=sql('pre',f"select 'CURRENT|'||sequence# from v$log where status='CURRENT';\nselect 'LOGGING|'||supplemental_log_data_min||'|'||supplemental_log_data_all from v$database;\nalter session set container=ORKPDB;\nselect 'FREE|'||count(*) from dba_tables where owner='ORKLAB' and table_name='{table}';\nselect 'DBTZ|'||dbtimezone||'|'||sessiontimezone from dual;\nselect 'TZFILE|'||filename||'|'||version from v$timezone_file;")
    assert 'FREE|0' in pre and 'LOGGING|NO|NO' in pre
    first=int(next(l.split('|')[1] for l in pre.splitlines() if l.startswith('CURRENT|')))
    sql('fixture',f"alter session set container=ORKPDB;\ncreate table ORKLAB.{table}(ID number not null,D date,TS0 timestamp(0),TS3 timestamp(3),TS6 timestamp(6),TS9 timestamp(9),TZ timestamp(9) with time zone,TZR timestamp(6) with time zone,LTZ timestamp(6) with local time zone,constraint PK_D4_VARIANTS_20261011 primary key(ID));\ngrant select,flashback on ORKLAB.{table} to {reader[0]};")
    scn=sql('before-values',"select 'SCN|'||dbms_flashback.get_system_change_number from dual;")
    start_scn=int(next(l.split('|')[1] for l in scn.splitlines() if l.startswith('SCN|')))
    cases=[(1,'2024-03-31 01:30:45','AD','123456789','+05:30','2024-03-31 01:30:45.123456 Europe/Paris'),
           (2,'0044-03-15 12:34:56','BC','987654321','-03:30','2024-03-31 03:30:45.987654 Europe/Paris'),
           (3,'0001-01-01 00:00:00','BC','000000001','+00:00','2024-10-27 03:30:00.000001 Europe/Paris')]
    body="alter session set container=ORKPDB;\nalter session set time_zone='+00:00';\n"
    for id,date,era,fraction,offset,region in cases:
        d=f"to_date('{date} {era}','YYYY-MM-DD HH24:MI:SS AD')"
        ts=f"to_timestamp('{date}.{fraction} {era}','YYYY-MM-DD HH24:MI:SS.FF9 AD')"
        tz=f"to_timestamp_tz('2024-03-31 03:30:45.{fraction} {offset}','YYYY-MM-DD HH24:MI:SS.FF9 TZH:TZM')"
        tzr=f"to_timestamp_tz('{region}','YYYY-MM-DD HH24:MI:SS.FF6 TZR')"
        body+=f"insert into ORKLAB.{table} values({id},{d},{ts},{ts},{ts},{ts},{tz},{tzr},{tzr});\n"
    (N/'values.started').write_text('Execute once; do not replay even if subsequent verification fails.\n')
    sql('values',body+'commit;')
    cols=['D','TS0','TS3','TS6','TS9','TZ','TZR','LTZ']
    for col in cols:
        sql('bytes-'+col,f"alter session set container=ORKPDB;\nalter session set time_zone='+00:00';\nselect 'BYTES|'||ID||'|{col}|'||dump({col},16) from ORKLAB.{table} order by ID;")
    sql('truth',f"alter session set container=ORKPDB;\nalter session set time_zone='+00:00';\nselect 'TRUTH|'||ID||'|'||to_char(D,'YYYY-MM-DD HH24:MI:SS AD')||'|'||to_char(TS0,'YYYY-MM-DD HH24:MI:SS.FF AD')||'|'||to_char(TS3,'YYYY-MM-DD HH24:MI:SS.FF AD')||'|'||to_char(TS6,'YYYY-MM-DD HH24:MI:SS.FF AD')||'|'||to_char(TS9,'YYYY-MM-DD HH24:MI:SS.FF AD')||'|'||to_char(TZ,'YYYY-MM-DD HH24:MI:SS.FF9 TZH:TZM')||'|'||to_char(TZR,'YYYY-MM-DD HH24:MI:SS.FF6 TZH:TZM TZR TZD')||'|'||to_char(LTZ,'YYYY-MM-DD HH24:MI:SS.FF6') from ORKLAB.{table} order by ID;\nselect 'OBJECT|'||object_id||'|'||data_object_id from dba_objects where owner='ORKLAB' and object_name='{table}' and object_type='TABLE';")
    out=sql('close',"select 'CURRENT|'||sequence# from v$log where status='CURRENT';\nalter system archive log current;")
    last=int(next(l.split('|')[1] for l in out.splitlines() if l.startswith('CURRENT|')));archives=[]
    for sequence in range(first,last+1):
        out=sql('archive-'+str(sequence),f"select 'PATH|'||name from v$archived_log where sequence#={sequence} and archived='YES' and deleted='NO' and name is not null order by recid;")
        paths=set(l.split('|')[1] for l in out.splitlines() if l.startswith('PATH|'));assert len(paths)==1
        path=paths.pop();local=N/'inputs'/f'{sequence}.raw'
        run('copy-'+str(sequence),['docker','cp',S+':'+path,local])
        assert run('sha-'+str(sequence),['docker','exec',S,'sha256sum',path]).split()[0]==sha(local)
        archives.append(dict(sequence=sequence,path=str(local),sourcePath=path,sha256=sha(local),bytes=local.stat().st_size))
        out=sql('native-'+str(sequence),f"alter session set tracefile_identifier='ORK_TEMPORAL_VARIANTS';\nalter system dump logfile '{path}' scn min {start_scn};\nselect 'TRACE|'||value from v$diag_info where name='Default Trace File';")
        traces=[l.split('|',1)[1] for l in out.splitlines() if l.startswith('TRACE|')];assert len(traces)==1
        run('native-copy-'+str(sequence),['docker','cp',S+':'+traces[0],N/f'{sequence}.trc'])
    (N/'archives.json').write_text(json.dumps(archives,indent=2))
    print('TEMPORAL_VARIANTS_FROZEN',flush=True)
finally:
    if started:run('source-stop',['docker','stop','-t','60',S])
