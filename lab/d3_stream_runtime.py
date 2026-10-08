"""Normal incremental product check; every action has a new label, source actions execute once."""
from pathlib import Path
import base64,hashlib,json,os,shutil,subprocess,sys

R=Path('D:/work/vibecode/OracleRedoKernel')
N=R/'lab/out/d3-stream-runtime-20261009'
C=R/'lab/out/d3-stream-candidate-20261009/r2'
OLD=R/'lab/out/d2-blocks-runtime-20261008/product'
CAP=R/'lab/out/d2-blocks-source-20261008'
S='oraredo-19c-lab-20261004';HELPER='ork-product-runtime';PORT=37301
U='ORKD2BLOCKCTL_20261008';SOURCE='oracle-d2-blocks';GRANT=12001
action=sys.argv[1];label=sys.argv[2]
assert N.is_dir() and label.replace('-','').isalnum()
assert not (N/(label+'.executed.py')).exists(),'use a new label, never replay an executed action'
shutil.copyfile(__file__,N/(label+'.executed.py'))
props=dict(line.split('=',1) for line in (OLD/'runtime.secret.env').read_text().splitlines())
secrets=[v.encode() for k,v in props.items() if 'PASSWORD' in k and v]
if (N/'machine.secret.env').exists():secrets.append((N/'machine.secret.env').read_text().strip().split('=',1)[1].encode())
sha=lambda p:hashlib.sha256(Path(p).read_bytes()).hexdigest()

def run(name,args,data=None,private=False):
    assert not (N/(name+'.rc')).exists(),name
    p=subprocess.run(list(map(str,args)),input=data,capture_output=True,timeout=180)
    for suffix,content in [('stdout',p.stdout),('stderr',p.stderr)]:
        if not private:
            for secret in secrets:content=content.replace(secret,b'<redacted-secret>')
        (N/(name+('.private.' if private else '.')+suffix)).write_bytes(content)
    (N/(name+'.rc')).write_text(str(p.returncode))
    assert p.returncode==0,(name,p.returncode)
    return p.stdout.decode('utf-8')

def sql(name,body):
    text='whenever sqlerror exit failure rollback\nwhenever oserror exit failure rollback\nset pages 0 heading off feedback off lines 32767 long 300000 longchunksize 300000 trimspool on\n'+body+'\nexit success\n'
    (N/(name+'.sql')).write_text(text)
    out=run(name,['docker','exec','-i',S,'sqlplus','-s','/','as','sysdba'],text.encode())
    assert 'SP2-' not in out
    return out

def http(name,path,data=None,private=False):
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
    result=json.loads(run(name,['docker','exec','-i',S,'python3','-'],code.encode(),private))
    assert result['code']==0,(name,result)
    return result['data']

if action=='prepare':
    proof=json.loads((C/'product-proof.json').read_text())
    jar=C/'source-python/server/streamhouse-app/target/streamhouse-app-0.1.0-SNAPSHOT.jar'
    assert sha(jar)==proof['applicationSha256']
    shutil.copyfile(jar,N/'application.jar')
    for name in ['acks','prefix-state']:shutil.copytree(OLD/name,N/name)
    (N/'new-inputs').mkdir()
    binary=R/'lab/out/d3-collector-runtime-20261008/formal-static/sh_agent'
    assert sha(binary)=='f2dbd6eeafb40a61d6c66d23f590d6670ae94ef8d719833a1ccabf2efaa3245e'
    shutil.copyfile(binary,N/'sh_agent')
    props.update(SERVER_PORT=str(PORT),SH_WORKER_ID='58',SH_ORACLE_RAW_PLAN_ENABLED='true',SH_ORACLE_RAW_PLAN_REGISTRATION_ENABLED='false',
        SH_ORACLE_RAW_PLAN_PLAN_JSON=json.dumps(dict(sourceId=SOURCE,transportSourceId=SOURCE,acquisitionId=101673191742861312,firstSequence=90,leaf=50359491,sessions=[1])),
        SH_ORACLE_RAW_DISCOVERY_ENABLED='true',SH_ORACLE_RAW_DISCOVERY_GRANT_ID=str(GRANT),SH_ORACLE_RAW_DISCOVERY_INTERVAL_MS='5000',
        SH_GATEWAY_STAGING_DIR='/evidence/staging',SH_GATEWAY_READY_ROOT_DIR='/evidence/raw-ready',SH_AUTH_MACHINE_TOKEN_REQUIRED='true')
    (N/'runtime.secret.env').write_text(''.join(k+'='+v+'\n' for k,v in props.items()))
