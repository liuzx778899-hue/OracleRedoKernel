"""One FORALL SAVE EXCEPTIONS sample retaining successful rows across a closed pending window."""
from pathlib import Path
import hashlib,json,re,shutil,subprocess,sys,time
R=Path(__file__).resolve().parent.parent;N=R/'lab/out/array-subnode-20261012/source'
S='oraredo-19c-lab-20261004';H='ork-product-runtime'
resume=sys.argv[1:]==['--resume'];assert resume or sys.argv[1:]==['--execute']
if resume:
    assert (N/'fixture.rc').read_text().strip()=='0' and (N/'baseline.snapshot').exists() and not (N/'actions.started').exists()
    shutil.copyfile(__file__,N/'executed-resume.py')
else:N.mkdir(parents=True,exist_ok=False);shutil.copyfile(__file__,N/'executed.py')
common=(R/'lab/common_fields_source.py').read_text();exec(common[common.index('reader='):common.index('libs=N/')])
if resume:
    initial_run=run
    def run(label,args,data=None):return initial_run(label+'-r2',args,data)
else:
    shutil.copytree(R/'lab/out/array-partial-20261011/source/libs',N/'libs');(N/'classes').mkdir();(N/'inputs').mkdir()
    shutil.copyfile(R/'lab/out/dictionary-baseline-candidate-20261006/source.secret',N/'source.secret')
    run('compile',['D:/work/Java/jdk-17.0.7/bin/javac.exe','-cp',str(N/'libs/*'),'-d',N/'classes',R/'lab/OracleSnapshotAcquisitionProbe.java'])
