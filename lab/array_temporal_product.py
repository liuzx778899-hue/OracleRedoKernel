"""Two fresh HTTP-acquired future windows on the committed candidate; each source action once."""
from pathlib import Path
import hashlib,json,os,secrets,shutil,subprocess,sys,time,zipfile
R=Path(__file__).resolve().parent.parent;BASE=R/'lab/out/array-temporal-product-20261011';BUILD=BASE/'build-r1';N=BASE/'runtime-r1'
S='oraredo-19c-lab-20261004';H='ork-product-runtime';PORT=37301
assert sys.argv[1:]==['--execute'];N.mkdir(exist_ok=False);shutil.copyfile(__file__,N/'executed.py')
props=dict(line.split('=',1) for line in (R/'lab/out/d4-lob-product-20261010/runtime.secret.env').read_text().splitlines())
reader=(R/'lab/out/dictionary-baseline-candidate-20261006/source.secret').read_text().splitlines()
passwords=[v.encode() for k,v in props.items() if 'PASSWORD' in k]+[reader[1].encode()]
sha=lambda p:hashlib.sha256(Path(p).read_bytes()).hexdigest()
candidate=json.loads((BUILD/'candidate.json').read_text());assert candidate['productCommit']=='19071ed14e2dc6b3c634799f72977b9b8d9c138d'
assert sha(BUILD/'application.jar')==candidate['applicationSha256'];shutil.copyfile(BUILD/'application.jar',N/'application.jar')
for folder in ['acks','prefix-state','failures','inputs','snapshots','libs','classes']:(N/folder).mkdir()
with zipfile.ZipFile(N/'application.jar') as archive:
    for name in archive.namelist():
        if name.startswith('BOOT-INF/lib/') and name.endswith('.jar'):(N/'libs'/Path(name).name).write_bytes(archive.read(name))
