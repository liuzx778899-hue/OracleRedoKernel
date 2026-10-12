"""Owned normal product chain using frozen native acquisition; no source DDL/DML."""
from pathlib import Path
import hashlib,json,os,secrets,shutil,subprocess,sys,time,zipfile
R=Path(__file__).resolve().parent.parent;P=Path('D:/work/vibecode/StreamHouse-wt/oracle-main-merge-20261010')
N=R/'lab/out/lob-unified-20261012/product-runtime-r1';S='oraredo-19c-lab-20261004';H='ork-product-runtime'
assert sys.argv[1:]==['--execute'];N.mkdir(exist_ok=False);shutil.copyfile(__file__,N/'executed.py')
props=dict(line.split('=',1) for line in (R/'lab/out/d4-lob-product-20261010/runtime.secret.env').read_text().splitlines())
passwords=[v.encode() for k,v in props.items() if 'PASSWORD' in k]
sha=lambda p:hashlib.sha256(Path(p).read_bytes()).hexdigest()
common=(R/'lab/common_fields_product.py').read_text();exec(common[common.index('def run('):common.index("run('compile'")])
for folder in ['acks','prefix-state','failures','inputs','snapshots','libs','classes','appclasses']:(N/folder).mkdir()
app=R/'lab/out/array-temporal-product-20261011/build-r2/application.jar'
assert sha(app)=='fca2520735887401b543f64acc8e907cb3a1d2dbc062960416db48985dd07f7e'
with zipfile.ZipFile(app) as archive:
    for name in archive.namelist():
        if name.startswith('BOOT-INF/lib/') and name.endswith('.jar') and '/oracle-redo-kernel-' not in name:
            (N/'libs'/Path(name).name).write_bytes(archive.read(name))
        elif name.startswith('BOOT-INF/classes/') and not name.endswith('/'):
            path=N/'appclasses'/name.removeprefix('BOOT-INF/classes/');path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(archive.read(name))
run('development-kernel',['D:/work/Java/jdk-17.0.7/bin/jar.exe','--create','--file',N/'libs/development-kernel.jar','-C',R/'target/classes','.'])
cp=os.pathsep.join(map(str,[N/'classes',N/'appclasses',N/'libs/*']))
sources=[R/'lab/LobRecoveryProductProbe.java',R/'lab/LobRecoveryProductMain.java']
sources += [P/'server/streamhouse-app/src/main/java/com/streamhouse/app/port'/name for name in ['OracleApprovedInput.java','OracleReadCutService.java']]
run('compile',['D:/work/Java/jdk-17.0.7/bin/javac.exe','-J-Duser.language=en','-cp',cp,'-d',N/'classes',*sources])
(N/'development-manifest.json').write_text(json.dumps(dict(formalDelivery=False,baseApplicationSha256=sha(app),developmentKernelSha256=sha(N/'libs/development-kernel.jar'),overlaySources={str(p.relative_to(R) if p.is_relative_to(R) else p.relative_to(P)):sha(p) for p in sources}),indent=2))
baseline=R/'lab/out/lob-migration-20261011/source/baseline.snapshot';shutil.copyfile(baseline,N/'snapshots/29801.snapshot')
original=R/'lab/out/lob-migration-20261011/source/inputs/120.raw';rawName=sha(original)+'.raw';shutil.copyfile(original,N/'inputs'/rawName)
def prepare(label,baseline,leaf,raws):
    return json.loads(run(label,['D:/work/Java/jdk-17.0.7/bin/java.exe','-Xmx1536m','-cp',cp,'LobRecoveryProductProbe',baseline,sha(baseline),leaf,*raws]))