elif action=='grant':
    old=sql(label+'-preflight',f"alter session set container=ORKPDB;\nselect 'GRANT|'||count(*) from {U}.SH_ORACLE_INPUT_BINDING where ID={GRANT};\nselect 'TARGET|'||count(*) from ORKTGT_CMT_20261005.SHPBL1_APPLIED;")
    assert 'GRANT|0' in old and 'TARGET|40' in old
    generation=json.loads((OLD/'actual-projection.json').read_text())['evidence']['schemaSha256']
    binding=json.dumps(dict(sourceId=SOURCE,transportSourceId=SOURCE,acquisitionId=101673191742861312,firstSequence=90,leaf=50359491),separators=(',',':'))
    sql(label,f"alter session set container=ORKPDB;\ninsert into {U}.SH_ORACLE_INPUT_BINDING values({GRANT},'{SOURCE}','d3-stream-grant',1,64,'v3-history-consumer','{generation}','APPROVED_RAW_STREAM',to_clob('{binding}'),systimestamp,'owned-stream-normal');\ncommit;")
elif action=='start':
    assert HELPER not in run(label+'-containers',['docker','ps','-a','--format','{{.Names}}']).splitlines()
    if (N/'collector.pid').exists():(N/'collector.pid').rename(N/(label+'-prior-collector.pid'))
    run(label,['docker','run','-d','--name',HELPER,'--label','owner=OracleRedoKernel','--label','scope=oracle-product-runtime',
        '--user','0','--network','container:'+S,'--workdir','/evidence','--env-file',N/'runtime.secret.env',
        '--mount','type=bind,source='+str(N)+',target=/evidence','--mount','type=bind,source='+str(CAP)+',target=/inputs,readonly',
        '--mount','type=bind,source='+str(OLD.parent/'snapshots')+',target=/evidence/snapshots,readonly',
        '--entrypoint','java','flink:1.18-java17','-Xmx1024m','-jar','/evidence/application.jar'])
elif action=='health':
    http(label,'/system/info')
    assert run(label+'-sha',['docker','exec',HELPER,'sha256sum','/evidence/application.jar']).split()[0]==sha(N/'application.jar')
elif action=='machine':
    rows=http(label+'-list','/machines')
    matches=[row for row in rows if row['sourceId']==SOURCE]
    assert len(matches)<=1
    machine=matches[0] if matches else http(label+'-register','/machines',dict(name='ORK-STREAM-COLLECTOR',sourceId=SOURCE,engineType='oracle',host='127.0.0.1'))
    token=http(label+'-rotate','/machines/'+str(machine['id'])+'/agent-token/rotate',{},True)['agentToken']
    (N/'machine.secret.env').write_bytes(('SH_AGENT_MACHINE_TOKEN='+token+'\n').encode())
    config='[agent]\nsource_id='+SOURCE+'\n[oracle]\nmember_count=3\nmember_1=/inputs/90.raw\nmember_2=/inputs/91.raw\nmember_3=/evidence/new-inputs/92.raw\nspool_dir=/evidence/spool\npoll_interval_ms=1000\nmax_spool_bytes=1073741824\n[transfer]\nbase_url=http://127.0.0.1:'+str(PORT)+'\n'
    (N/'collector.ini').write_text(config)
    (N/'run-collector.sh').write_bytes(b'#!/bin/sh\nset -eu\nset -a\n. /evidence/machine.secret.env\nset +a\necho $$ > /evidence/collector.pid\nexec /evidence/sh_agent --collect-oracle /evidence/collector.ini\n')
    run(label+'-permissions',['docker','exec',HELPER,'chmod','700','/evidence/sh_agent','/evidence/run-collector.sh'])
