"""One post-acquisition BC/precision update; raw acquisition, source/native diagnostics frozen once."""
from pathlib import Path
import hashlib,json,re,shutil,subprocess,sys,time
R=Path(__file__).resolve().parent.parent;BASE=R/'lab/out/bc-temporal-20261011';diagnose=sys.argv[1:]==['--diagnose'];resume=sys.argv[1:]==['--resume-before-update'];N=BASE/('diagnostic-r1' if diagnose else 'source/resume-r1' if resume else 'source')
S='oraredo-19c-lab-20261004';H='ork-product-runtime'
assert diagnose or resume or sys.argv[1:]==['--execute'];N.mkdir(parents=True,exist_ok=False);shutil.copyfile(__file__,N/'executed.py')
if resume:
    assert (BASE/'source/source-stop.rc').read_text().strip()=='0' and not (BASE/'source/future-update.started').exists()
    assert (BASE/'diagnostic-r1/source-stop.rc').read_text().strip()=='0'
common=(R/'lab/common_fields_source.py').read_text();exec(common[common.index('reader='):common.index('libs=N/')])
libs=N/'libs';libs.mkdir();(N/'classes').mkdir();(N/'inputs').mkdir()
shutil.copyfile(R/'target/oracle-redo-kernel-0.1.0-SNAPSHOT.jar',libs/'kernel.jar')
shutil.copyfile(R/'integration/streamhouse-engine-oracle/target/streamhouse-engine-oracle-0.1.0-SNAPSHOT.jar',libs/'adapter.jar')
for p in (R/'lab/out/common-fields-20261010/source/libs').glob('*.jar'):
    if p.name not in ['kernel.jar','adapter.jar']:shutil.copyfile(p,libs/p.name)
