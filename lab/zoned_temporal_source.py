"""Owned source V4 acquisition followed by one future timezone UPDATE; no source configuration changes."""
from pathlib import Path
import hashlib,json,re,shutil,subprocess,sys,time
R=Path(__file__).resolve().parent.parent;BASE=R/'lab/out/zoned-temporal-20261011';N=BASE/'source-r2'
S='oraredo-19c-lab-20261004';H='ork-product-runtime'
assert sys.argv[1:]==['--execute'];N.mkdir(parents=True,exist_ok=False);shutil.copyfile(__file__,N/'executed.py')
assert (BASE/'source/source-stop.rc').read_text().strip()=='0' and not (BASE/'source/fixture.command.json').exists()
common=(R/'lab/common_fields_source.py').read_text();exec(common[common.index('reader='):common.index('libs=N/')])
libs=N/'libs';shutil.copytree(R/'lab/out/common-fields-20261010/source/libs',libs);(N/'classes').mkdir();(N/'inputs').mkdir()
shutil.copyfile(R/'target/oracle-redo-kernel-0.1.0-SNAPSHOT.jar',libs/'kernel.jar')
shutil.copyfile(R/'integration/streamhouse-engine-oracle/target/streamhouse-engine-oracle-0.1.0-SNAPSHOT.jar',libs/'adapter.jar')
shutil.copyfile(R/'lab/out/dictionary-baseline-candidate-20261006/source.secret',N/'source.secret')
run('compile',['D:/work/Java/jdk-17.0.7/bin/javac.exe','-cp',str(libs/'*'),'-d',N/'classes',R/'lab/OracleSnapshotAcquisitionProbe.java',R/'lab/ZonedTemporalBatchProbe.java'])
table='T_D4_ZONED_20261011';index='PK_D4_ZONED_20261011';started=False
try:
    assert run('state',['docker','inspect',S,'--format','{{.Config.Labels.owner}}|{{.State.Status}}']).strip()=='OracleRedoKernel|exited'
    assert H not in run('helpers',['docker','ps','-a','--format','{{.Names}}']).splitlines()
    run('start',['docker','start',S]);started=True
    for attempt in range(30):
        try:
            out=sql('ready-'+str(attempt),"select 'READY|'||open_mode from v$pdbs where name='ORKPDB';")
            if 'READY|READ WRITE' in out:break
        except AssertionError:
            if attempt==29:raise
        time.sleep(3)
    pre=sql('pre',f"select 'CURRENT|'||sequence# from v$log where status='CURRENT';\nselect 'LOGGING|'||supplemental_log_data_min||'|'||supplemental_log_data_all from v$database;\nalter session set container=ORKPDB;\nselect 'FREE|'||count(*) from dba_tables where owner='ORKLAB' and table_name='{table}';")
    assert 'FREE|0' in pre and 'LOGGING|NO|NO' in pre;first=int(next(l.split('|')[1] for l in pre.splitlines() if l.startswith('CURRENT|')))
    sql('fixture',f"alter session set container=ORKPDB;\ncreate table ORKLAB.{table}(ID number not null,TZ timestamp(9) with time zone,LTZ timestamp(9) with local time zone,constraint {index} primary key(ID));\ninsert into ORKLAB.{table} select level,to_timestamp_tz('2024-03-30 03:30:45.123456789 +05:30','YYYY-MM-DD HH24:MI:SS.FF9 TZH:TZM'),to_timestamp_tz('2024-03-30 03:30:45.123456789 +05:30','YYYY-MM-DD HH24:MI:SS.FF9 TZH:TZM') from dual connect by level<=3;\ncommit;\ngrant select,flashback on ORKLAB.{table} to {reader[0]};")
    acquired=run('acquire',['docker','run','--rm','--name',H,'--label','owner=OracleRedoKernel','--network','container:'+S,'--mount','type=bind,source='+str(N)+',target=/evidence','--entrypoint','java','flink:1.18-java17','-cp','/evidence/classes:/evidence/libs/*','OracleSnapshotAcquisitionProbe','/evidence/source.secret','zoned-future','ORKLAB',table,index,'history-normal','/evidence/baseline.snapshot'])
    assert acquired.startswith('ACQUIRED|zoned-future|');scn=int(acquired.split('|')[2]);(N/'snapshot.json').write_text(json.dumps(dict(scn=scn,sha256=sha(N/'baseline.snapshot')),indent=2))
    (N/'future-update.started').write_text('One future UPDATE after source-owned V4 acquisition; do not replay.\n')
    cases="case ID when 1 then to_timestamp_tz('2024-03-31 01:30:45.123456789 Europe/Paris','YYYY-MM-DD HH24:MI:SS.FF9 TZR') when 2 then to_timestamp_tz('2024-03-31 03:30:45.987654321 Europe/Paris','YYYY-MM-DD HH24:MI:SS.FF9 TZR') else to_timestamp_tz('2024-03-31 03:30:45.000000001 -03:30','YYYY-MM-DD HH24:MI:SS.FF9 TZH:TZM') end"
    sql('future-update',f"alter session set container=ORKPDB;\nupdate ORKLAB.{table} set TZ={cases},LTZ={cases};\ncommit;")
    sql('truth',f"alter session set container=ORKPDB;\nalter session set time_zone='+00:00';\nselect 'ROW|'||ID||'|'||to_char(TZ,'YYYY-MM-DD HH24:MI:SS.FF9 TZH:TZM TZR TZD')||'|'||to_char(LTZ,'YYYY-MM-DD HH24:MI:SS.FF9') from ORKLAB.{table} order by ID;")
    closed=sql('close',"select 'CURRENT|'||sequence# from v$log where status='CURRENT';\nalter system archive log current;");last=int(next(l.split('|')[1] for l in closed.splitlines() if l.startswith('CURRENT|')));archives=[]
    for seq in range(first,last+1):
        out=sql('archive-'+str(seq),f"select 'PATH|'||name from v$archived_log where sequence#={seq} and archived='YES' and deleted='NO' and name is not null order by recid;");paths=set(l.split('|')[1] for l in out.splitlines() if l.startswith('PATH|'));assert len(paths)==1
        path=paths.pop();local=N/'inputs'/f'{seq}.raw';run('copy-'+str(seq),['docker','cp',S+':'+path,local]);assert run('sha-'+str(seq),['docker','exec',S,'sha256sum',path]).split()[0]==sha(local)
        archives.append(dict(sequence=seq,path=str(local),sourcePath=path,sha256=sha(local),bytes=local.stat().st_size))
        trace=sql('native-'+str(seq),f"alter session set tracefile_identifier='ORK_ZONED_FUTURE';\nalter system dump logfile '{path}' scn min {scn};\nselect 'TRACE|'||value from v$diag_info where name='Default Trace File';");tracepath=next(l.split('|',1)[1] for l in trace.splitlines() if l.startswith('TRACE|'));run('native-copy-'+str(seq),['docker','cp',S+':'+tracepath,N/f'{seq}.trc'])
    (N/'archives.json').write_text(json.dumps(archives,indent=2));print('ZONED_FUTURE_FROZEN',flush=True)
finally:
    if started:run('source-stop',['docker','stop','-t','60',S])
