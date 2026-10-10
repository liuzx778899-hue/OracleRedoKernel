"""Owned BC/precision JDBC target and native-byte reacquisition; no source DML."""
from pathlib import Path
import hashlib,json,os,shutil,subprocess,sys,time
assert sys.argv[1:]==['--execute']
R=Path(__file__).resolve().parent.parent;BASE=R/'lab/out/bc-temporal-20261011'
N=BASE/'target-r1';N.mkdir(exist_ok=False);shutil.copyfile(__file__,N/'executed.py')
SOURCE=BASE/'source/resume-r1';V=BASE/'verify-r2'
assert (SOURCE/'source-stop.rc').read_text().strip()=='0' and (V/'project.rc').read_text().strip()=='0'
e=json.loads((V/'project.stdout').read_text())['evidence']
S='oraredo-19c-lab-20261004';H='ork-product-runtime'
secret=R/'lab/out/d4-lob-product-20261010/runtime.secret.env'
props=dict(line.split('=',1) for line in secret.read_text().splitlines() if '=' in line)
owner=props['SPRING_DATASOURCE_USERNAME'];assert owner=='ORKLOBSCTL_20261010'
shutil.copyfile(secret,N/'target.secret.env')
reader=(R/'lab/out/dictionary-baseline-candidate-20261006/source.secret').read_text().splitlines()
sha=lambda p:hashlib.file_digest(Path(p).open('rb'),'sha256').hexdigest()
common=(R/'lab/common_fields_target.py').read_text();exec(common[common.index('def run('):common.index('classes=N/')])
shutil.copytree(SOURCE/'libs',N/'libs');shutil.copyfile(R/'target/oracle-redo-kernel-0.1.0-SNAPSHOT.jar',N/'libs/kernel.jar')
shutil.copyfile(R/'integration/streamhouse-engine-oracle/target/streamhouse-engine-oracle-0.1.0-SNAPSHOT.jar',N/'libs/adapter.jar')
shutil.copyfile(SOURCE/'source.secret',N/'source.secret');(N/'classes').mkdir()
run('compile',['D:/work/Java/jdk-17.0.7/bin/javac.exe','-cp',str(N/'libs/*'),'-d',N/'classes',R/'lab/BcTemporalBatchProbe.java',R/'lab/OracleSnapshotAcquisitionProbe.java'])
snapshot=SOURCE/'baseline.snapshot';raw=SOURCE/'inputs/110.raw'
assert sha(snapshot)==e['baselineSha256'] and sha(raw)==e['captures'][0]['journalSha256']
prefix='BC20261011';t=owner+'.'+prefix
binding=':'.join(str(e['source'][k]) for k in ['dbid','activationId','compatibility','thread','resetlogsId','resetlogsScn','containerUid','containerId'])
def java(label,main,args):
    return run(label,['docker','run','--rm','--name',H,'--label','owner=OracleRedoKernel','--network','container:'+S,'--mount','type=bind,source='+str(BASE)+',target=/evidence','--entrypoint','java','flink:1.18-java17','-cp','/evidence/target-r1/classes:/evidence/target-r1/libs/*',main]+args)