elif action=='collect':
    with (N/(label+'.stdout')).open('xb') as out,(N/(label+'.stderr')).open('xb') as err:
        process=subprocess.Popen(['docker','exec',HELPER,'/evidence/run-collector.sh'],stdout=out,stderr=err)
        (N/(label+'.host-pid.txt')).write_text(str(process.pid))
        (N/'collector-host-pid.txt').write_text(str(process.pid))
elif action=='state':
    sql(label,f"alter session set container=ORKPDB;\nselect 'TARGET|'||(select count(*) from ORKTGT_CMT_20261005.SHPBL1_APPLIED)||'|'||(select count(*) from ORKTGT_CMT_20261005.SHPBL1_MEMBERS)||'|'||(select count(*) from ORKTGT_CMT_20261005.SHPBL1_COMMITS) from dual;\nselect 'TASK|'||to_char(ID,'FM99999999999999999999')||'|'||STATE from {U}.SH_TASK;\nselect 'CP|'||CHECKPOINT_JSON from {U}.SH_TASK;\nselect 'BATCH|'||to_char(ID,'FM99999999999999999999')||'|'||STATE||'|'||RC||'|'||EVENT_COUNT from {U}.SH_PIPELINE_BATCH;\nselect 'WINDOW|'||WINDOW_KEY||'|'||STATUS||'|'||ISSUE_SEQ_START||'|'||ISSUE_SEQ_END from {U}.SH_CAP_WINDOW;\nselect 'RAW|'||STATE||'|'||ISSUE_SEQ_END from {U}.SH_ORACLE_INPUT_BINDING where ID={GRANT} or WINDOW_KEY='raw-discovery-{GRANT}';\nselect 'TRANSFER|'||FILE_NAME||'|'||STATUS||'|'||RECEIVED_OFFSET from {U}.SH_TRANSFER_SESSION where SOURCE_ID='{SOURCE}';\nselect 'FAIL|'||REASON||'|'||DETAIL from {U}.SH_FAILCLOSED_RECORD;")
elif action=='source':
    assert not (N/'source-actions-started.txt').exists(),'never replay source actions'
    pre=sql(label+'-pre',"select 'CURRENT|'||sequence# from v$log where status='CURRENT';\nselect 'LOGGING|'||supplemental_log_data_min||'|'||supplemental_log_data_all from v$database;\nalter session set container=ORKPDB;\nselect 'ROWS|'||count(*) from ORKLAB.T_D2_BLOCKS_20261008 where TXT=rpad('block-seed',100,'x') and NOTE='anchor';")
    assert 'CURRENT|92' in pre and 'ROWS|20' in pre and 'LOGGING|NO|NO' in pre
    (N/'source-actions-started.txt').write_text('new sequence92 incremental fixture; never replay\n')
    sql(label+'-actions',"alter session set container=ORKPDB;\nbegin update ORKLAB.T_D2_BLOCKS_20261008 set NOTE='stream-next' where ID=0 and NOTE='anchor';if sql%rowcount<>1 then raise_application_error(-20001,'rowcount');end if;end;\n/\ncommit;\nbegin update ORKLAB.T_D2_BLOCKS_20261008 set NOTE='anchor' where ID=0 and NOTE='stream-next';if sql%rowcount<>1 then raise_application_error(-20002,'rowcount');end if;end;\n/\ncommit;")
    sql(label+'-archive','alter system archive log current;')
    out=sql(label+'-path',"select 'PATH|'||name from v$archived_log where sequence#=92 and archived='YES' and deleted='NO' and name is not null order by recid;")
    paths={line.split('|',1)[1] for line in out.splitlines() if line.startswith('PATH|')};assert len(paths)==1;path=paths.pop()
    local=N/'new-inputs/92.raw';partial=N/'new-inputs/92.raw.partial';assert not local.exists()
    run(label+'-copy',['docker','cp',S+':'+path,partial])
    assert run(label+'-sha',['docker','exec',S,'sha256sum',path]).split()[0]==sha(partial)
    partial.rename(local)
    (N/'new-archive.json').write_text(json.dumps(dict(sequence=92,path=str(local),sourcePath=path,sha256=sha(local),bytes=local.stat().st_size),indent=2))
