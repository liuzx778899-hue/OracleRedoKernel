"""One-shot interleaved fixture; preserves all evidence and stops the owned runtime in finally."""
from pathlib import Path
from decimal import Decimal
import base64,hashlib,json,os,shutil,subprocess,sys,time

R=Path('D:/work/vibecode/OracleRedoKernel')
N=R/'lab/out/d3-stream-runtime-20261009'
label='pending-r15'
sys.argv=[str(__file__),'pending',label]
exec((R/'lab/d3_stream_runtime.py').read_text(encoding='utf-8').split("if action=='prepare':")[0])
PY=sys.executable
SOURCE_MARK=N/'pending-source-actions-started.txt'
assert not SOURCE_MARK.exists(),'never replay interleaved source fixture'
candidate=R/'lab/out/d3-stream-candidate-20261009/r15'
product=json.loads((candidate/'product-proof.json').read_text())
application=candidate/'source-python/server/streamhouse-app/target/streamhouse-app-0.1.0-SNAPSHOT.jar'
assert sha(application)==product['applicationSha256']
kernel=R/'lab/out/d3-stream-candidate-20261009/kernel-r14/source/target/oracle-redo-kernel-0.1.0-SNAPSHOT.jar'
assert sha(kernel)=='91a0d37dfa3c568e9c3654a12da4db05c024024b28adba8267080d11e820390f'
classes=N/(label+'-classes');classes.mkdir()
cp=str(kernel)+';'+str(classes)+';'+(R/'lab/out/d3-discovery-20261008/stream-test-classpath-20261009.txt').read_text().strip()
run(label+'-compile',['D:/work/Java/jdk-17.0.7/bin/javac.exe','-cp',cp,'-d',classes,R/'lab/StreamPendingRuntimeProbe.java'])
old_acks={p.name:p.read_bytes() for p in (N/'acks').glob('*.ack.json')}
shutil.copyfile(N/'application.jar',N/(label+'-prior-application.jar'))
shutil.copyfile(application,N/'application.jar')
(N/(label+'-candidate-proof.json')).write_text(json.dumps(product,indent=2))
session=None;started=False

def driver(action,name):
    return run(name+'-driver',[PY,R/'lab/d3_stream_runtime.py',action,name])

def archive(sequence):
    name=label+'-'+str(sequence)
    sql(name+'-archive','alter system archive log current;')
    out=sql(name+'-path',f"select 'PATH|'||name from v$archived_log where sequence#={sequence} and archived='YES' and deleted='NO' and name is not null order by recid;")
    paths={line.split('|',1)[1] for line in out.splitlines() if line.startswith('PATH|')};assert len(paths)==1
    path=paths.pop();local=N/f'new-inputs/{sequence}.raw';partial=local.with_suffix('.raw.partial');assert not local.exists()
    run(name+'-copy',['docker','cp',S+':'+path,partial]);assert run(name+'-sha',['docker','exec',S,'sha256sum',path]).split()[0]==sha(partial)
    partial.rename(local)
    (N/(name+'-archive.json')).write_text(json.dumps(dict(sequence=sequence,sourcePath=path,sha256=sha(local),bytes=local.stat().st_size),indent=2))

def ready(sequence):
    for attempt in range(30):
        out=sql(f'{label}-{sequence}-wait-{attempt}',f"alter session set container=ORKPDB;\nselect 'READY|'||count(*) from {U}.SH_CAP_WINDOW where WINDOW_KEY='raw-stream-12001-{sequence-89}' and STATUS='CONSUMED';")
        if 'READY|1' in out:return
        time.sleep(5)
    raise AssertionError('normal stream consumption timeout')

def image(values):
    return ';'.join(k+'='+('NULL' if v is None else 'NUMBER:'+format(v.normalize(),'f') if isinstance(v,Decimal) else 'NUMBER:'+str(v) if isinstance(v,(int,float)) else 'VARCHAR2:'+base64.urlsafe_b64encode(v.encode()).decode().rstrip('=')) for k,v in values.items())

