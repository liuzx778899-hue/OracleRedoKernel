"""One two-session group-commit fixture. Real commit SCNs must be surveyed from frozen redo."""
from pathlib import Path
import hashlib,json,re,shutil,subprocess,sys,time
R=Path(__file__).resolve().parent.parent;N=R/'lab/out/same-scn-20261012/source'
S='oraredo-19c-lab-20261004';H='ork-product-runtime'
assert sys.argv[1:]==['--execute'];N.mkdir(parents=True,exist_ok=False);shutil.copyfile(__file__,N/'executed.py')
common=(R/'lab/common_fields_source.py').read_text();exec(common[common.index('reader='):common.index('libs=N/')])
shutil.copytree(R/'lab/out/array-partial-20261011/source/libs',N/'libs');(N/'classes').mkdir();(N/'inputs').mkdir()
shutil.copyfile(R/'lab/out/dictionary-baseline-candidate-20261006/source.secret',N/'source.secret')
run('kernel',['D:/work/Java/jdk-17.0.7/bin/jar.exe','--create','--file',N/'libs/kernel.jar','-C',R/'target/classes','.'])
run('compile',['D:/work/Java/jdk-17.0.7/bin/javac.exe','-cp',str(N/'libs/*'),'-d',N/'classes',R/'lab/OracleSnapshotAcquisitionProbe.java'])
table='T_D2_SAMESCN_20261012';index='PK_D2_SAMESCN_20261012';gate='T_D2_SAME_GATE_20261012';started=False;workers=[]
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
    pre=sql('pre',f"select 'CURRENT|'||sequence# from v$log where status='CURRENT';\nselect 'LOGGING|'||supplemental_log_data_min||'|'||supplemental_log_data_all from v$database;\nalter session set container=ORKPDB;\nselect 'FREE|'||count(*) from dba_tables where owner='ORKLAB' and table_name in ('{table}','{gate}');")
    assert 'FREE|0' in pre and 'LOGGING|NO|NO' in pre;first=int(next(l.split('|')[1] for l in pre.splitlines() if l.startswith('CURRENT|')))
    sql('fixture',f"alter session set container=ORKPDB;\ncreate table ORKLAB.{table}(ID number not null,TXT varchar2(32),NOTE number,constraint {index} primary key(ID));\ninsert into ORKLAB.{table} values(1,'seed',0);\ncreate table ORKLAB.{gate}(ID number primary key,START_AT timestamp with time zone);\ninsert into ORKLAB.{gate} values(1,null);\ncommit;\ngrant select,flashback on ORKLAB.{table} to {reader[0]};")
    acquired=run('acquire',['docker','run','--rm','--name',H,'--label','owner=OracleRedoKernel','--network','container:'+S,'--mount','type=bind,source='+str(N)+',target=/evidence','--entrypoint','java','flink:1.18-java17','-cp','/evidence/classes:/evidence/libs/*','OracleSnapshotAcquisitionProbe','/evidence/source.secret','same-scn','ORKLAB',table,index,'history-normal','/evidence/baseline.snapshot'])
    assert acquired.startswith('ACQUIRED|same-scn|');minimum=int(acquired.split('|')[2])
    sql('gate',f"alter session set container=ORKPDB;\nupdate ORKLAB.{gate} set START_AT=systimestamp+interval '20' second where ID=1;\ncommit;")
    (N/'actions.started').write_text('one pair of sessions, sixteen scheduled ordinary commits each; never replay\n')
    for actor in (1,2):
        body=f"""whenever sqlerror exit failure rollback
set pages 0 heading off feedback off lines 32767
alter session set container=ORKPDB;
set serveroutput on
declare deadline timestamp with time zone;
begin
 for ordinal in 1..16 loop
  select START_AT+numtodsinterval((ordinal-1)*0.1,'SECOND') into deadline from ORKLAB.{gate} where ID=1;
  insert into ORKLAB.{table} values({actor}*100+ordinal,'actor-{actor}',ordinal);
  while systimestamp<deadline loop dbms_session.sleep(0.001);end loop;
  commit write batch nowait;
 end loop;
 dbms_output.put_line('COMMITTED|{actor}|16');
end;
/
exit success
"""
        (N/f'actor-{actor}.sql').write_text(body)
        out=(N/f'actor-{actor}.stdout').open('wb');err=(N/f'actor-{actor}.stderr').open('wb')
        p=subprocess.Popen(['docker','exec','-i',S,'sqlplus','-s','/','as','sysdba'],stdin=subprocess.PIPE,stdout=out,stderr=err);p.stdin.write(body.encode());p.stdin.close();workers.append((actor,p,out,err))
    for actor,p,out,err in workers:
        rc=p.wait(timeout=90);out.close();err.close();(N/f'actor-{actor}.rc').write_text(str(rc));assert rc==0 and f'COMMITTED|{actor}|16' in (N/f'actor-{actor}.stdout').read_text()
    truth=sql('truth',f"alter session set container=ORKPDB;\nselect 'ROW|'||ID||'|'||TXT||'|'||NOTE from ORKLAB.{table} order by ID;")
    rows=[l.split('|')[1:] for l in truth.splitlines() if l.startswith('ROW|')];assert len(rows)==33
    (N/'truth.json').write_text(json.dumps(rows,indent=2))
    closed=sql('close',"select 'CURRENT|'||sequence# from v$log where status='CURRENT';\nalter system archive log current;")
    last=int(next(l.split('|')[1] for l in closed.splitlines() if l.startswith('CURRENT|')));archives=[]
    for sequence in range(first,last+1):
        out=sql('archive-'+str(sequence),f"select 'PATH|'||name from v$archived_log where sequence#={sequence} and archived='YES' and deleted='NO' and name is not null;")
        paths=set(l.split('|')[1] for l in out.splitlines() if l.startswith('PATH|'));assert len(paths)==1;path=paths.pop();local=N/'inputs'/f'{sequence}.raw'
        run('copy-'+str(sequence),['docker','cp',S+':'+path,local]);assert run('sha-'+str(sequence),['docker','exec',S,'sha256sum',path]).split()[0]==sha(local)
        archives.append(dict(sequence=sequence,sha256=sha(local),bytes=local.stat().st_size))
        out=sql('native-'+str(sequence),f"alter session set tracefile_identifier='ORK_SAME_SCN';\nalter system dump logfile '{path}' scn min {minimum};\nselect 'TRACE|'||value from v$diag_info where name='Default Trace File';")
        trace=next(l.split('|',1)[1] for l in out.splitlines() if l.startswith('TRACE|'));run('native-copy-'+str(sequence),['docker','cp',S+':'+trace,N/f'{sequence}.trc'])
    (N/'archives.json').write_text(json.dumps(archives,indent=2));print('TWO_SESSION_SAMPLE_FROZEN|same_scn_not_yet_verified=true',flush=True)
finally:
    for actor,p,out,err in workers:
        if p.poll() is None:p.wait(timeout=90)
        out.close();err.close()
    if started:run('source-stop',['docker','stop','-t','60',S])