assert sha(N/'libs'/('oracle-redo-kernel-'+candidate['kernelVersion']+'.jar'))==candidate['kernelSha256']
assert sha(N/'libs'/('streamhouse-engine-oracle-'+candidate['adapterVersion']+'.jar'))==candidate['adapterSha256']
cp=os.pathsep.join([str(N/'classes'),str(N/'libs/*')])
common=(R/'lab/common_fields_product.py').read_text();exec(common[common.index('def run('):common.index("run('compile'")])
run('compile',['D:/work/Java/jdk-17.0.7/bin/javac.exe','-cp',cp,'-d',N/'classes',R/'lab/ArrayTemporalProductProbe.java'])
source=dict(dbid=1276726821,activationId=1276717861,compatibility=318767104,thread=1,resetlogsId=1245693159,resetlogsScn=1920977,containerUid=677737149,containerId=3)
started=False;helper=False;proofs=[]
try:
    assert H not in run('helpers',['docker','ps','-a','--format','{{.Names}}']).splitlines()
    assert run('state',['docker','inspect',S,'--format','{{.Config.Labels.owner}}|{{.State.Status}}']).strip()=='OracleRedoKernel|exited'
    run('start',['docker','start',S]);started=True
    for attempt in range(30):
        try:
            out=sql('ready-'+str(attempt),"select 'READY|'||open_mode from v$pdbs where name='ORKPDB';")
            if 'READY|READ WRITE' in out:break
        except AssertionError:
            if attempt==29:raise
        time.sleep(3)
    assert 'LOGGING|NO|NO' in sql('logging',"select 'LOGGING|'||supplemental_log_data_min||'|'||supplemental_log_data_all from v$database;")
    for ordinal,kind in enumerate(['A','T']):
        U='ORKAT'+kind+'CTL_20261011';SOURCE='oracle-array-time-'+kind.lower();prefix='ATH'+kind+'20261011';owner=props['SH_ORACLE_BOUNDED_TARGET_OWNER'];T=owner+'.'+prefix;TARGET=SOURCE+'-target'
        table='T_D2_APROD_20261011' if kind=='A' else 'T_D4_TPROD_20261011';index='PK_'+table;window='array-time-'+kind.lower();count=20 if kind=='A' else 3
        pre=sql(kind+'-free',f"alter session set container=ORKPDB;\nselect 'FREE_USER|'||count(*) from dba_users where username='{U}';\nselect 'FREE_SOURCE|'||count(*) from dba_tables where owner='ORKLAB' and table_name='{table}';\nselect 'FREE_TARGET|'||count(*) from dba_tables where owner='{owner}' and table_name like '{prefix}\\_%' escape '\\';\nselect 'CURRENT|'||sequence# from v$log where status='CURRENT';")
        assert all(v in pre for v in ['FREE_USER|0','FREE_SOURCE|0','FREE_TARGET|0']);first=int(next(l.split('|')[1] for l in pre.splitlines() if l.startswith('CURRENT|')))
        columns='ID number not null,TXT varchar2(128),NOTE varchar2(128)' if kind=='A' else 'ID number not null,D date,T0 timestamp(0),T3 timestamp(3),T6 timestamp(6),T9 timestamp(9),TZ timestamp(9) with time zone,REG timestamp(9) with time zone,LTZ timestamp(9) with local time zone'
        seed="values(1,'seed','anchor')" if kind=='A' else "select level,to_date('2024-03-30 03:30:45','YYYY-MM-DD HH24:MI:SS'),timestamp '2024-03-30 03:30:45',timestamp '2024-03-30 03:30:45.123',timestamp '2024-03-30 03:30:45.123456',timestamp '2024-03-30 03:30:45.123456789',to_timestamp_tz('2024-03-30 03:30:45.123456789 +05:30','YYYY-MM-DD HH24:MI:SS.FF9 TZH:TZM'),to_timestamp_tz('2024-03-30 03:30:45.123456789 Europe/Paris','YYYY-MM-DD HH24:MI:SS.FF9 TZR'),to_timestamp_tz('2024-03-30 03:30:45.123456789 +05:30','YYYY-MM-DD HH24:MI:SS.FF9 TZH:TZM') from dual connect by level<=3"
        sql(kind+'-fixture',f"alter session set container=ORKPDB;\ncreate table ORKLAB.{table}({columns},constraint {index} primary key(ID));\ninsert into ORKLAB.{table} {seed};\ncommit;\ngrant select,flashback on ORKLAB.{table} to {reader[0]};")
        password='P'+secrets.token_hex(12)+'a1';passwords.append(password.encode())
        sql(kind+'-provision',f'alter session set container=ORKPDB;\ncreate user {U} identified by "{password}" default tablespace USERS quota unlimited on USERS;\ngrant create session,create table,create sequence to {U};',private=True)
        props.update(SPRING_DATASOURCE_USERNAME=U,SPRING_DATASOURCE_PASSWORD=password,SERVER_PORT=str(PORT),SH_WORKER_ID=str(81+ordinal),SH_ORACLE_RAW_DISCOVERY_ENABLED='false',SH_ORACLE_RAW_PLAN_ENABLED='false',SH_PIPELINE_AUTO_CONSUME_ENABLED='true',SH_PIPELINE_AUTO_CONSUME_SOURCE_IDS=SOURCE,SH_ORACLE_PREFIX_INPUT_DIRECTORY='/evidence/inputs',SH_ORACLE_PREFIX_CHECKPOINT_DIRECTORY='/evidence/prefix-state',SH_ORACLE_BOUNDED_ACK_DIRECTORY='/evidence/acks',SH_ORACLE_BOUNDED_TARGET_ID=TARGET,SH_ORACLE_BOUNDED_TARGET_PREFIX=prefix,SH_PIPELINE_TARGET_APPLY_FAIL_DIR='/evidence/failures',SH_ORACLE_ACQUISITION_ENABLED='true',SH_ORACLE_ACQUISITION_SOURCE_ID=SOURCE,SH_ORACLE_ACQUISITION_AUTHORITY='owned-array-time-normal',SH_ORACLE_ACQUISITION_SOURCE_JSON=json.dumps(source,separators=(',',':')),SH_ORACLE_ACQUISITION_OWNER='ORKLAB',SH_ORACLE_ACQUISITION_TABLE=table,SH_ORACLE_ACQUISITION_INDEX=index,SH_ORACLE_ACQUISITION_DIRECTORY='/evidence/snapshots',SH_ORACLE_ACQUISITION_USER=reader[0],SH_ORACLE_ACQUISITION_PASSWORD=reader[1],SH_ORACLE_ACQUISITION_URL=reader[2])
        (N/(kind+'-runtime.secret.env')).write_text(''.join(k+'='+v+'\n' for k,v in props.items()))
        run(kind+'-helper-start',['docker','run','-d','--name',H,'--label','owner=OracleRedoKernel','--label','scope=oracle-array-time-product','--user','0','--network','container:'+S,'--workdir','/evidence','--env-file',N/(kind+'-runtime.secret.env'),'--mount','type=bind,source='+str(N)+',target=/evidence','--entrypoint','java','flink:1.18-java17','-Xmx1536m','-jar','/evidence/application.jar']);helper=True
        for attempt in range(40):
            try:http(kind+'-health-'+str(attempt),'/system/info');break
            except (AssertionError,ValueError):
                assert run(kind+'-health-state-'+str(attempt),['docker','inspect',H,'--format','{{.State.Status}}']).strip()!='exited'
                if attempt==39:raise
                time.sleep(3)
        sql(kind+'-target',f"alter session set container=ORKPDB;\ncreate table {T}_ROWS({columns},primary key(ID));\ninsert into {T}_ROWS select * from ORKLAB.{table};\nalter table {U}.SH_ORACLE_ACQUISITION allocate extent;\ncommit;")
        acquired=http(kind+'-acquire','/oracle/acquisitions/historical',dict(sourceId=SOURCE));ACQ=int(acquired['id']);verified=http(kind+'-verified',f'/oracle/acquisitions/{ACQ}')
        assert {k:v for k,v in verified.items() if k!='registeredAt'}=={k:v for k,v in acquired.items() if k!='registeredAt'}
        baseline=N/'snapshots'/f'{ACQ}.snapshot';assert sha(baseline)==acquired['snapshotSha256']
        action=f"insert into ORKLAB.{table} select 399+level,'product-array','committed' from dual connect by level<=20;" if kind=='A' else f"update ORKLAB.{table} set D=to_date('0044-03-15 12:34:56 BC','YYYY-MM-DD HH24:MI:SS BC'),T0=to_timestamp('0044-03-15 12:34:56 BC','YYYY-MM-DD HH24:MI:SS BC'),T3=to_timestamp('0044-03-15 12:34:56.123 BC','YYYY-MM-DD HH24:MI:SS.FF3 BC'),T6=to_timestamp('0044-03-15 12:34:56.123456 BC','YYYY-MM-DD HH24:MI:SS.FF6 BC'),T9=to_timestamp('0001-01-01 00:00:00.000000001 BC','YYYY-MM-DD HH24:MI:SS.FF9 BC'),TZ=to_timestamp_tz('2024-03-31 03:30:45.000000001 -03:30','YYYY-MM-DD HH24:MI:SS.FF9 TZH:TZM'),REG=to_timestamp_tz('2024-03-31 03:30:45.987654321 Europe/Paris','YYYY-MM-DD HH24:MI:SS.FF9 TZR'),LTZ=to_timestamp_tz('2024-03-31 03:30:45.123456789 Europe/Paris','YYYY-MM-DD HH24:MI:SS.FF9 TZR');"
        (N/(kind+'-source-action.started')).write_text('one new future action after HTTP acquisition; never replay\n');sql(kind+'-source-action','alter session set container=ORKPDB;\n'+action+'\ncommit;')
        closed=sql(kind+'-close',"select 'CURRENT|'||sequence# from v$log where status='CURRENT';\nalter system archive log current;");last=int(next(l.split('|')[1] for l in closed.splitlines() if l.startswith('CURRENT|')));captures=[]
        for seq in range(first,last+1):
            out=sql(kind+'-archive-'+str(seq),f"select 'PATH|'||name from v$archived_log where sequence#={seq} and archived='YES' and deleted='NO' and name is not null order by recid;");paths=set(l.split('|')[1] for l in out.splitlines() if l.startswith('PATH|'));assert len(paths)==1
            path=paths.pop();local=N/'inputs'/f'{seq}.raw';run(kind+'-copy-'+str(seq),['docker','cp',S+':'+path,local]);assert run(kind+'-sha-'+str(seq),['docker','exec',S,'sha256sum',path]).split()[0]==sha(local);captures.append(local)
        prepared=json.loads(run(kind+'-preparation',['D:/work/Java/jdk-17.0.7/bin/java.exe','-Xmx1536m','-cp',cp,'ArrayTemporalProductProbe',baseline,acquired['snapshotSha256'],count,*captures]));e=prepared['evidence'];assert len(prepared['events'])==count
        assert all(x['operation']==('INSERT' if kind=='A' else 'UPDATE') for x in prepared['events'])
        identity=':'.join(str(e['source'][k]) for k in ['dbid','activationId','compatibility','thread','resetlogsId','resetlogsScn','containerUid','containerId'])
        sql(kind+'-ledger',f"alter session set container=ORKPDB;\ncreate table {T}_IDENT(ID number primary key,TARGET_ID varchar2(128) not null,SOURCE_BINDING varchar2(512) not null,SCHEMA_SHA varchar2(64) not null);\ninsert into {T}_IDENT values(1,'{TARGET}','{identity}','{e['schemaSha256']}');\ncreate table {T}_APPLIED(EVENT_ID varchar2(80) primary key,PAYLOAD_SHA varchar2(64) not null,BEFORE_IMAGE clob,AFTER_IMAGE clob,OPERATION varchar2(16) not null);\ncreate table {T}_MEMBERS(BATCH_SHA varchar2(64),ORDINAL number,EVENT_ID varchar2(80) references {T}_APPLIED(EVENT_ID),primary key(BATCH_SHA,ORDINAL),unique(BATCH_SHA,EVENT_ID));\ncreate table {T}_COMMITS(BATCH_SHA varchar2(64) primary key,TARGET_ID varchar2(128) not null,SCHEMA_SHA varchar2(64) not null,EVENTS_COUNT number not null);\ncommit;")
        binding=dict(baselinePath=None,capturePaths=['/evidence/inputs/'+p.name for p in captures],evidence=e,envelopesSha256=prepared['envelopesSha256'],acquisitionId=ACQ,inputFormat='RAW_MEMBER');hashes='\n'.join(p.name+' '+sha(p) for p in captures)
        sql(kind+'-enroll',f"alter session set container=ORKPDB;\ninsert into {U}.SH_ORACLE_INPUT_BINDING values(29701,'{SOURCE}','{window}',1,{len(captures)},'v3-history-consumer','{e['schemaSha256']}','APPROVED_BOUNDED',"+clob(binding)+f",systimestamp,'owned-array-time');\ninsert into {U}.SH_CAP_WINDOW(ID,SOURCE_ID,ENGINE_TYPE,WINDOW_KEY,ISSUE_SEQ_START,ISSUE_SEQ_END,HEAD_PATH,FROZEN,FILE_SHA256,TOTAL_SIZE,DROPPED_SEGMENTS,STATUS,REGISTERED_AT) values(29702,'{SOURCE}','oracle','{window}',1,{len(captures)},'/evidence/inputs/{captures[0].name}',1,'{hashes}',{sum(p.stat().st_size for p in captures)},0,'PENDING',systimestamp);\ncommit;")
        for attempt in range(60):
            out=sql(kind+'-wait-'+str(attempt),f"alter session set container=ORKPDB;\nselect 'BATCH|'||STATE||'|'||RC||'|'||EVENT_COUNT from {U}.SH_PIPELINE_BATCH where WINDOW_KEY='{window}';")
            if f'BATCH|PUBLISHED|0|{count}' in out:break
            assert 'FAILED' not in out,out;time.sleep(2)
        else:raise AssertionError('Product publication timeout')
        match="t.TXT=s.TXT and t.NOTE=s.NOTE" if kind=='A' else "t.D=s.D and t.T0=s.T0 and t.T3=s.T3 and t.T6=s.T6 and t.T9=s.T9 and t.TZ=s.TZ and t.REG=s.REG and t.LTZ=s.LTZ and to_char(t.TZ,'TZR')=to_char(s.TZ,'TZR') and to_char(t.REG,'TZR')=to_char(s.REG,'TZR')"
        actual=sql(kind+'-actual',f"alter session set container=ORKPDB;\nselect 'COUNTS|'||(select count(*) from {T}_APPLIED)||'|'||(select count(*) from {T}_MEMBERS)||'|'||(select count(*) from {T}_COMMITS)||'|'||(select count(*) from {U}.SH_EVENT_LEDGER) from dual;\nselect 'MATCH|'||count(*) from {T}_ROWS t join ORKLAB.{table} s on s.ID=t.ID where {match};\nselect 'EXTRA|'||count(*) from {T}_ROWS t where not exists(select 1 from ORKLAB.{table} s where s.ID=t.ID);\nselect 'MISSING|'||count(*) from ORKLAB.{table} s where not exists(select 1 from {T}_ROWS t where t.ID=s.ID);\nselect 'WINDOW|'||STATUS from {U}.SH_CAP_WINDOW;\nset serveroutput on size unlimited\nbegin for r in (select ID,STATE,CHECKPOINT_JSON from {U}.SH_TASK where CHECKPOINT_JSON is not null) loop dbms_output.put_line('TASK|'||r.ID||'|'||r.STATE);for i in 0..trunc((dbms_lob.getlength(r.CHECKPOINT_JSON)-1)/1000) loop dbms_output.put_line('CPHEX|'||i||'|'||rawtohex(utl_raw.cast_to_raw(dbms_lob.substr(r.CHECKPOINT_JSON,1000,1+i*1000))));end loop;end loop;end;\n/\n")
        assert f'COUNTS|{count}|{count}|1|{count}' in actual and f'MATCH|{21 if kind=="A" else 3}' in actual and 'EXTRA|0' in actual and 'MISSING|0' in actual and 'WINDOW|CONSUMED' in actual
        tasks=[l.split('|')[1:] for l in actual.splitlines() if l.startswith('TASK|')];assert len(tasks)==1 and tasks[0][1]=='DONE'
        parts=sorted((int(l.split('|')[1]),l.split('|')[2]) for l in actual.splitlines() if l.startswith('CPHEX|'));assert [i for i,_ in parts]==list(range(len(parts)))
        checkpointBytes=bytes.fromhex(''.join(p for _,p in parts));checkpoint=json.loads(checkpointBytes);ack=N/'acks'/(hashlib.sha256((SOURCE+'\0'+window).encode()).hexdigest()+'.ack.json')
        for attempt in range(30):
            if ack.exists():break
            time.sleep(1)
        assert ack.read_bytes()==checkpointBytes and checkpoint['oracleEvidence']==e
        proof=dict(kind=kind,acquisition=acquired,events=count,fullRowDifferences=0,extraRows=0,missingRows=0,cpAckSha256=sha(ack),rawInputs=[sha(p) for p in captures],checkpoint=checkpoint)
        (N/(kind+'-proof.json')).write_text(json.dumps(proof,indent=2));proofs.append(proof)
        run(kind+'-logs',['docker','logs',H]);run(kind+'-stop',['docker','stop','-t','20',H]);run(kind+'-remove',['docker','rm',H]);helper=False
    (N/'proof.json').write_text(json.dumps(dict(candidate=candidate,scenarios=proofs,sourceDml=2,oldSourceDmlReplayed=0,sqlUsedAsEventInput=False),indent=2));print('ARRAY_TEMPORAL_HTTP_TARGET_CPACK_PASS',flush=True)
finally:
    try:
        if helper:run('failure-logs',['docker','logs',H]);run('failure-stop',['docker','stop','-t','20',H]);run('failure-remove',['docker','rm',H])
    finally:
        if started:run('source-stop',['docker','stop','-t','60',S])
