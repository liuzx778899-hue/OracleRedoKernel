"""Owned Timezone JDBC target and native-byte reacquisition; no source DML."""
from pathlib import Path
import hashlib,json,os,shutil,subprocess,sys,time
assert sys.argv[1:]==['--execute']
R=Path(__file__).resolve().parent.parent;BASE=R/'lab/out/zoned-temporal-20261011'
N=BASE/'target-r1';N.mkdir(exist_ok=False);shutil.copyfile(__file__,N/'executed.py')
SOURCE=BASE/'source-r2';V=BASE/'verify-r2'
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
run('compile',['D:/work/Java/jdk-17.0.7/bin/javac.exe','-cp',str(N/'libs/*'),'-d',N/'classes',R/'lab/ZonedTemporalBatchProbe.java',R/'lab/OracleSnapshotAcquisitionProbe.java'])
snapshot=SOURCE/'baseline.snapshot';archives=json.loads((SOURCE/'archives.json').read_text());assert len(archives)==1;raw=Path(archives[0]['path'])
assert sha(snapshot)==e['baselineSha256'] and sha(raw)==e['captures'][0]['journalSha256']
prefix='ZON20261011';t=owner+'.'+prefix
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
            out=sql('ready-'+str(attempt),"select 'READY|'||count(*) from ORKLAB.T_D4_ZONED_20261011;")
            if 'READY|3' in out:break
        except AssertionError:
            if attempt==29:raise
        time.sleep(3)
    assert 'READY|3' in out
    assert 'FREE|0' in sql('free',f"select 'FREE|'||count(*) from dba_tables where owner='{owner}' and table_name like '{prefix}\\_%' escape '\\';")
    sql('setup',f"""
grant select on sys.v_$timezone_file to {owner};
create table {t}_ROWS(ID number primary key,TZ timestamp(9) with time zone,LTZ timestamp(9) with local time zone);
insert into {t}_ROWS select ID,TZ,LTZ from ORKLAB.T_D4_ZONED_20261011 as of scn {e['baselineScn']};
create table {t}_IDENT(ID number primary key,TARGET_ID varchar2(128),SOURCE_BINDING varchar2(512),SCHEMA_SHA varchar2(64));
insert into {t}_IDENT values(1,'oracle-zoned-{prefix}','{binding}','{e['schemaSha256']}');
create table {t}_APPLIED(EVENT_ID varchar2(80) primary key,PAYLOAD_SHA varchar2(64),BEFORE_IMAGE clob,AFTER_IMAGE clob,OPERATION varchar2(16));
create table {t}_MEMBERS(BATCH_SHA varchar2(64),ORDINAL number,EVENT_ID varchar2(80) references {t}_APPLIED(EVENT_ID),primary key(BATCH_SHA,ORDINAL),unique(BATCH_SHA,EVENT_ID));
create table {t}_COMMITS(BATCH_SHA varchar2(64) primary key,TARGET_ID varchar2(128),SCHEMA_SHA varchar2(64),EVENTS_COUNT number);
commit;
""")
    assert 'ZONED_TARGET_PASS|3|0|' in java('target','ZonedTemporalBatchProbe',['target','/evidence/source-r2/baseline.snapshot',sha(snapshot),'/evidence/source-r2/inputs/'+raw.name,sha(raw),'/evidence/target-r1/target.secret.env',prefix])
    actual=sql('actual',f"""
alter session set time_zone='+00:00';
select 'COUNTS|'||(select count(*) from {t}_APPLIED)||'|'||(select count(*) from {t}_MEMBERS)||'|'||(select count(*) from {t}_COMMITS)||'|'||(select count(*) from {t}_ROWS) from dual;
select 'DIFF|'||count(*) from ((select ID,TZ,LTZ from {t}_ROWS minus select ID,TZ,LTZ from ORKLAB.T_D4_ZONED_20261011) union all (select ID,TZ,LTZ from ORKLAB.T_D4_ZONED_20261011 minus select ID,TZ,LTZ from {t}_ROWS));
select 'ROW|'||ID||'|'||to_char(TZ,'YYYY-MM-DD HH24:MI:SS.FF9 TZH:TZM TZR TZD')||'|'||to_char(LTZ,'YYYY-MM-DD HH24:MI:SS.FF9') from {t}_ROWS order by ID;
""")
    assert 'COUNTS|3|3|1|3' in actual and 'DIFF|0' in actual
    index='PK_D4_ZONED_20261011'
    acquired=java('zoned-acquire','OracleSnapshotAcquisitionProbe',['/evidence/target-r1/source.secret','zoned-after','ORKLAB','T_D4_ZONED_20261011',index,'history-normal','/evidence/target-r1/after.snapshot'])
    assert acquired.startswith('ACQUIRED|zoned-after|')
    (N/'proof.json').write_text(json.dumps(dict(kernelSha256=sha(N/'libs/kernel.jar'),adapterSha256=sha(N/'libs/adapter.jar'),events=3,newApplications=3,retryApplications=0,independentCommitVerified=True,target=[3,3,1,3],completeRowDifferences=0,zonedAcquisitionSha256=sha(N/'after.snapshot'),sourceDml=0,productHTTPIntegrated=False),indent=2))
    print('ZONED_JDBC_TARGET_VERIFIED|3|retry=0',flush=True)
finally:
    if started:run('stop',['docker','stop','-t','60',S])
