"""One owned statement-error/savepoint ARRAY fixture spanning two closed redo members."""
from pathlib import Path
import hashlib,json,re,shutil,subprocess,sys,time
R=Path(__file__).resolve().parent.parent;N=R/'lab/out/array-partial-20261011/source'
S='oraredo-19c-lab-20261004';H='ork-product-runtime'
assert sys.argv[1:]==['--execute'];N.mkdir(parents=True,exist_ok=False);shutil.copyfile(__file__,N/'executed.py')
common=(R/'lab/common_fields_source.py').read_text();exec(common[common.index('reader='):common.index('libs=N/')])
libs=N/'libs';shutil.copytree(R/'lab/out/common-fields-20261010/source/libs',libs);(N/'classes').mkdir();(N/'inputs').mkdir()
shutil.copyfile(R/'target/oracle-redo-kernel-0.1.0-SNAPSHOT.jar',libs/'kernel.jar')
shutil.copyfile(R/'integration/streamhouse-engine-oracle/target/streamhouse-engine-oracle-0.1.0-SNAPSHOT.jar',libs/'adapter.jar')
shutil.copyfile(R/'lab/out/dictionary-baseline-candidate-20261006/source.secret',N/'source.secret')
run('compile',['D:/work/Java/jdk-17.0.7/bin/javac.exe','-cp',str(libs/'*'),'-d',N/'classes',R/'lab/OracleSnapshotAcquisitionProbe.java'])
table='T_D2_PARTIAL_20261011';index='PK_D2_PARTIAL_20261011';started=False;p=None
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
    sql('fixture',f"alter session set container=ORKPDB;\ncreate table ORKLAB.{table}(ID number not null,TXT varchar2(128),NOTE varchar2(128),constraint {index} primary key(ID));\ninsert into ORKLAB.{table} values(1,'seed','anchor');\ncommit;\ngrant select,flashback on ORKLAB.{table} to {reader[0]};")
    acquired=run('acquire',['docker','run','--rm','--name',H,'--label','owner=OracleRedoKernel','--network','container:'+S,'--mount','type=bind,source='+str(N)+',target=/evidence','--entrypoint','java','flink:1.18-java17','-cp','/evidence/classes:/evidence/libs/*','OracleSnapshotAcquisitionProbe','/evidence/source.secret','array-partial','ORKLAB',table,index,'history-normal','/evidence/baseline.snapshot'])
    assert acquired.startswith('ACQUIRED|array-partial|');scn=int(acquired.split('|')[2]);(N/'snapshot.json').write_text(json.dumps(dict(scn=scn,sha256=sha(N/'baseline.snapshot')),indent=2))
    body=f"""whenever sqlerror exit failure rollback
set pages 0 heading off feedback off lines 32767
set serveroutput on
alter session set container=ORKPDB;
begin
 begin
  insert into ORKLAB.{table} select case when level=11 then 1 else 99+level end,'error-array','must-rollback' from dual connect by level<=20;
  raise_application_error(-20001,'EXPECTED_DUPLICATE_NOT_RAISED');
 exception when dup_val_on_index then dbms_output.put_line('EXPECTED_ERROR|'||sqlerrm);end;
 insert into ORKLAB.{table} select 199+level,'kept','committed' from dual connect by level<=20;
 if sql%rowcount<>20 then raise_application_error(-20002,'KEPT_COUNT');end if;
 savepoint keep_array;
 update ORKLAB.{table} set TXT='rollback-update',NOTE='must-rollback' where ID>=200;
 if sql%rowcount<>20 then raise_application_error(-20003,'UPDATE_COUNT');end if;
 rollback to keep_array;
end;
/
prompt ARRAY_HELD
begin dbms_session.sleep(90);end;
/
commit;
prompt ARRAY_COMMITTED
exit success
"""
    (N/'actions.started').write_text('single source execution; never replay\n');(N/'actions.sql').write_text(body)
    with (N/'actions.stdout').open('wb') as stdout,(N/'actions.stderr').open('wb') as stderr:
        p=subprocess.Popen(['docker','exec','-i',S,'sqlplus','-s','/','as','sysdba'],stdin=subprocess.PIPE,stdout=stdout,stderr=stderr)
        p.stdin.write(body.encode());p.stdin.close()
        for attempt in range(50):
            text=(N/'actions.stdout').read_text()
            if 'ARRAY_HELD' in text:break
            assert p.poll() is None,'source terminated before pending cut';time.sleep(2)
        assert 'ARRAY_HELD' in text and 'EXPECTED_ERROR|ORA-00001' in text
        sql('prefix-close','alter system archive log current;')
        rc=p.wait(timeout=180);(N/'actions.rc').write_text(str(rc));assert rc==0
    assert 'ARRAY_COMMITTED' in (N/'actions.stdout').read_text()
    truth=sql('truth',f"alter session set container=ORKPDB;\nselect 'ROW|'||ID||'|'||TXT||'|'||NOTE from ORKLAB.{table} order by ID;")
    rows=[l.split('|')[1:] for l in truth.splitlines() if l.startswith('ROW|')];assert len(rows)==21
    assert all(r[1:]==['kept','committed'] for r in rows[1:]);(N/'truth.json').write_text(json.dumps(rows,indent=2))
    closed=sql('close',"select 'CURRENT|'||sequence# from v$log where status='CURRENT';\nalter system archive log current;");last=int(next(l.split('|')[1] for l in closed.splitlines() if l.startswith('CURRENT|')));archives=[]
    for seq in range(first,last+1):
        out=sql('archive-'+str(seq),f"select 'PATH|'||name from v$archived_log where sequence#={seq} and archived='YES' and deleted='NO' and name is not null order by recid;");paths=set(l.split('|')[1] for l in out.splitlines() if l.startswith('PATH|'));assert len(paths)==1
        path=paths.pop();local=N/'inputs'/f'{seq}.raw';run('copy-'+str(seq),['docker','cp',S+':'+path,local]);assert run('sha-'+str(seq),['docker','exec',S,'sha256sum',path]).split()[0]==sha(local)
        archives.append(dict(sequence=seq,path=str(local),sourcePath=path,sha256=sha(local),bytes=local.stat().st_size))
        trace=sql('native-'+str(seq),f"alter session set tracefile_identifier='ORK_ARRAY_PARTIAL';\nalter system dump logfile '{path}' scn min {scn};\nselect 'TRACE|'||value from v$diag_info where name='Default Trace File';");tracepath=next(l.split('|',1)[1] for l in trace.splitlines() if l.startswith('TRACE|'));run('native-copy-'+str(seq),['docker','cp',S+':'+tracepath,N/f'{seq}.trc'])
    (N/'archives.json').write_text(json.dumps(archives,indent=2));print('ARRAY_PARTIAL_SOURCE_FROZEN',flush=True)
finally:
    if p is not None and p.poll() is None:p.wait(timeout=180)
    if started:run('source-stop',['docker','stop','-t','60',S])
