"""New mixed-field HTTP/target/CPACK windows; previous source transactions are never replayed."""
from pathlib import Path
import hashlib,json,os,secrets,shutil,subprocess,sys,time,zipfile
R=Path(__file__).resolve().parent.parent;BASE=R/'lab/out/common-fields-20261010'
inventoryResume=sys.argv[1:]==['--resume-control-inventory']
resume=inventoryResume or sys.argv[1:]==['--resume-pre-source']
BUILD=BASE/('product-build-r3' if resume else 'product-build-r2');N=BASE/('product-runtime-r3' if inventoryResume else 'product-runtime-r2' if resume else 'product-runtime-r1')
S='oraredo-19c-lab-20261004';H='ork-product-runtime';PORT=37301
assert (resume or sys.argv[1:]==['--execute']) and (BUILD/'candidate.json').is_file()
if resume:
    previous=BASE/'product-runtime-r1'
    assert (previous/'source-stop.rc').read_text().strip()=='0' and (previous/'failure-remove.rc').read_text().strip()=='0'
    assert (previous/'B-provision.rc').read_text().strip()=='0' and not list(previous.glob('*-source-action.started'))
    assert 'V729__sh_route.sql' in (previous/'failure-logs.stdout').read_text(errors='replace')
    if inventoryResume:
        failed=BASE/'product-runtime-r2'
        assert (failed/'source-stop.rc').read_text().strip()=='0' and (failed/'failed-control-inventory.rc').read_text().strip()=='1'
        assert not (failed/'failed-control-drop.rc').exists() and not list(failed.glob('*-source-action.started'))
N.mkdir(exist_ok=False);shutil.copyfile(__file__,N/'executed.py')
props=dict(line.split('=',1) for line in (R/'lab/out/d4-lob-product-20261010/runtime.secret.env').read_text().splitlines())
reader=(R/'lab/out/dictionary-baseline-candidate-20261006/source.secret').read_text().splitlines()
passwords=[v.encode() for k,v in props.items() if 'PASSWORD' in k]+[reader[1].encode()]
sha=lambda p:hashlib.sha256(Path(p).read_bytes()).hexdigest()
candidate=json.loads((BUILD/'candidate.json').read_text());app=BUILD/'application.jar'
assert sha(app)==candidate['applicationSha256'];shutil.copyfile(app,N/'application.jar')
for folder in ['acks','prefix-state','failures','inputs','snapshots','libs','classes']:(N/folder).mkdir()
with zipfile.ZipFile(app) as archive:
    for name in archive.namelist():
        if name.startswith('BOOT-INF/lib/') and name.endswith('.jar'):(N/'libs'/Path(name).name).write_bytes(archive.read(name))
assert sha(N/'libs'/('oracle-redo-kernel-'+candidate['kernelVersion']+'.jar'))==candidate['kernelSha256']
assert sha(N/'libs'/('streamhouse-engine-oracle-'+candidate['adapterVersion']+'.jar'))==candidate['adapterSha256']
cp=os.pathsep.join([str(N/'classes'),str(N/'libs/*')])

def run(label,args,data=None):
    assert not (N/(label+'.rc')).exists(),label
    p=subprocess.run(list(map(str,args)),input=data,capture_output=True,timeout=300)
    for suffix,value in [('stdout',p.stdout),('stderr',p.stderr),('rc',str(p.returncode).encode())]:
        for password in passwords:value=value.replace(password,b'<redacted-secret>')
        (N/(label+'.'+suffix)).write_bytes(value)
    assert p.returncode==0,(label,p.returncode)
    return p.stdout.decode()

def sql(label,body,private=False):
    text='whenever sqlerror exit failure rollback\nwhenever oserror exit failure rollback\nset pages 0 heading off feedback off lines 32767 long 300000 longchunksize 300000 trimspool on\n'+body+'\nexit success\n'
    if not private:(N/(label+'.sql')).write_text(text)
    result=run(label,['docker','exec','-i',S,'sqlplus','-s','/','as','sysdba'],text.encode())
    assert 'SP2-' not in result
    return result