def proof(sequence,published,pending,previous):
    name=label+'-'+str(sequence);key=f'raw-stream-12001-{sequence-89}';t='ORKTGT_CMT_20261005.SHPBL1'
    args=[OLD.parent/'snapshots/101673191742861312.snapshot','f85525d7aa20597a4f8eeaa9accd0358192bfed9175ad0ca1c52fc0f55c08f4c',published,pending]
    for seq in range(90,sequence+1):
        raw=CAP/f'{seq}.raw' if seq<92 else N/f'new-inputs/{seq}.raw';args.extend([raw,sha(raw)])
    projection=json.loads(run(name+'-raw',['D:/work/Java/jdk-17.0.7/bin/java.exe','-cp',cp,'StreamPendingRuntimeProbe']+args),parse_float=Decimal)
    evidence=projection['evidence'];batch=evidence['batchSha256'];total=published+2;commits=sequence-90
    out=sql(name+'-actual',f"""alter session set container=ORKPDB;
select 'COUNTS|'||(select count(*) from {t}_APPLIED)||'|'||(select count(*) from {t}_MEMBERS)||'|'||(select count(*) from {t}_COMMITS)||'|'||(select count(*) from {U}.SH_EVENT_LEDGER)||'|'||(select count(*) from {U}.SH_PUBLISH_GATE where VERDICT='PASS') from dual;
select 'EVENT|'||m.ORDINAL||'|'||m.EVENT_ID||'|'||a.PAYLOAD_SHA||'|'||a.BEFORE_IMAGE||'|'||a.AFTER_IMAGE||'|'||a.OPERATION from {t}_MEMBERS m join {t}_APPLIED a on a.EVENT_ID=m.EVENT_ID where m.BATCH_SHA='{batch}' order by m.ORDINAL;
select 'COMMIT|'||BATCH_SHA||'|'||TARGET_ID||'|'||SCHEMA_SHA||'|'||EVENTS_COUNT from {t}_COMMITS where BATCH_SHA='{batch}';
select 'WINDOW|'||STATUS||'|'||ISSUE_SEQ_START||'|'||ISSUE_SEQ_END from {U}.SH_CAP_WINDOW where WINDOW_KEY='{key}';
select 'SOURCE|'||count(*) from ORKLAB.T_D2_BLOCKS_20261008 where NOTE='anchor' and TXT=rpad('block-seed',100,'x');
select 'OPEN_A|'||count(*) from v$session s join v$transaction t on s.taddr=t.addr where s.client_identifier='ORK_PENDING_A_20261009';
set serveroutput on size unlimited
begin
for r in (select ID,STATE,CHECKPOINT_JSON from {U}.SH_TASK where CHECKPOINT_JSON is not null) loop
dbms_output.put_line('TASK|'||r.ID||'|'||r.STATE);
for i in 0..trunc((dbms_lob.getlength(r.CHECKPOINT_JSON)-1)/1000) loop
dbms_output.put_line('CPHEX|'||r.ID||'|'||i||'|'||rawtohex(utl_raw.cast_to_raw(dbms_lob.substr(r.CHECKPOINT_JSON,1000,1+i*1000))));
end loop;end loop;
for r in (select BINDING_JSON from {U}.SH_ORACLE_INPUT_BINDING where WINDOW_KEY='{key}') loop
for i in 0..trunc((dbms_lob.getlength(r.BINDING_JSON)-1)/1000) loop
dbms_output.put_line('BINDINGHEX|'||i||'|'||rawtohex(utl_raw.cast_to_raw(dbms_lob.substr(r.BINDING_JSON,1000,1+i*1000))));
end loop;end loop;end;
/""")
    lines=[line.strip().split('|') for line in out.splitlines() if '|' in line]
    select=lambda kind:[line[1:] for line in lines if line[0]==kind]
    assert select('COUNTS')==[[str(total),str(total),str(commits),str(total),str(commits)]]
    assert select('WINDOW')==[['CONSUMED',str(sequence-89),str(sequence-89)]] and select('SOURCE')==[['20']] and select('OPEN_A')==[[str(pending)]]
    assert select('EVENT')==[[str(i),event['eventId'],meta['payloadSha256'],image(event['before']),image(event['after']),event['operation']] for i,(event,meta) in enumerate(zip(projection['events'],evidence['events']))]
    assert select('COMMIT')==[[batch,'oracle-d2-blocks-SHPBL1-20261008',evidence['schemaSequenceSha256'],'2']]
    checkpoints={}
    for task,state in select('TASK'):
        chunks=sorted((int(i),data) for tid,i,data in select('CPHEX') if tid==task);assert [i for i,_ in chunks]==list(range(len(chunks))) and state=='DONE'
        checkpoints[task]=bytes.fromhex(''.join(data for _,data in chunks))
    actual=[(tid,raw,json.loads(raw)) for tid,raw in checkpoints.items() if json.loads(raw)['lastWindowKey']==key];assert len(actual)==1
    task,raw,checkpoint=actual[0];assert checkpoint['oracleEvidence']==evidence
    ack=N/'acks'/(hashlib.sha256((SOURCE+'\0'+key).encode()).hexdigest()+'.ack.json');assert ack.read_bytes()==raw
    binding=json.loads(bytes.fromhex(''.join(data for _,data in sorted((int(i),data) for i,data in select('BINDINGHEX')))))
    assert binding.get('committedPrefix',False)==bool(pending)
    assert binding['publication']==dict(previousTaskId=int(previous['taskId']),previousBatchId=previous['batchId'],publishedEvents=published,prefixSha256=projection['prefixSha256'])
    for filename,data in old_acks.items():assert (N/'acks'/filename).read_bytes()==data
    old_acks[ack.name]=ack.read_bytes()
    result=dict(taskId=task,batchId=checkpoint['lastBatchId'],fullEvents=total,incrementalEvents=2,pendingTransactions=pending,target=[total,total,commits],cpAckSha256=sha(ack),rawSha256=sha(N/f'new-inputs/{sequence}.raw'),mismatches=0,independentTargetAccepted=False)
    (N/(name+'-summary.json')).write_text(json.dumps(result,indent=2));print(json.dumps(result),flush=True);return result