elif action=='proof':
    kernel=R/'lab/out/d3-stream-candidate-20261009/kernel-r7/source/target/oracle-redo-kernel-0.1.0-SNAPSHOT.jar'
    assert sha(kernel)==json.loads((kernel.parents[2]/'proof.json').read_text())['kernelSha256']
    classes=N/(label+'-classes');classes.mkdir()
    cp=str(kernel)+';'+str(classes)+';'+(R/'lab/out/d3-discovery-20261008/stream-test-classpath-20261009.txt').read_text().strip()
    run(label+'-compile',['D:/work/Java/jdk-17.0.7/bin/javac.exe','-cp',cp,'-d',classes,R/'lab/StreamIncrementalRuntimeProbe.java'])
    archive=json.loads((N/'new-archive.json').read_text())
    projection=json.loads(run(label+'-raw',['D:/work/Java/jdk-17.0.7/bin/java.exe','-cp',cp,'StreamIncrementalRuntimeProbe',
        OLD.parent/'snapshots/101673191742861312.snapshot','f85525d7aa20597a4f8eeaa9accd0358192bfed9175ad0ca1c52fc0f55c08f4c',
        CAP/'90.raw','44a08e6ec18c86b6a9601b137f1a37ead8c6797769cd4d3b70d67ace6016986b',CAP/'91.raw',
        'e837cd97db88b0212275c1451686911949fb954161cd0d7cc84feecede6b4b2f',N/'new-inputs/92.raw',archive['sha256']]))
    evidence=projection['evidence'];batch=evidence['batchSha256'];key='raw-stream-12001-3';t='ORKTGT_CMT_20261005.SHPBL1'
    out=sql(label+'-actual',f"""alter session set container=ORKPDB;
select 'COUNTS|'||(select count(*) from {t}_APPLIED)||'|'||(select count(*) from {t}_MEMBERS)||'|'||(select count(*) from {t}_COMMITS)||'|'||(select count(*) from {U}.SH_EVENT_LEDGER)||'|'||(select count(*) from {U}.SH_PUBLISH_GATE where VERDICT='PASS') from dual;
select 'EVENT|'||m.ORDINAL||'|'||m.EVENT_ID||'|'||a.PAYLOAD_SHA||'|'||a.BEFORE_IMAGE||'|'||a.AFTER_IMAGE||'|'||a.OPERATION from {t}_MEMBERS m join {t}_APPLIED a on a.EVENT_ID=m.EVENT_ID where m.BATCH_SHA='{batch}' order by m.ORDINAL;
select 'OLD_EVENT|'||m.ORDINAL||'|'||m.EVENT_ID||'|'||a.PAYLOAD_SHA||'|'||a.BEFORE_IMAGE||'|'||a.AFTER_IMAGE||'|'||a.OPERATION from {t}_MEMBERS m join {t}_APPLIED a on a.EVENT_ID=m.EVENT_ID where m.BATCH_SHA='c98dd25724224a9f44c8b261b013631db09504205edae5981450a188590c2ef0' order by m.ORDINAL;
select 'COMMIT|'||BATCH_SHA||'|'||TARGET_ID||'|'||SCHEMA_SHA||'|'||EVENTS_COUNT from {t}_COMMITS where BATCH_SHA='{batch}';
select 'WINDOW|'||STATUS||'|'||ISSUE_SEQ_START||'|'||ISSUE_SEQ_END from {U}.SH_CAP_WINDOW where WINDOW_KEY='{key}';
select 'SOURCE|'||count(*) from ORKLAB.T_D2_BLOCKS_20261008 where NOTE='anchor' and TXT=rpad('block-seed',100,'x');
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
    assert select('COUNTS')==[['42','42','2','42','2']] and select('WINDOW')==[['CONSUMED','3','3']] and select('SOURCE')==[['20']]
    def image(values):
        return ';'.join(k+'='+('NULL' if v is None else 'NUMBER:'+str(v) if isinstance(v,(int,float)) else 'VARCHAR2:'+base64.urlsafe_b64encode(v.encode()).decode().rstrip('=')) for k,v in values.items())
    expected=[[str(i),event['eventId'],meta['payloadSha256'],image(event['before']),image(event['after']),event['operation']] for i,(event,meta) in enumerate(zip(projection['events'],evidence['events']))]
    assert select('EVENT')==expected
    old=json.loads((OLD/'actual-projection.json').read_text())
    assert select('OLD_EVENT')==[[str(i),event['eventId'],meta['payloadSha256'],image(event['before']),image(event['after']),event['operation']] for i,(event,meta) in enumerate(zip(old['envelopes'],old['evidence']['events']))]
    assert select('COMMIT')==[[batch,'oracle-d2-blocks-SHPBL1-20261008',evidence['schemaSequenceSha256'],'2']]
    checkpoints={}
    for task,state in select('TASK'):
        chunks=sorted((int(i),data) for tid,i,data in select('CPHEX') if tid==task)
        assert [i for i,_ in chunks]==list(range(len(chunks))) and state=='DONE'
        checkpoints[task]=bytes.fromhex(''.join(data for _,data in chunks))
    actual=[(tid,raw,json.loads(raw)) for tid,raw in checkpoints.items() if json.loads(raw)['lastWindowKey']==key];assert len(actual)==1
    task,raw,checkpoint=actual[0];assert checkpoint['oracleEvidence']==evidence
    ack=N/'acks'/(hashlib.sha256((SOURCE+'\0'+key).encode()).hexdigest()+'.ack.json');assert ack.read_bytes()==raw
    binding=json.loads(bytes.fromhex(''.join(data for _,data in sorted((int(i),data) for i,data in select('BINDINGHEX')))))
    assert binding['publication']==dict(previousTaskId=101684760056922112,previousBatchId=101684894673108992,publishedEvents=40,prefixSha256=projection['prefixSha256'])
    for old in (OLD/'acks').glob('*.ack.json'):assert (N/'acks'/old.name).read_bytes()==old.read_bytes()
    (N/(label+'-summary.json')).write_text(json.dumps(dict(taskId=task,batchId=checkpoint['lastBatchId'],fullEvents=42,incrementalEvents=2,target=[42,42,2],ledger=42,gates=2,cpAckSha256=sha(ack),rawSha256=archive['sha256'],mismatches=0,independentTargetAccepted=False),indent=2))
elif action=='stop':
    assert run(label+'-owner',['docker','inspect',HELPER,'--format','{{.Config.Labels.owner}}|{{.Config.Labels.scope}}']).strip()=='OracleRedoKernel|oracle-product-runtime'
    if (N/'collector.pid').exists():
        pid=(N/'collector.pid').read_text().strip();assert pid.isdecimal()
        command=run(label+'-client-owner',['docker','exec',HELPER,'cat','/proc/'+pid+'/cmdline'])
        assert command.split('\0')[0]=='/evidence/sh_agent'
        run(label+'-client-signal',['docker','exec',HELPER,'kill','-TERM',pid])
    run(label+'-logs',['docker','logs',HELPER])
    run(label+'-helper',['docker','stop','--timeout','30',HELPER])
    run(label+'-terminal',['docker','inspect',HELPER,'--format','{{.State.Status}}|{{.State.ExitCode}}'])
    run(label+'-remove',['docker','rm',HELPER])
    run(label+'-source',['docker','stop','--timeout','45',S])
else:raise ValueError(action)