prepared=prepare('initial-preparation',baseline,50380683,[original]);e=prepared['evidence'];source=e['source']
SOURCE='oracle-lob-recovery-20261012';U='ORKLRCTL_20261012';prefix='LR20261012';owner=props['SH_ORACLE_BOUNDED_TARGET_OWNER'];T=owner+'.'+prefix;TARGET=SOURCE+'-target'
password='P'+secrets.token_hex(12)+'a1';passwords.append(password.encode())
props.update(SPRING_DATASOURCE_USERNAME=U,SPRING_DATASOURCE_PASSWORD=password,SERVER_PORT='37301',SH_WORKER_ID='86',
    SH_ORACLE_RAW_DISCOVERY_ENABLED='false',SH_ORACLE_RAW_PLAN_ENABLED='false',SH_PIPELINE_AUTO_CONSUME_ENABLED='true',SH_PIPELINE_AUTO_CONSUME_SOURCE_IDS=SOURCE,
    SH_ORACLE_PREFIX_INPUT_DIRECTORY='/evidence/inputs',SH_ORACLE_PREFIX_CHECKPOINT_DIRECTORY='/evidence/prefix-state',SH_ORACLE_BOUNDED_ACK_DIRECTORY='/evidence/acks',
    SH_ORACLE_BOUNDED_TARGET_ID=TARGET,SH_ORACLE_BOUNDED_TARGET_PREFIX=prefix,SH_PIPELINE_TARGET_APPLY_FAIL_DIR='/evidence/failures',SH_ORACLE_ACQUISITION_ENABLED='true',
    SH_ORACLE_ACQUISITION_SOURCE_ID=SOURCE,SH_ORACLE_ACQUISITION_AUTHORITY='owned-frozen-native-lob-recovery',SH_ORACLE_ACQUISITION_SOURCE_JSON=json.dumps(source,separators=(',',':')),
    SH_ORACLE_ACQUISITION_OWNER='ORKLAB',SH_ORACLE_ACQUISITION_TABLE='T_D4_MIG_20261011',SH_ORACLE_ACQUISITION_INDEX='PK_D4_MIG_20261011',SH_ORACLE_ACQUISITION_DIRECTORY='/evidence/snapshots')
(N/'runtime.secret.env').write_text(''.join(k+'='+v+'\n' for k,v in props.items()))
def product(label,args):
    run(label,['docker','run','--rm','--name',H,'--label','owner=OracleRedoKernel','--label','scope=lob-recovery-normal','--user','0','--network','container:'+S,
        '--workdir','/evidence','--env-file',N/'runtime.secret.env','--mount','type=bind,source='+str(N)+',target=/evidence','--entrypoint','java','flink:1.18-java17',
        '-Xmx1536m','-cp','/evidence/classes:/evidence/appclasses:/evidence/libs/*','LobRecoveryProductMain',*args])