try:
    assert HELPER not in run(label+'-containers',['docker','ps','-a','--format','{{.Names}}']).splitlines()
    assert run(label+'-source-state',['docker','inspect',S,'--format','{{.State.Status}}']).strip()=='exited'
    run(label+'-source-start',['docker','start',S]);started=True
    for attempt in range(30):
        try:
            pre=sql(f'{label}-preflight-{attempt}',"select 'CURRENT|'||sequence# from v$log where status='CURRENT';\nselect 'LOGGING|'||supplemental_log_data_min||'|'||supplemental_log_data_all from v$database;\nalter session set container=ORKPDB;\nselect 'ROWS|'||count(*) from ORKLAB.T_D2_BLOCKS_20261008 where TXT=rpad('block-seed',100,'x') and NOTE='anchor';")
            if 'CURRENT|' in pre and 'ROWS|' in pre:break
            raise AssertionError('source not ready')
        except AssertionError:
            if attempt==29:raise
            time.sleep(5)
    assert 'CURRENT|93' in pre and 'ROWS|20' in pre and 'LOGGING|NO|NO' in pre
    driver('start',label+'-start')
    for attempt in range(30):
        try:http(f'{label}-health-{attempt}','/system/info');break
        except (AssertionError,ValueError):
            if attempt==29:raise
            time.sleep(5)
    driver('machine',label+'-machine')
    config=(N/'collector.ini').read_text().replace('member_count=3','member_count=5').replace('spool_dir=', 'member_4=/evidence/new-inputs/93.raw\nmember_5=/evidence/new-inputs/94.raw\nspool_dir=')
    (N/'collector.ini').write_bytes(config.encode());driver('collect',label+'-collect')
    print('runtime ready; beginning the one-shot interleaved fixture',flush=True)
    SOURCE_MARK.write_text('Only new93/94. A pending, B committed first, then A commit. Never replay.\n')
    session=subprocess.Popen(['docker','exec','-i',S,'sqlplus','-s','/','as','sysdba'],stdin=subprocess.PIPE,stdout=(N/(label+'-A.stdout')).open('xb'),stderr=(N/(label+'-A.stderr')).open('xb'))
    session.stdin.write(b"whenever sqlerror exit failure rollback\nwhenever oserror exit failure rollback\nalter session set container=ORKPDB;\nexec dbms_session.set_identifier('ORK_PENDING_A_20261009');\nbegin update ORKLAB.T_D2_BLOCKS_20261008 set NOTE='pending-A' where ID=1 and NOTE='anchor';if sql%rowcount<>1 then raise_application_error(-20001,'A rowcount');end if;end;\n/\nprompt A_READY\n");session.stdin.flush()
    for attempt in range(20):
        out=sql(f'{label}-A-open-{attempt}',"select 'OPEN_A|'||count(*) from v$session s join v$transaction t on s.taddr=t.addr where s.client_identifier='ORK_PENDING_A_20261009';")
        if 'OPEN_A|1' in out:break
        assert session.poll() is None;time.sleep(1)
    else:raise AssertionError('fixture A did not open')
    sql(label+'-B',"alter session set container=ORKPDB;\nbegin update ORKLAB.T_D2_BLOCKS_20261008 set NOTE='closed-B' where ID=2 and NOTE='anchor';if sql%rowcount<>1 then raise_application_error(-20002,'B rowcount');end if;end;\n/\ncommit;\nbegin update ORKLAB.T_D2_BLOCKS_20261008 set NOTE='anchor' where ID=2 and NOTE='closed-B';if sql%rowcount<>1 then raise_application_error(-20003,'B restore rowcount');end if;end;\n/\ncommit;")
    archive(93);ready(93)
    previous=json.loads((N/'incremental-proof-r13-summary.json').read_text());previous=proof(93,42,1,previous)
    session.stdin.write(b'commit;\nprompt A_COMMITTED\nexit success\n');session.stdin.flush();session.stdin.close();rc=session.wait(timeout=30)
    (N/(label+'-A.rc')).write_text(str(rc));assert rc==0;session=None
    sql(label+'-A-restore',"alter session set container=ORKPDB;\nbegin update ORKLAB.T_D2_BLOCKS_20261008 set NOTE='anchor' where ID=1 and NOTE='pending-A';if sql%rowcount<>1 then raise_application_error(-20004,'A restore rowcount');end if;end;\n/\ncommit;")
    archive(94);ready(94);proof(94,44,0,previous)
finally:
    if session is not None:
        if session.stdin and not session.stdin.closed:
            try:session.stdin.write(b'rollback;\nexit failure rollback\n');session.stdin.flush()
            except BrokenPipeError:pass
            session.stdin.close()
        try:session.wait(timeout=30)
        except subprocess.TimeoutExpired:session.terminate()
        (N/(label+'-A-terminal.rc')).write_text(str(session.poll()))
    if started:
        try:
            if HELPER in subprocess.check_output(['docker','ps','-a','--format','{{.Names}}']).decode().splitlines():driver('stop',label+'-stop')
        finally:
            run(label+'-source-stop',['docker','stop','--timeout','45',S])
