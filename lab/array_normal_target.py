"""One owned JDBC target application and duplicate retry of the frozen ARRAY batch."""
from pathlib import Path
import hashlib,json,os,shutil,subprocess,sys,time

assert sys.argv[1:]==['--execute']
R=Path(__file__).resolve().parent.parent;BASE=R/'lab/out/array-normal-20261010'
N=BASE/'target-r1';N.mkdir(exist_ok=False);shutil.copyfile(__file__,N/'executed.py')
SOURCE=BASE/'source/resume-r1';V=BASE/'verify-r1'
assert (V/'package-r1.rc').read_text().strip()=='0'
prep=json.loads((V/'verify-r3.json').read_text());assert json.loads((V/'proof.json').read_text())['completeRowDifferences']==0
S='oraredo-19c-lab-20261004';H='ork-product-runtime'
secret=R/'lab/out/d4-lob-product-20261010/runtime.secret.env'
props=dict(line.split('=',1) for line in secret.read_text().splitlines() if '=' in line)
owner=props['SPRING_DATASOURCE_USERNAME'];assert owner=='ORKLOBSCTL_20261010'
shutil.copyfile(secret,N/'target.secret.env')
reader=(R/'lab/out/dictionary-baseline-candidate-20261006/source.secret').read_text().splitlines()
sha=lambda p:hashlib.file_digest(Path(p).open('rb'),'sha256').hexdigest()
common=(R/'lab/common_fields_target.py').read_text();exec(common[common.index('def run('):common.index('classes=N/')])
shutil.copyfile(R/'target/oracle-redo-kernel-0.1.0-SNAPSHOT.jar',N/'kernel.jar')
adapter=R/'lab/out/common-fields-20261010/candidate-r15/adapter.jar';shutil.copyfile(adapter,N/'adapter.jar')
assert sha(N/'adapter.jar')=='87eb1e9064dee46e580853ac889453b458a9fb59a1d79cb40742c64e554938fb'
shutil.copytree(R/'lab/out/common-fields-20261010/source/libs',N/'libs')
(N/'classes').mkdir();cp=os.pathsep.join(map(str,[N/'kernel.jar',N/'adapter.jar',N/'libs/*',N/'classes']))
run('compile',['D:/work/Java/jdk-17.0.7/bin/javac.exe','-cp',cp,'-d',N/'classes',R/'lab/ArrayTargetProbe.java'])
snapshot=SOURCE/'snapshots/102392741874659328.snapshot';raw=SOURCE/'inputs/107.raw';e=prep['evidence']
assert sha(snapshot)==e['baselineSha256'] and sha(raw)==e['captures'][0]['journalSha256']
prefix='ARR20261010';t=owner+'.'+prefix;binding=':'.join(str(e['source'][k]) for k in ['dbid','activationId','compatibility','thread','resetlogsId','resetlogsScn','containerUid','containerId'])
started=False
try:
    assert run('state',['docker','inspect',S,'--format','{{.Config.Labels.owner}}|{{.State.Status}}']).strip()=='OracleRedoKernel|exited'
    assert H not in run('helpers',['docker','ps','-a','--format','{{.Names}}']).splitlines()
    run('start',['docker','start',S]);started=True
    for attempt in range(30):
        try:
            out=sql('ready-'+str(attempt),'select \'READY|\'||count(*) from ORKLAB.T_D2_ARRAY_20261010;')
            if 'READY|21' in out:break
        except AssertionError:
            if attempt==29:raise
        time.sleep(3)
    assert 'READY|21' in out
    assert 'FREE|0' in sql('free',f"select 'FREE|'||count(*) from dba_tables where owner='{owner}' and table_name like '{prefix}\\_%' escape '\\';")
    sql('setup',f"""
create table {t}_ROWS(ID number primary key,TXT varchar2(128),NOTE varchar2(128));
insert into {t}_ROWS select ID,TXT,NOTE from ORKLAB.T_D2_ARRAY_20261010 as of scn {e['baselineScn']};
create table {t}_IDENT(ID number primary key,TARGET_ID varchar2(128),SOURCE_BINDING varchar2(512),SCHEMA_SHA varchar2(64));
insert into {t}_IDENT values(1,'oracle-array-{prefix}','{binding}','{e['schemaSha256']}');
create table {t}_APPLIED(EVENT_ID varchar2(80) primary key,PAYLOAD_SHA varchar2(64),BEFORE_IMAGE clob,AFTER_IMAGE clob,OPERATION varchar2(16));
create table {t}_MEMBERS(BATCH_SHA varchar2(64),ORDINAL number,EVENT_ID varchar2(80) references {t}_APPLIED(EVENT_ID),primary key(BATCH_SHA,ORDINAL),unique(BATCH_SHA,EVENT_ID));
create table {t}_COMMITS(BATCH_SHA varchar2(64) primary key,TARGET_ID varchar2(128),SCHEMA_SHA varchar2(64),EVENTS_COUNT number);
commit;
""")
    command=['docker','run','--rm','--name',H,'--label','owner=OracleRedoKernel','--network','container:'+S,
        '--mount','type=bind,source='+str(BASE)+',target=/evidence','--entrypoint','java','flink:1.18-java17',
        '-cp','/evidence/target-r1/kernel.jar:/evidence/target-r1/adapter.jar:/evidence/target-r1/libs/*:/evidence/target-r1/classes','ArrayTargetProbe',
        '/evidence/source/resume-r1/snapshots/'+snapshot.name,sha(snapshot),'/evidence/source/resume-r1/inputs/107.raw',sha(raw),'/evidence/target-r1/target.secret.env',owner,prefix]
    assert 'ARRAY_TARGET_PASS|20|0|' in run('target',command)
    actual=sql('actual',f"""
select 'COUNTS|'||(select count(*) from {t}_APPLIED)||'|'||(select count(*) from {t}_MEMBERS)||'|'||(select count(*) from {t}_COMMITS)||'|'||(select count(*) from {t}_ROWS) from dual;
select 'DIFF|'||count(*) from ((select ID,TXT,NOTE from {t}_ROWS minus select ID,TXT,NOTE from ORKLAB.T_D2_ARRAY_20261010) union all (select ID,TXT,NOTE from ORKLAB.T_D2_ARRAY_20261010 minus select ID,TXT,NOTE from {t}_ROWS));
""")
    assert 'COUNTS|20|20|1|21' in actual and 'DIFF|0' in actual
    (N/'proof.json').write_text(json.dumps(dict(kernelSha256=sha(N/'kernel.jar'),adapterSha256=sha(N/'adapter.jar'),events=20,newApplications=20,retryApplications=0,independentCommitVerified=True,target=[20,20,1,21],completeRowDifferences=0,sourceDml=0,productHTTPIntegrated=False),indent=2))
    print('ARRAY_JDBC_TARGET_VERIFIED|20|retry=0',flush=True)
finally:
    if started:run('stop',['docker','stop','-t','60',S])
