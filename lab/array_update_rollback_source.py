"""Freeze one owned 11.19 UPDATE rollback/savepoint experiment; never replay actions."""
from pathlib import Path
import hashlib,json,re,shutil,subprocess,sys,time
R=Path(__file__).resolve().parent.parent;N=R/'lab/out/array-update-rollback-20261011/source'
S='oraredo-19c-lab-20261004';H='ork-product-runtime'
assert sys.argv[1:]==['--execute'];N.mkdir(parents=True,exist_ok=False);shutil.copyfile(__file__,N/'executed.py')
common=(R/'lab/common_fields_source.py').read_text();exec(common[common.index('reader='):common.index('libs=N/')])
libs=N/'libs';shutil.copytree(R/'lab/out/array-temporal-product-20261011/runtime-r2/libs',libs)
(N/'classes').mkdir();(N/'inputs').mkdir();shutil.copyfile(R/'lab/out/dictionary-baseline-candidate-20261006/source.secret',N/'source.secret')
run('compile',['D:/work/Java/jdk-17.0.7/bin/javac.exe','-cp',str(libs/'*'),'-d',N/'classes',R/'lab/OracleSnapshotAcquisitionProbe.java',R/'lab/ArrayTemporalProductProbe.java'])
table='T_D2_AURP_20261011';index='PK_D2_AURP_20261011';started=False
def acquire(label):
    out=run(label,['docker','run','--rm','--name',H,'--label','owner=OracleRedoKernel','--network','container:'+S,'--mount','type=bind,source='+str(N)+',target=/evidence','--entrypoint','java','flink:1.18-java17','-cp','/evidence/classes:/evidence/libs/*','OracleSnapshotAcquisitionProbe','/evidence/source.secret',label,'ORKLAB',table,index,'history-normal','/evidence/'+label+'.snapshot'])
    assert out.startswith('ACQUIRED|'+label+'|');return dict(scn=int(out.split('|')[2]),sha256=sha(N/(label+'.snapshot')))
try:
    assert run('state',['docker','inspect',S,'--format','{{.Config.Labels.owner}}|{{.State.Status}}']).strip()=='OracleRedoKernel|exited'
    assert H not in run('helpers',['docker','ps','-a','--format','{{.Names}}']).splitlines()
    run('start',['docker','start',S]);started=True
    for attempt in range(30):
        try:
            if 'READY|READ WRITE' in sql('ready-'+str(attempt),"select 'READY|'||open_mode from v$pdbs where name='ORKPDB';"):break
        except AssertionError:
            if attempt==29:raise
        time.sleep(3)
    else:raise AssertionError('PDB_NOT_READY')
    pre=sql('pre',f"select 'CURRENT|'||sequence# from v$log where status='CURRENT';\nselect 'LOGGING|'||supplemental_log_data_min||'|'||supplemental_log_data_all from v$database;\nalter session set container=ORKPDB;\nselect 'FREE|'||count(*) from dba_tables where owner='ORKLAB' and table_name='{table}';")
    assert 'FREE|0' in pre and 'LOGGING|NO|NO' in pre;first=int(next(l.split('|')[1] for l in pre.splitlines() if l.startswith('CURRENT|')))
    sql('fixture',f"""alter session set container=ORKPDB;
create table ORKLAB.{table}(ID number not null,TZ timestamp(9) with time zone,LTZ timestamp(9) with local time zone,constraint {index} primary key(ID));
insert into ORKLAB.{table} select level,to_timestamp_tz('2024-03-30 03:30:45.123456789 +05:30','YYYY-MM-DD HH24:MI:SS.FF9 TZH:TZM'),to_timestamp_tz('2024-03-30 03:30:45.123456789 +05:30','YYYY-MM-DD HH24:MI:SS.FF9 TZH:TZM') from dual connect by level<=3;
commit;
grant select,flashback on ORKLAB.{table} to {reader[0]};""")
    before=acquire('baseline');(N/'snapshot.json').write_text(json.dumps(before,indent=2))
    rolled="to_timestamp_tz('2025-01-01 12:34:56.987654321 -03:30','YYYY-MM-DD HH24:MI:SS.FF9 TZH:TZM')"
    kept="to_timestamp_tz('2024-03-31 03:30:45.000000001 Europe/Paris','YYYY-MM-DD HH24:MI:SS.FF9 TZR')"
    (N/'actions.started').write_text('single new source execution; never replay\n')
    sql('actions',f"""alter session set container=ORKPDB;
begin
 update ORKLAB.{table} set TZ={rolled},LTZ={rolled};
 if sql%rowcount<>3 then raise_application_error(-20001,'FULL_ROLLBACK_COUNT');end if;
 rollback;
 update ORKLAB.{table} set TZ={kept},LTZ={kept};
 if sql%rowcount<>3 then raise_application_error(-20002,'KEEP_COUNT');end if;
 savepoint keep_three;
 update ORKLAB.{table} set TZ={rolled},LTZ={rolled};
 if sql%rowcount<>3 then raise_application_error(-20003,'SAVEPOINT_COUNT');end if;
 rollback to keep_three;
 commit;
end;
/
alter session set time_zone='+00:00';
select 'ROW|'||ID||'|'||to_char(TZ,'YYYY-MM-DD HH24:MI:SS.FF9 TZR')||'|'||to_char(LTZ,'YYYY-MM-DD HH24:MI:SS.FF9') from ORKLAB.{table} order by ID;""")
    after=acquire('after');(N/'after.json').write_text(json.dumps(after,indent=2))
    closed=sql('close',"select 'CURRENT|'||sequence# from v$log where status='CURRENT';\nalter system archive log current;");last=int(next(l.split('|')[1] for l in closed.splitlines() if l.startswith('CURRENT|')));archives=[]
    for seq in range(first,last+1):
        out=sql('archive-'+str(seq),f"select 'PATH|'||name from v$archived_log where sequence#={seq} and archived='YES' and deleted='NO' and name is not null order by recid;");paths=set(l.split('|')[1] for l in out.splitlines() if l.startswith('PATH|'));assert len(paths)==1
        path=paths.pop();local=N/'inputs'/f'{seq}.raw';run('copy-'+str(seq),['docker','cp',S+':'+path,local]);assert run('sha-'+str(seq),['docker','exec',S,'sha256sum',path]).split()[0]==sha(local)
        archives.append(dict(sequence=seq,path=str(local),sourcePath=path,sha256=sha(local),bytes=local.stat().st_size))
        trace=sql('native-'+str(seq),f"alter session set tracefile_identifier='ORK_ARRAY_UPDATE_ROLLBACK';\nalter system dump logfile '{path}' scn min {before['scn']};\nselect 'TRACE|'||value from v$diag_info where name='Default Trace File';");tracepath=next(l.split('|',1)[1] for l in trace.splitlines() if l.startswith('TRACE|'));run('native-copy-'+str(seq),['docker','cp',S+':'+tracepath,N/f'{seq}.trc'])
    (N/'archives.json').write_text(json.dumps(archives,indent=2));print('ARRAY_UPDATE_ROLLBACK_FROZEN',flush=True)
finally:
    if started:run('source-stop',['docker','stop','-t','60',S])