started=False
try:
    assert run('state',['docker','inspect',S,'--format','{{.Config.Labels.owner}}|{{.State.Status}}']).strip()=='OracleRedoKernel|exited'
    assert H not in run('helpers',['docker','ps','-a','--format','{{.Names}}']).splitlines()
    run('start',['docker','start',S]);started=True
    for attempt in range(30):
        try:
            out=sql('ready-'+str(attempt),"select 'READY|'||count(*) from ORKLAB.T_D4_BC_20261011;")
            if 'READY|1' in out:break
        except AssertionError:
            if attempt==29:raise
        time.sleep(3)
    assert 'READY|1' in out
    assert 'FREE|0' in sql('free',f"select 'FREE|'||count(*) from dba_tables where owner='{owner}' and table_name like '{prefix}\\_%' escape '\\';")
    sql('setup',f"""
create table {t}_ROWS(ID number primary key,D date,T0 timestamp(0),T3 timestamp(3),T6 timestamp(6),T9 timestamp(9));
insert into {t}_ROWS select ID,D,T0,T3,T6,T9 from ORKLAB.T_D4_BC_20261011 as of scn {e['baselineScn']};
create table {t}_IDENT(ID number primary key,TARGET_ID varchar2(128),SOURCE_BINDING varchar2(512),SCHEMA_SHA varchar2(64));
insert into {t}_IDENT values(1,'oracle-bc-{prefix}','{binding}','{e['schemaSha256']}');
create table {t}_APPLIED(EVENT_ID varchar2(80) primary key,PAYLOAD_SHA varchar2(64),BEFORE_IMAGE clob,AFTER_IMAGE clob,OPERATION varchar2(16));
create table {t}_MEMBERS(BATCH_SHA varchar2(64),ORDINAL number,EVENT_ID varchar2(80) references {t}_APPLIED(EVENT_ID),primary key(BATCH_SHA,ORDINAL),unique(BATCH_SHA,EVENT_ID));
create table {t}_COMMITS(BATCH_SHA varchar2(64) primary key,TARGET_ID varchar2(128),SCHEMA_SHA varchar2(64),EVENTS_COUNT number);
commit;
""")
    assert 'BC_TARGET_PASS|1|0|' in java('target','BcTemporalBatchProbe',['target','/evidence/source/resume-r1/baseline.snapshot',sha(snapshot),'/evidence/source/resume-r1/inputs/110.raw',sha(raw),'/evidence/target-r1/target.secret.env',prefix])
    actual=sql('actual',f"""
select 'COUNTS|'||(select count(*) from {t}_APPLIED)||'|'||(select count(*) from {t}_MEMBERS)||'|'||(select count(*) from {t}_COMMITS)||'|'||(select count(*) from {t}_ROWS) from dual;
select 'DIFF|'||count(*) from ((select ID,D,T0,T3,T6,T9 from {t}_ROWS minus select ID,D,T0,T3,T6,T9 from ORKLAB.T_D4_BC_20261011) union all (select ID,D,T0,T3,T6,T9 from ORKLAB.T_D4_BC_20261011 minus select ID,D,T0,T3,T6,T9 from {t}_ROWS));
select 'ROW|'||ID||'|'||to_char(D,'YYYY-MM-DD HH24:MI:SS AD')||'|'||to_char(T0,'YYYY-MM-DD HH24:MI:SS AD')||'|'||to_char(T3,'YYYY-MM-DD HH24:MI:SS.FF3 AD')||'|'||to_char(T6,'YYYY-MM-DD HH24:MI:SS.FF6 AD')||'|'||to_char(T9,'YYYY-MM-DD HH24:MI:SS.FF9 AD') from {t}_ROWS;
""")
    assert 'COUNTS|1|1|1|1' in actual and 'DIFF|0' in actual
    index=next(l.split('|')[1] for l in (SOURCE/'index.stdout').read_text().splitlines() if l.startswith('INDEX|'))
    acquired=java('bc-acquire','OracleSnapshotAcquisitionProbe',['/evidence/target-r1/source.secret','bc-after','ORKLAB','T_D4_BC_20261011',index,'history-normal','/evidence/target-r1/after.snapshot'])
    assert acquired.startswith('ACQUIRED|bc-after|')
    (N/'proof.json').write_text(json.dumps(dict(kernelSha256=sha(N/'libs/kernel.jar'),adapterSha256=sha(N/'libs/adapter.jar'),events=1,newApplications=1,retryApplications=0,independentCommitVerified=True,target=[1,1,1,1],completeRowDifferences=0,bcAcquisitionSha256=sha(N/'after.snapshot'),sourceDml=0,productHTTPIntegrated=False),indent=2))
    print('BC_JDBC_TARGET_VERIFIED|1|retry=0',flush=True)
finally:
    if started:run('stop',['docker','stop','-t','60',S])