def http(label,path,data=None):
    login=json.loads((R/'lab/out/product-active-20261007/admin.secret.json').read_text())
    code='''import json,urllib.request
base=BASE
def request(path,data=None,token=None):
 headers={'Content-Type':'application/json'}
 if token:headers['Authorization']='Bearer '+token
 with urllib.request.urlopen(urllib.request.Request(base+path,data=None if data is None else json.dumps(data).encode(),headers=headers),timeout=120) as response:return json.loads(response.read())
login=request('/auth/login',LOGIN);assert login['code']==0
result=request(PATH,DATA,login['data']['token']);print(json.dumps(result))
'''.replace('BASE',repr('http://127.0.0.1:'+str(PORT)+'/api/v1')).replace('LOGIN',repr(login)).replace('PATH',repr(path)).replace('DATA',repr(data))
    result=json.loads(run(label,['docker','exec','-i',S,'python3','-'],code.encode()))
    assert result['code']==0,(label,result)
    return result['data']

def clob(value):
    text=json.dumps(value,separators=(',',':'))
    return '||\n'.join("to_clob('"+text[i:i+1800].replace("'","''")+"')" for i in range(0,len(text),1800))

run('compile',['D:/work/Java/jdk-17.0.7/bin/javac.exe','-cp',cp,'-d',N/'classes',R/'lab/LobProductPreparationProbe.java'])
raw=BASE/'source/resume-r2/104.raw'
assert sha(raw)=='6658fce9c8f41e836e901c39954469abadf45926f1a756658f46ccd18fdd6290'
os.link(raw,N/'inputs/104.raw')
source=dict(dbid=1276726821,activationId=1276717861,compatibility=318767104,thread=1,resetlogsId=1245693159,resetlogsScn=1920977,containerUid=677737149,containerId=3)
started=False;helper=False;proofs=[]
try:
    assert H not in run('helpers',['docker','ps','-a','--format','{{.Names}}']).splitlines()
    assert run('source-state',['docker','inspect',S,'--format','{{.Config.Labels.owner}}|{{.State.Status}}']).strip()=='OracleRedoKernel|exited'
    run('start',['docker','start',S]);started=True
    for attempt in range(30):
        try:
            pre=sql('ready-'+str(attempt),"select 'LOGGING|'||supplemental_log_data_min||'|'||supplemental_log_data_all from v$database;\nalter session set container=ORKPDB;\nselect 'READY|'||count(*) from ORKLAB.T_D4_MIX_S_20261010;\nselect 'FREE|'||sum(bytes) from dba_free_space where tablespace_name='USERS';")
            if 'READY|1' in pre:break
        except AssertionError:
            if attempt==29:raise
        time.sleep(3)
    assert 'LOGGING|NO|NO' in pre and 'READY|1' in pre
    if resume:
        # Only the empty failed control schema created by r1; source/target schemas are preserved.
        inventory=sql('failed-control-inventory',"alter session set container=ORKPDB;\nselect 'NO_BINDING_TABLE|'||count(*) from dba_tables where owner='ORKCMBCTL_20261010' and table_name='SH_ORACLE_INPUT_BINDING';\nselect 'EMPTY_EVENTS|'||count(*) from ORKCMBCTL_20261010.SH_EVENT_LEDGER;\nselect 'TABLE|'||table_name from dba_tables where owner='ORKCMBCTL_20261010' order by table_name;")
        assert 'NO_BINDING_TABLE|0' in inventory and 'EMPTY_EVENTS|0' in inventory
        sql('failed-control-drop','alter session set container=ORKPDB;\ndrop user ORKCMBCTL_20261010 cascade;')
    # Fresh control schemas, no reuse of the old Flyway 729-732 migration history.
    for ordinal,kind in enumerate(['B','S']):
        U='ORKCM'+kind+'CTL_20261010';SOURCE='oracle-common-'+kind.lower();prefix='CMHTTP'+kind+'1'
        owner=props['SH_ORACLE_BOUNDED_TARGET_OWNER'];T=owner+'.'+prefix;TARGET='oracle-common-'+prefix
        table='T_D4_MIX_'+kind+'_20261010';window='mixed-'+kind.lower()
        pre=sql(kind+'-free',f"alter session set container=ORKPDB;\nselect 'FREE_USER|'||count(*) from dba_users where username='{U}';\nselect 'FREE_TARGET|'||count(*) from dba_tables where owner='{owner}' and table_name like '{prefix}\\_%' escape '\\';\nselect 'SOURCE_READY|'||count(*) from ORKLAB.{table} where ID=1 and TXT='mixed-text' and dbms_lob.getlength(B)=10000 and dbms_lob.getlength(C)=5000 and dbms_lob.getlength(J)=5012 and dbms_lob.getlength(X)=5007;")
        assert all(v in pre for v in ['FREE_USER|0','FREE_TARGET|0','SOURCE_READY|1'])
        password='P'+secrets.token_hex(12)+'a1';passwords.append(password.encode())
        sql(kind+'-provision',f'alter session set container=ORKPDB;\ncreate user {U} identified by "{password}" default tablespace USERS quota unlimited on USERS;\ngrant create session,create table,create sequence to {U};',private=True)
        props.update(SPRING_DATASOURCE_USERNAME=U,SPRING_DATASOURCE_PASSWORD=password,SERVER_PORT=str(PORT),SH_WORKER_ID=str(71+ordinal),SH_ORACLE_RAW_DISCOVERY_ENABLED='false',SH_ORACLE_RAW_PLAN_ENABLED='false',SH_PIPELINE_AUTO_CONSUME_ENABLED='true',SH_PIPELINE_AUTO_CONSUME_SOURCE_IDS=SOURCE,SH_ORACLE_PREFIX_INPUT_DIRECTORY='/evidence/inputs',SH_ORACLE_PREFIX_CHECKPOINT_DIRECTORY='/evidence/prefix-state',SH_ORACLE_BOUNDED_ACK_DIRECTORY='/evidence/acks',SH_ORACLE_BOUNDED_TARGET_ID=TARGET,SH_ORACLE_BOUNDED_TARGET_PREFIX=prefix,SH_PIPELINE_TARGET_APPLY_FAIL_DIR='/evidence/failures',SH_ORACLE_ACQUISITION_ENABLED='true',SH_ORACLE_ACQUISITION_SOURCE_ID=SOURCE,SH_ORACLE_ACQUISITION_AUTHORITY='owned-common-normal-acquisition',SH_ORACLE_ACQUISITION_SOURCE_JSON=json.dumps(source,separators=(',',':')),SH_ORACLE_ACQUISITION_OWNER='ORKLAB',SH_ORACLE_ACQUISITION_TABLE=table,SH_ORACLE_ACQUISITION_INDEX='PK_D4_MIX_'+kind+'_20261010',SH_ORACLE_ACQUISITION_DIRECTORY='/evidence/snapshots',SH_ORACLE_ACQUISITION_USER=reader[0],SH_ORACLE_ACQUISITION_PASSWORD=reader[1],SH_ORACLE_ACQUISITION_URL=reader[2])
        (N/(kind+'-runtime.secret.env')).write_text(''.join(k+'='+v+'\n' for k,v in props.items()))
        run(kind+'-helper-start',['docker','run','-d','--name',H,'--label','owner=OracleRedoKernel','--label','scope=oracle-common-product','--user','0','--network','container:'+S,'--workdir','/evidence','--env-file',N/(kind+'-runtime.secret.env'),'--mount','type=bind,source='+str(N)+',target=/evidence','--entrypoint','java','flink:1.18-java17','-Xmx1536m','-jar','/evidence/application.jar']);helper=True
        for attempt in range(40):
            try:http(f'{kind}-health-{attempt}','/system/info');break
            except (AssertionError,ValueError):
                state=run(f'{kind}-health-state-{attempt}',['docker','inspect',H,'--format','{{.State.Status}}']).strip()
                if state=='exited':raise AssertionError('PRODUCT_STARTUP_EXITED')
                if attempt==39:raise
                time.sleep(3)
        sql(kind+'-target',f"alter session set container=ORKPDB;\ncreate table {T}_ROWS(ID number primary key,TXT varchar2(128),BIN raw(16),DT date,TS timestamp(6),B blob,C clob,J clob,X clob);\ninsert into {T}_ROWS select ID,TXT,BIN,DT,TS,B,C,J,X from ORKLAB.{table};\nalter table {U}.SH_ORACLE_ACQUISITION allocate extent;\ncommit;")
        acquired=http(kind+'-acquire','/oracle/acquisitions/historical',dict(sourceId=SOURCE));ACQ=int(acquired['id'])
        verified=http(kind+'-verified',f'/oracle/acquisitions/{ACQ}')
        assert {k:v for k,v in verified.items() if k!='registeredAt'}=={k:v for k,v in acquired.items() if k!='registeredAt'}
        baseline=N/'snapshots'/f'{ACQ}.snapshot';assert sha(baseline)==acquired['snapshotSha256']
        assert 'ANCHOR|1' in sql(kind+'-anchor',f"alter session set container=ORKPDB;\nselect 'ANCHOR|'||count(*) from {T}_ROWS t join ORKLAB.{table} as of scn {acquired['snapshotScn']} s on s.ID=t.ID where t.TXT=s.TXT and t.BIN=s.BIN and t.DT=s.DT and t.TS=s.TS and dbms_lob.compare(t.B,s.B)=0 and dbms_lob.compare(t.C,s.C)=0 and dbms_lob.compare(t.J,s.J)=0 and dbms_lob.compare(t.X,s.X)=0;")
        (N/(kind+'-source-action.started')).write_text('distinct new TXT-only post-acquisition transaction; never replay\n')
        sql(kind+'-source-action',f"alter session set container=ORKPDB;\nbegin update ORKLAB.{table} set TXT='product-mixed-{kind.lower()}' where ID=1 and TXT='mixed-text';if sql%rowcount<>1 then raise_application_error(-20001,'ROW_COUNT');end if;end;\n/\ncommit;")
        sql(kind+'-archive','alter system archive log current;')
        paths=sql(kind+'-paths',"select 'PATH|'||sequence#||'|'||name from v$archived_log where sequence#>=105 and archived='YES' and deleted='NO' and name is not null order by sequence#,recid;")
        byseq={int(l.split('|')[1]):l.split('|')[2] for l in paths.splitlines() if l.startswith('PATH|')}
        assert sorted(byseq)==list(range(105,max(byseq)+1))
        for seq,path in sorted(byseq.items()):
            local=N/'inputs'/f'{seq}.raw'
            if not local.exists():
                run(f'copy-{seq}',['docker','cp',S+':'+path,local])
                assert run(f'sha-{seq}',['docker','exec',S,'sha256sum',path]).split()[0]==sha(local)
        captures=[N/'inputs'/f'{seq}.raw' for seq in range(104,max(byseq)+1)]
        prepared=json.loads(run(kind+'-preparation',['D:/work/Java/jdk-17.0.7/bin/java.exe','-Xmx1536m','-cp',cp,'LobProductPreparationProbe',baseline,acquired['snapshotSha256'],*captures]));e=prepared['evidence'];events=prepared['events']
        assert len(events)==1 and events[0]['operation']=='UPDATE' and events[0]['after']['TXT']=='product-mixed-'+kind.lower()
        assert list(events[0]['after'])==['ID','TXT','BIN','DT','TS','B','C','J','X']
        old=json.loads((BASE/'verify-r15'/(kind+'.stdout')).read_text())['events'][0]['after']
        assert all(events[0]['before'][col]==old[col] and events[0]['after'][col]==old[col] for col in old if col!='TXT')
        identity=':'.join(str(e['source'][k]) for k in ['dbid','activationId','compatibility','thread','resetlogsId','resetlogsScn','containerUid','containerId'])
        sql(kind+'-ledger',f"alter session set container=ORKPDB;\ncreate table {T}_IDENT(ID number primary key,TARGET_ID varchar2(128) not null,SOURCE_BINDING varchar2(512) not null,SCHEMA_SHA varchar2(64) not null);\ninsert into {T}_IDENT values(1,'{TARGET}','{identity}','{e['schemaSha256']}');\ncreate table {T}_APPLIED(EVENT_ID varchar2(80) primary key,PAYLOAD_SHA varchar2(64) not null,BEFORE_IMAGE clob,AFTER_IMAGE clob,OPERATION varchar2(16) not null);\ncreate table {T}_MEMBERS(BATCH_SHA varchar2(64),ORDINAL number,EVENT_ID varchar2(80) references {T}_APPLIED(EVENT_ID),primary key(BATCH_SHA,ORDINAL),unique(BATCH_SHA,EVENT_ID));\ncreate table {T}_COMMITS(BATCH_SHA varchar2(64) primary key,TARGET_ID varchar2(128) not null,SCHEMA_SHA varchar2(64) not null,EVENTS_COUNT number not null);\ncommit;")
        binding=dict(baselinePath=None,capturePaths=['/evidence/inputs/'+p.name for p in captures],evidence=e,envelopesSha256=prepared['envelopesSha256'],acquisitionId=ACQ,inputFormat='RAW_MEMBER')
        hashes='\n'.join(p.name+' '+sha(p) for p in captures)
        sql(kind+'-enroll',f"alter session set container=ORKPDB;\ninsert into {U}.SH_ORACLE_INPUT_BINDING values(29601,'{SOURCE}','{window}',1,{len(captures)},'v3-history-consumer','{e['schemaSha256']}','APPROVED_BOUNDED',"+clob(binding)+f",systimestamp,'owned-normal-common');\ninsert into {U}.SH_CAP_WINDOW(ID,SOURCE_ID,ENGINE_TYPE,WINDOW_KEY,ISSUE_SEQ_START,ISSUE_SEQ_END,HEAD_PATH,FROZEN,FILE_SHA256,TOTAL_SIZE,DROPPED_SEGMENTS,STATUS,REGISTERED_AT) values(29602,'{SOURCE}','oracle','{window}',1,{len(captures)},'/evidence/inputs/104.raw',1,'{hashes}',{sum(p.stat().st_size for p in captures)},0,'PENDING',systimestamp);\ncommit;")
        for attempt in range(60):
            status=sql(f'{kind}-wait-{attempt}',f"alter session set container=ORKPDB;\nselect 'BATCH|'||STATE||'|'||RC||'|'||EVENT_COUNT from {U}.SH_PIPELINE_BATCH where WINDOW_KEY='{window}';")
            if 'BATCH|PUBLISHED|0|1' in status:break
            if 'FAILED' in status:raise AssertionError(status)
            time.sleep(2)
        else:raise AssertionError('Mixed publication timeout')
        actual=sql(kind+'-actual',f"""alter session set container=ORKPDB;
select 'COUNTS|'||(select count(*) from {T}_APPLIED)||'|'||(select count(*) from {T}_MEMBERS)||'|'||(select count(*) from {T}_COMMITS)||'|'||(select count(*) from {U}.SH_EVENT_LEDGER) from dual;
select 'MATCH|'||count(*) from {T}_ROWS t join ORKLAB.{table} s on s.ID=t.ID where t.TXT=s.TXT and t.BIN=s.BIN and t.DT=s.DT and t.TS=s.TS and dbms_lob.compare(t.B,s.B)=0 and dbms_lob.getlength(t.B)=dbms_lob.getlength(s.B) and dbms_lob.compare(t.C,s.C)=0 and dbms_lob.getlength(t.C)=dbms_lob.getlength(s.C) and dbms_lob.compare(t.J,s.J)=0 and dbms_lob.getlength(t.J)=dbms_lob.getlength(s.J) and dbms_lob.compare(t.X,s.X)=0 and dbms_lob.getlength(t.X)=dbms_lob.getlength(s.X);
select 'EVENT|'||EVENT_ID||'|'||PAYLOAD_SHA from {T}_APPLIED;
select 'COMMIT|'||BATCH_SHA||'|'||SCHEMA_SHA||'|'||EVENTS_COUNT from {T}_COMMITS;
select 'WINDOW|'||STATUS from {U}.SH_CAP_WINDOW;
set serveroutput on size unlimited
begin for r in (select ID,STATE,CHECKPOINT_JSON from {U}.SH_TASK where CHECKPOINT_JSON is not null) loop
dbms_output.put_line('TASK|'||r.ID||'|'||r.STATE);
for i in 0..trunc((dbms_lob.getlength(r.CHECKPOINT_JSON)-1)/1000) loop dbms_output.put_line('CPHEX|'||i||'|'||rawtohex(utl_raw.cast_to_raw(dbms_lob.substr(r.CHECKPOINT_JSON,1000,1+i*1000))));end loop;end loop;end;
/
""")
        assert 'COUNTS|1|1|1|1' in actual and 'MATCH|1' in actual and 'WINDOW|CONSUMED' in actual
        assert f"EVENT|{e['events'][0]['eventId']}|{e['events'][0]['payloadSha256']}" in actual and f"COMMIT|{e['batchSha256']}|{e['schemaSha256']}|1" in actual
        tasks=[l.split('|')[1:] for l in actual.splitlines() if l.startswith('TASK|')];assert len(tasks)==1 and tasks[0][1]=='DONE'
        parts=sorted((int(l.split('|')[1]),l.split('|')[2]) for l in actual.splitlines() if l.startswith('CPHEX|'));assert [i for i,_ in parts]==list(range(len(parts)))
        checkpointBytes=bytes.fromhex(''.join(part for _,part in parts));checkpoint=json.loads(checkpointBytes)
        ack=N/'acks'/(hashlib.sha256((SOURCE+'\0'+window).encode()).hexdigest()+'.ack.json')
        for attempt in range(30):
            if ack.exists():break
            time.sleep(1)
        assert ack.read_bytes()==checkpointBytes and dict(checkpoint['oracleEvidence'],eventSchemas=[],schemaSequenceSha256=None)==e
        proof=dict(storage=kind,acquisition=acquired,events=1,columns=9,target=[1,1,1,1],fullRowDifferences=0,cpAckSha256=sha(ack),rawInputs=[sha(p) for p in captures],checkpoint=checkpoint)
        (N/(kind+'-proof.json')).write_text(json.dumps(proof,indent=2));proofs.append(proof)
        run(kind+'-logs',['docker','logs',H]);run(kind+'-stop',['docker','stop','-t','20',H]);run(kind+'-remove',['docker','rm',H]);helper=False
    (N/'proof.json').write_text(json.dumps(dict(candidate=candidate,scenarios=proofs,sourceDml=2,oldSourceDmlReplayed=0,oldEventsPublished=0,sqlUsedAsEventInput=False),indent=2))
    print('MIXED_HTTP_TARGET_CPACK_PASS',flush=True)
finally:
    try:
        if helper:
            run('failure-logs',['docker','logs',H]);run('failure-stop',['docker','stop','-t','20',H]);run('failure-remove',['docker','rm',H])
    finally:
        if started:run('source-stop',['docker','stop','-t','60',S])