started=False;proofs=[];previous=None;retained=rawName
try:
    assert H not in run('helpers',['docker','ps','-a','--format','{{.Names}}']).splitlines()
    assert run('source-state',['docker','inspect',S,'--format','{{.Config.Labels.owner}}|{{.State.Status}}']).strip()=='OracleRedoKernel|exited'
    run('source-start',['docker','start',S]);started=True
    for attempt in range(30):
        try:
            if 'READY|READ WRITE' in sql('ready-'+str(attempt),"select 'READY|'||open_mode from v$pdbs where name='ORKPDB';"):break
        except AssertionError:
            if attempt==29:raise
        time.sleep(3)
    free=sql('free',f"alter session set container=ORKPDB;\nselect 'FREE_USER|'||count(*) from dba_users where username='{U}';\nselect 'FREE_TARGET|'||count(*) from dba_tables where owner='{owner}' and table_name like '{prefix}\\_%' escape '\\';")
    assert 'FREE_USER|0' in free and 'FREE_TARGET|0' in free
    sql('provision',f'alter session set container=ORKPDB;\ncreate user {U} identified by "{password}" default tablespace USERS quota unlimited on USERS;\ngrant create session,create table,create sequence to {U};',private=True)
    product('initialize',['--initialize'])
    sql('frozen-native-acquisition',f"alter session set container=ORKPDB;\ninsert into {U}.SH_ORACLE_ACQUISITION(ID,SOURCE_ID,AUTHORITY,SNAPSHOT_SHA256,SNAPSHOT_SCN,METADATA_JSON,STATE,REGISTERED_AT,REGISTERED_BY) values(29801,'{SOURCE}','owned-frozen-native-lob-recovery','{sha(baseline)}',{e['baselineScn']},"+clob(prepared['metadata'])+",'ACQUIRED_ONLY',systimestamp,'frozen-native-reuse');\ncommit;")
    identity=':'.join(str(source[k]) for k in ['dbid','activationId','compatibility','thread','resetlogsId','resetlogsScn','containerUid','containerId'])
    sql('target',f"alter session set container=ORKPDB;\ncreate table {T}_ROWS(ID number primary key,TXT varchar2(128),B blob,C clob);\ninsert into {T}_ROWS values(1,'migration-seed',to_blob(hextoraw('0080FF')),'migration-text');\ncreate table {T}_IDENT(ID number primary key,TARGET_ID varchar2(128) not null,SOURCE_BINDING varchar2(512) not null,SCHEMA_SHA varchar2(64) not null);\ninsert into {T}_IDENT values(1,'{TARGET}','{identity}','{e['schemaSha256']}');\ncreate table {T}_APPLIED(EVENT_ID varchar2(80) primary key,PAYLOAD_SHA varchar2(64) not null,BEFORE_IMAGE clob,AFTER_IMAGE clob,OPERATION varchar2(16) not null);\ncreate table {T}_MEMBERS(BATCH_SHA varchar2(64),ORDINAL number,EVENT_ID varchar2(80) references {T}_APPLIED(EVENT_ID),primary key(BATCH_SHA,ORDINAL),unique(BATCH_SHA,EVENT_ID));\ncreate table {T}_COMMITS(BATCH_SHA varchar2(64) primary key,TARGET_ID varchar2(128) not null,SCHEMA_SHA varchar2(64) not null,EVENTS_COUNT number not null);\ncommit;")
    for step in range(3):
        window='lob-recovery-'+str(step);names=[retained]
        if step:
            raw=R/f'lab/out/lob-unified-20261012/source/inputs/{120+step}.raw';name=sha(raw)+'.raw';shutil.copyfile(raw,N/'inputs'/name);names.append(name)
            cut=previous['cut'];local=N/Path(cut['baselinePath']).relative_to('/evidence');prepared=prepare('prepare-'+str(step),local,cut['leaf'],[N/'inputs'/n for n in names]);e=prepared['evidence']
        binding=dict(baselinePath=None,capturePaths=['/evidence/inputs/'+n for n in names],evidence=e,envelopesSha256=prepared['envelopesSha256'],acquisitionId=29801,inputFormat='RAW_MEMBER')
        if step:binding['readCutId']=previous['cut']['id']
        hashes='\n'.join(n+' '+sha(N/'inputs'/n) for n in names);seq=1 if step==0 else step*2
        sql('enroll-'+str(step),f"alter session set container=ORKPDB;\ninsert into {U}.SH_ORACLE_INPUT_BINDING values({29810+step*2},'{SOURCE}','{window}',{seq},{seq+len(names)-1},'v3-history-consumer','{e['schemaSha256']}','APPROVED_BOUNDED',"+clob(binding)+f",systimestamp,'owned-frozen-normal');\ninsert into {U}.SH_CAP_WINDOW(ID,SOURCE_ID,ENGINE_TYPE,WINDOW_KEY,ISSUE_SEQ_START,ISSUE_SEQ_END,HEAD_PATH,FROZEN,FILE_SHA256,TOTAL_SIZE,DROPPED_SEGMENTS,STATUS,REGISTERED_AT) values({29811+step*2},'{SOURCE}','oracle','{window}',{seq},{seq+len(names)-1},'/evidence/inputs/{names[0]}',1,'{hashes}',{sum((N/'inputs'/n).stat().st_size for n in names)},0,'PENDING',systimestamp);\ncommit;")
        product('publish-'+str(step),[SOURCE,'/evidence/inputs',f'/evidence/cut-{step}.json',str(0 if step==0 else 1),window])
        previous=json.loads((N/f'cut-{step}.json').read_text());retained=names[-1]
        assert (N/'inputs'/retained).is_file() and (step==0 or not (N/'inputs'/names[0]).exists())
        actual=sql('actual-'+str(step),f"alter session set container=ORKPDB;\nselect 'COUNTS|'||(select count(*) from {T}_APPLIED)||'|'||(select count(*) from {T}_COMMITS)||'|'||(select count(*) from {U}.SH_EVENT_LEDGER) from dual;\nselect 'VALUES|'||ID||'|'||TXT||'|'||rawtohex(dbms_lob.substr(B,32767,1))||'|'||dbms_lob.substr(C,32767,1) from {T}_ROWS;")
        assert f'COUNTS|{step+1}|{step+1}|{step+1}' in actual
        if step:assert f'VALUES|1|recovery-window-{step}|00AAFF800{step}|recovery-text-{step}' in actual
        proofs.append(dict(step=step,cut=previous,targetEvents=step+1))
    (N/'proof.json').write_text(json.dumps(dict(developmentRuntimeOnly=True,sourceDml=0,frozenAcquisitionReused=True,oldSourceDmlReplayed=0,scenarios=proofs),indent=2))
    print('LOB_NORMAL_PRODUCT_CHAIN_PASS',flush=True)
finally:
    try:
        if H in run('remaining-helper',['docker','ps','-a','--format','{{.Names}}']).splitlines():
            run('failure-logs',['docker','logs',H]);run('failure-stop',['docker','stop','-t','20',H]);run('failure-remove',['docker','rm',H])
    finally:
        if started:run('source-stop',['docker','stop','-t','60',S])