table='T_D2_SUBNODE_20261012';index='PK_D2_SUBNODE_20261012';started=False;p=None
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
    assert ('FREE|1' if resume else 'FREE|0') in pre and 'LOGGING|NO|NO' in pre
    first=int(next(l.split('|')[1] for l in ((N/'pre.stdout').read_text() if resume else pre).splitlines() if l.startswith('CURRENT|')))
    if resume:
        out=sql('seed',f"alter session set container=ORKPDB;\nselect 'ROW|'||ID||'|'||TXT||'|'||NOTE from ORKLAB.{table};")
        assert [l for l in out.splitlines() if l.startswith('ROW|')]==['ROW|1|seed|anchor']
        validated=run('validate-baseline',['D:/work/Java/jdk-17.0.7/bin/java.exe','-cp',str(R/'target/classes')+';'+str(N/'classes'),'SnapshotValidationProbe',N/'baseline.snapshot',table])
        assert validated.startswith('VALIDATED|');scn=int(validated.split('|')[1])
    else:
        sql('fixture',f"alter session set container=ORKPDB;\ncreate table ORKLAB.{table}(ID number not null,TXT varchar2(128),NOTE varchar2(128),constraint {index} primary key(ID));\ninsert into ORKLAB.{table} values(1,'seed','anchor');\ncommit;\ngrant select,flashback on ORKLAB.{table} to {reader[0]};")
        acquired=run('acquire',['docker','run','--rm','--name',H,'--label','owner=OracleRedoKernel','--network','container:'+S,'--mount','type=bind,source='+str(N)+',target=/evidence','--entrypoint','java','flink:1.18-java17','-cp','/evidence/classes:/evidence/libs/*','OracleSnapshotAcquisitionProbe','/evidence/source.secret','array-subnode','ORKLAB',table,index,'history-normal','/evidence/baseline.snapshot'])
        assert acquired.startswith('ACQUIRED|array-subnode|');scn=int(acquired.split('|')[2])
    body=f"""whenever sqlerror exit failure rollback
set pages 0 heading off feedback off lines 32767
set serveroutput on
alter session set container=ORKPDB;
declare
 type ids_t is table of number index by binary_integer;
 ids ids_t;
 bulk_errors exception;
 pragma exception_init(bulk_errors,-24381);
begin
 for i in 1..20 loop ids(i):=case when i in (11,17) then 1 else 199+i end;end loop;
 begin
  forall i in 1..20 save exceptions insert into ORKLAB.{table} values(ids(i),'kept-subnode','partial-array');
  raise_application_error(-20001,'EXPECTED_BULK_ERRORS_MISSING');
 exception when bulk_errors then
  if sql%bulk_exceptions.count<>2 or sql%bulk_exceptions(1).error_index<>11 or sql%bulk_exceptions(2).error_index<>17
   or sql%bulk_exceptions(1).error_code<>1 or sql%bulk_exceptions(2).error_code<>1 then raise_application_error(-20002,'BULK_ERROR_COVERAGE');end if;
  dbms_output.put_line('EXPECTED_ERRORS|11|17');
 end;
end;
/
prompt SUBNODE_HELD
begin dbms_session.sleep(20);end;
/
commit;
prompt SUBNODE_COMMITTED
exit success
"""
    (N/'actions.started').write_text('one FORALL SAVE EXCEPTIONS execution; never replay\n');(N/'actions.sql').write_text(body)
    with (N/'actions.stdout').open('wb') as out,(N/'actions.stderr').open('wb') as err:
        p=subprocess.Popen(['docker','exec','-i',S,'sqlplus','-s','/','as','sysdba'],stdin=subprocess.PIPE,stdout=out,stderr=err)
        p.stdin.write(body.encode());p.stdin.close()
        for attempt in range(20):
            text=(N/'actions.stdout').read_text()
            if 'SUBNODE_HELD' in text:break
            assert p.poll() is None;time.sleep(1)
        assert 'SUBNODE_HELD' in text and 'EXPECTED_ERRORS|11|17' in text
        sql('prefix-close','alter system archive log current;');rc=p.wait(timeout=60);(N/'actions.rc').write_text(str(rc));assert rc==0
    assert 'SUBNODE_COMMITTED' in (N/'actions.stdout').read_text()
    truth=sql('truth',f"alter session set container=ORKPDB;\nselect 'ROW|'||ID||'|'||TXT||'|'||NOTE from ORKLAB.{table} order by ID;")
    rows=[l.split('|')[1:] for l in truth.splitlines() if l.startswith('ROW|')]
    assert len(rows)==19 and [int(r[0]) for r in rows]==[1]+[i for i in range(200,220) if i not in (210,216)]
    (N/'truth.json').write_text(json.dumps(rows,indent=2))
    closed=sql('close',"select 'CURRENT|'||sequence# from v$log where status='CURRENT';\nalter system archive log current;")
    last=int(next(l.split('|')[1] for l in closed.splitlines() if l.startswith('CURRENT|')));archives=[]
    for sequence in range(first,last+1):
        out=sql('archive-'+str(sequence),f"select 'PATH|'||name from v$archived_log where sequence#={sequence} and archived='YES' and deleted='NO' and name is not null;")
        paths=set(l.split('|')[1] for l in out.splitlines() if l.startswith('PATH|'));assert len(paths)==1;path=paths.pop();local=N/'inputs'/f'{sequence}.raw'
        run('copy-'+str(sequence),['docker','cp',S+':'+path,local]);assert run('sha-'+str(sequence),['docker','exec',S,'sha256sum',path]).split()[0]==sha(local)
        archives.append(dict(sequence=sequence,sha256=sha(local),bytes=local.stat().st_size))
        out=sql('native-'+str(sequence),f"alter session set tracefile_identifier='ORK_ARRAY_SUBNODE';\nalter system dump logfile '{path}' scn min {scn};\nselect 'TRACE|'||value from v$diag_info where name='Default Trace File';")
        trace=next(l.split('|',1)[1] for l in out.splitlines() if l.startswith('TRACE|'));run('native-copy-'+str(sequence),['docker','cp',S+':'+trace,N/f'{sequence}.trc'])
    (N/'archives.json').write_text(json.dumps(archives,indent=2));print('ARRAY_SUBNODE_FROZEN',flush=True)
finally:
    if p is not None and p.poll() is None:p.wait(timeout=60)
    if started:run('source-stop',['docker','stop','-t','60',S])