shutil.copyfile(R/'lab/out/dictionary-baseline-candidate-20261006/source.secret',N/'source.secret')
run('compile',['D:/work/Java/jdk-17.0.7/bin/javac.exe','-cp',str(libs/'*'),'-d',N/'classes',R/'lab/OracleSnapshotAcquisitionProbe.java',R/'lab/BcTemporalBatchProbe.java'])
table='T_D4_BC_20261011';index='PK_D4_BC_20261011';started=False
try:
    assert run('state',['docker','inspect',S,'--format','{{.Config.Labels.owner}}|{{.State.Status}}']).strip()=='OracleRedoKernel|exited'
    assert H not in run('helpers',['docker','ps','-a','--format','{{.Names}}']).splitlines()
    run('start',['docker','start',S]);started=True
    for attempt in range(30):
        try:
            out=sql('ready-'+str(attempt),"select 'READY|'||open_mode from v$database;")
            if 'READY|READ WRITE' in out:break
        except AssertionError:
            if attempt==29:raise
        time.sleep(3)
    pre=sql('pre',f"select 'CURRENT|'||sequence# from v$log where status='CURRENT';\nselect 'LOGGING|'||supplemental_log_data_min||'|'||supplemental_log_data_all from v$database;\nalter session set container=ORKPDB;\nselect 'FREE|'||count(*) from dba_tables where owner='ORKLAB' and table_name='{table}';")
    assert 'LOGGING|NO|NO' in pre and ('FREE|1' if diagnose or resume else 'FREE|0') in pre
    first=int(next(l.split('|')[1] for l in pre.splitlines() if l.startswith('CURRENT|')))
    if diagnose:
        assert not (BASE/'source/future-update.started').exists()
        sql('columns',f"alter session set container=ORKPDB;\nselect 'COLUMN|'||intcol#||'|'||segcol#||'|'||name||'|'||type#||'|'||null$||'|'||property from sys.col$ where obj#=(select object_id from dba_objects where owner='ORKLAB' and object_name='{table}' and object_type='TABLE') order by segcol#;")
        print('BC_COLUMNS_DIAGNOSED',flush=True);raise SystemExit(0)
    if not resume:sql('fixture',f"alter session set container=ORKPDB;\ncreate table ORKLAB.{table}(ID number primary key,D date,T0 timestamp(0),T3 timestamp(3),T6 timestamp(6),T9 timestamp(9));\ninsert into ORKLAB.{table} values(1,date '2024-03-31',timestamp '2024-03-31 01:30:45',timestamp '2024-03-31 01:30:45.123',timestamp '2024-03-31 01:30:45.123457',timestamp '2024-03-31 01:30:45.123456789');\ncommit;\ngrant select,flashback on ORKLAB.{table} to {reader[0]};")
    else:assert 'SEED|1' in sql('seed-guard',f"alter session set container=ORKPDB;\nselect 'SEED|'||count(*) from ORKLAB.{table} where ID=1 and D=date '2024-03-31' and T9=timestamp '2024-03-31 01:30:45.123456789';")
    # The inline PK name is source-owned; acquisition discovers its real name instead of guessing it.
    names=sql('index',f"alter session set container=ORKPDB;\nselect 'INDEX|'||index_name from dba_constraints where owner='ORKLAB' and table_name='{table}' and constraint_type='P';")
    index=next(l.split('|')[1] for l in names.splitlines() if l.startswith('INDEX|'))
    acquired=run('acquire',['docker','run','--rm','--name',H,'--label','owner=OracleRedoKernel','--network','container:'+S,'--mount','type=bind,source='+str(N)+',target=/evidence','--entrypoint','java','flink:1.18-java17','-cp','/evidence/classes:/evidence/libs/*','OracleSnapshotAcquisitionProbe','/evidence/source.secret','bc-future','ORKLAB',table,index,'history-normal','/evidence/baseline.snapshot'])
    assert acquired.startswith('ACQUIRED|bc-future|');scn=int(acquired.split('|')[2])
    (N/'snapshot.json').write_text(json.dumps(dict(sha256=sha(N/'baseline.snapshot'),scn=scn),indent=2))
    (N/'future-update.started').write_text('One new update after source-owned acquisition; never replay.\n')
    sql('future-update',f"alter session set container=ORKPDB;\nupdate ORKLAB.{table} set D=to_date('0044-03-15 12:34:56 BC','YYYY-MM-DD HH24:MI:SS AD'),T0=to_timestamp('0044-03-15 12:34:56.987654321 BC','YYYY-MM-DD HH24:MI:SS.FF9 AD'),T3=to_timestamp('0044-03-15 12:34:56.987654321 BC','YYYY-MM-DD HH24:MI:SS.FF9 AD'),T6=to_timestamp('0044-03-15 12:34:56.987654321 BC','YYYY-MM-DD HH24:MI:SS.FF9 AD'),T9=to_timestamp('0001-01-01 00:00:00.000000001 BC','YYYY-MM-DD HH24:MI:SS.FF9 AD') where ID=1;\ncommit;")
    sql('truth',f"alter session set container=ORKPDB;\nselect 'ROW|'||ID||'|'||to_char(D,'YYYY-MM-DD HH24:MI:SS AD')||'|'||to_char(T0,'YYYY-MM-DD HH24:MI:SS AD')||'|'||to_char(T3,'YYYY-MM-DD HH24:MI:SS.FF3 AD')||'|'||to_char(T6,'YYYY-MM-DD HH24:MI:SS.FF6 AD')||'|'||to_char(T9,'YYYY-MM-DD HH24:MI:SS.FF9 AD') from ORKLAB.{table};")
    out=sql('close',"select 'CURRENT|'||sequence# from v$log where status='CURRENT';\nalter system archive log current;")
    last=int(next(l.split('|')[1] for l in out.splitlines() if l.startswith('CURRENT|')));archives=[]
    for seq in range(first,last+1):
        out=sql('archive-'+str(seq),f"select 'PATH|'||name from v$archived_log where sequence#={seq} and archived='YES' and deleted='NO' and name is not null order by recid;")
        paths=set(l.split('|')[1] for l in out.splitlines() if l.startswith('PATH|'));assert len(paths)==1
        path=paths.pop();local=N/'inputs'/f'{seq}.raw';run('copy-'+str(seq),['docker','cp',S+':'+path,local])
        assert run('sha-'+str(seq),['docker','exec',S,'sha256sum',path]).split()[0]==sha(local)
        archives.append(dict(sequence=seq,path=str(local),sourcePath=path,sha256=sha(local),bytes=local.stat().st_size))
        trace=sql('native-'+str(seq),f"alter session set tracefile_identifier='ORK_BC_FUTURE';\nalter system dump logfile '{path}' scn min {scn};\nselect 'TRACE|'||value from v$diag_info where name='Default Trace File';")
        tracepath=next(l.split('|',1)[1] for l in trace.splitlines() if l.startswith('TRACE|'));run('native-copy-'+str(seq),['docker','cp',S+':'+tracepath,N/f'{seq}.trc'])
    (N/'archives.json').write_text(json.dumps(archives,indent=2));print('BC_FUTURE_FROZEN',flush=True)
finally:
    if started:run('source-stop',['docker','stop','-t','60',S])
