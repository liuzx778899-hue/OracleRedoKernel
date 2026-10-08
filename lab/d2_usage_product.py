from pathlib import Path
import sys,json,zipfile,os,hashlib,shutil,subprocess
R=Path('D:/work/vibecode/OracleRedoKernel');O=R/'lab/out/d2-usage-runtime-20261008';N=O/'product';C=R/'lab/out/d2-usage-candidate-20261008'
A=R/'lab/out/product-active-20261007';S='oraredo-19c-lab-20261004';NAME='ork-d2-usage-product-20261008';U='ORKD2USAGECTL_20261008';PORT=37296
SOURCE='oracle-d2-usage';action=sys.argv[1];label=sys.argv[2] if len(sys.argv)>2 else action
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
assert not (N/('executed-'+label+'.py')).exists();shutil.copyfile(__file__,N/('executed-'+label+'.py'))

def run(name, args, data=None):
    assert not (N / (name + '.command.json')).exists(), name
    args = list(map(str, args))
    (N / (name + '.command.json')).write_text(json.dumps(args))
    p = subprocess.run(args, input=data, capture_output=True, timeout=180)
    passwords = []
    for f in [A / 'admin.secret.json', A / 'control.secret.json', N / 'control.secret.json']:
        if f.exists(): passwords.append(json.loads(f.read_text())['password'].encode())
    for f in [R / 'lab/out/oracle-target-20261005/target.secret', R / 'lab/out/oracle-snapshot-acquisition-20261005/source.secret']:
        passwords.append(f.read_text().splitlines()[1].encode())
    for suffix, value in [('stdout.txt', p.stdout), ('stderr.txt', p.stderr)]:
        for password in passwords: value = value.replace(password, b'<redacted-secret>')
        (N / (name + '.' + suffix)).write_bytes(value)
    (N / (name + '.rc.txt')).write_text(str(p.returncode))
    assert p.returncode == 0, (name, p.returncode)
    return p.stdout.decode()

def sql(name, text, private=False):
    body = 'whenever sqlerror exit failure rollback\nwhenever oserror exit failure rollback\nset pages 0 feedback off heading off lines 32767 long 200000 longchunksize 200000 trimspool on\nalter session set container=ORKPDB;\n' + text + '\nexit success\n'
    (N / (name + ('.secret.sql' if private else '.sql'))).write_text(body)
    out = run(name, ['docker', 'exec', '-i', S, 'sqlplus', '-s', '/', 'as', 'sysdba'], body.encode())
    assert 'SP2-' not in out
    return out

def http(name, path, data=None, success=True):
    remote = """import json,urllib.request,urllib.error
base=BASE
def req(path,data=None,token=None):
 h={'Content-Type':'application/json'}
 if token:h['Authorization']='Bearer '+token
 try:
  with urllib.request.urlopen(urllib.request.Request(base+path,data=None if data is None else json.dumps(data).encode(),headers=h),timeout=120) as r:return [r.status,json.loads(r.read())]
 except urllib.error.HTTPError as e:return [e.code,json.loads(e.read())]
l=req('/auth/login',LOGIN);assert l[1]['code']==0
r=req(PATH,DATA,l[1]['data']['token']);print(json.dumps(r))
""".replace('BASE', repr('http://127.0.0.1:' + str(PORT) + '/api/v1')).replace('LOGIN', repr(json.loads((A / 'admin.secret.json').read_text()))).replace('PATH', repr(path)).replace('DATA', repr(data))
    result = json.loads(run(name, ['docker', 'exec', '-i', S, 'python3', '-'], remote.encode()))
    assert (result[1]['code'] == 0) == success, result
    return result


OLDINPUT=O
CAP=R/'lab/out/d2-usage-source-20261008'
b=json.loads((OLDINPUT/'baseline-reference.json').read_text());ACQ=int(b['acquisition_id'])
T='ORKTGT_CMT_20261005.SHPUS1';TARGET_ID='oracle-d2-usage-SHPUS1-20261008'
if action=='start':
    proof=json.loads((C/'product-proof.json').read_text());assert sha(N/'application.jar')==proof['applicationSha256']
    assert NAME not in run('helper-preflight',['docker','ps','-a','--format','{{.Names}}']).splitlines()
    props=dict(line.split('=',1) for line in (OLDINPUT/'runtime.secret.env').read_text().splitlines())
    props.update(SERVER_PORT=str(PORT),SH_WORKER_ID='52',SH_ORACLE_BOUNDED_TARGET_ID=TARGET_ID,SH_ORACLE_BOUNDED_TARGET_PREFIX='SHPUS1',SH_ORACLE_PREFIX_INPUT_DIRECTORY='/inputs',SH_PIPELINE_AUTO_CONSUME_SOURCE_IDS=SOURCE,SH_PIPELINE_AUTO_CONSUME_ENABLED='true')
    (N/'runtime.secret.env').write_text(''.join(k+'='+v+'\n' for k,v in props.items()))
    run('start',['docker','run','-d','--name',NAME,'--label','owner=OracleRedoKernel','--label','scope=oracle-d2-usage-product','--user','0','--network','container:'+S,'--workdir','/evidence','--env-file',N/'runtime.secret.env','--mount','type=bind,source='+str(N)+',target=/evidence','--mount','type=bind,source='+str(CAP)+',target=/inputs,readonly','--mount','type=bind,source='+str(OLDINPUT/'snapshots')+',target=/evidence/snapshots,readonly','--entrypoint','java','flink:1.18-java17','-Xmx1536m','-jar','/evidence/application.jar'])
elif action=='health':
    http(label,'/system/info')
    assert run(label+'-sha',['docker','exec',NAME,'sha256sum','/evidence/application.jar']).split()[0]==sha(N/'application.jar')
elif action=='projection':
    libs=N/'projection-libs';libs.mkdir();classes=N/'projection-classes';classes.mkdir()
    with zipfile.ZipFile(N/'application.jar') as z:
        for path in z.namelist():
            if path.startswith('BOOT-INF/lib/') and any(Path(path).name.startswith(x) for x in ['oracle-redo-kernel-','streamhouse-engine-oracle-','streamhouse-spi-','jackson-']):(libs/Path(path).name).write_bytes(z.read(path))
    cp=str(classes)+';'+str(libs/'*')
    run('projection-compile',['D:/work/Java/jdk-17.0.7/bin/javac.exe','-cp',cp,'-d',classes,N/'UsageProjectionProbe.java'])
    inputs=[]
    for seq in [84,85]:
        path=CAP/(str(seq)+'.raw');inputs += [str(path),sha(path)]
    out=run('projection',['D:/work/Java/jdk-17.0.7/bin/java.exe','-cp',cp,'UsageProjectionProbe',b['path'],b['sha256'],(O/'index-leaf.txt').read_text(),*inputs])
    result=json.loads(out);assert len(result['envelopes'])==2
    assert [(v['before']['TXT'],v['after']['TXT']) for v in result['envelopes']]==[('statistics-seed','d2-statistics-added'),('d2-statistics-added','statistics-seed')]
    (N/'actual-projection.json').write_text(json.dumps(result,indent=2))
elif action=='prefix':
    http('prefix-health','/system/info')
    out=sql('prefix-metadata',f"select 'ACQ|'||count(*) from {U}.SH_ORACLE_ACQUISITION where ID={ACQ} and STATE='ACQUIRED_ONLY' and SNAPSHOT_SCN={b['scn']} and SNAPSHOT_SHA256='{b['sha256']}';")
    assert 'ACQ|1' in out
    p=CAP/'84.raw';binding=dict(acquisitionId=ACQ,leaf=int((O/'index-leaf.txt').read_text()),captures=[dict(path='/inputs/84.raw',sha256=sha(p),inputFormat='RAW_MEMBER')],checkpoint=None)
    data=json.dumps(binding,separators=(',',':')).replace("'","''")
    sql('prefix-binding',f"insert into {U}.SH_ORACLE_INPUT_BINDING values(9801,'oracle-d2-usage','d2-usage-prefix',1,1,'v3-history-consumer','{json.loads((N/"actual-projection.json").read_text())["evidence"]["schemaSha256"]}','APPROVED_PREFIX',to_clob('{data}'),systimestamp,'owned-d2-prefix');\ninsert into {U}.SH_CAP_WINDOW(ID,SOURCE_ID,ENGINE_TYPE,WINDOW_KEY,ISSUE_SEQ_START,ISSUE_SEQ_END,HEAD_PATH,FROZEN,FILE_SHA256,TOTAL_SIZE,DROPPED_SEGMENTS,STATUS,REGISTERED_AT) values(9802,'oracle-d2-usage','oracle','d2-usage-prefix',1,1,'/inputs/84.raw',1,'84.raw {sha(p)}',{p.stat().st_size},0,'PENDING',systimestamp);\ncommit;")
    # The configured scheduler submits the normal pending task.
elif action=='target':
    e=json.loads((N/'actual-projection.json').read_text())['evidence']
    pre=sql('target-preflight',"select 'TARGET|'||count(*) from dba_tables where owner='ORKTGT_CMT_20261005' and substr(table_name,1,7)='SHPUS1_';")
    assert 'TARGET|0' in pre
    source=':'.join(str(e['source'][k]) for k in ['dbid','activationId','compatibility','thread','resetlogsId','resetlogsScn','containerUid','containerId'])
    sql('target-create',f"create table {T}_IDENT(ID number primary key,TARGET_ID varchar2(128) not null,SOURCE_BINDING varchar2(512) not null,SCHEMA_SHA varchar2(64) not null);\ninsert into {T}_IDENT values(1,'{TARGET_ID}','{source}','{e['schemaSha256']}');\ncreate table {T}_ROWS(ID number primary key,TXT varchar2(128),NOTE varchar2(128),D2STAT_COL varchar2(80));\ninsert into {T}_ROWS values(0,'statistics-seed','anchor',null);\ncreate table {T}_APPLIED(EVENT_ID varchar2(80) primary key,PAYLOAD_SHA varchar2(64) not null,BEFORE_IMAGE varchar2(4000),AFTER_IMAGE varchar2(4000),OPERATION varchar2(16) not null);\ncreate table {T}_MEMBERS(BATCH_SHA varchar2(64),ORDINAL number,EVENT_ID varchar2(80) references {T}_APPLIED(EVENT_ID),primary key(BATCH_SHA,ORDINAL),unique(BATCH_SHA,EVENT_ID));\ncreate table {T}_COMMITS(BATCH_SHA varchar2(64) primary key,TARGET_ID varchar2(128) not null,SCHEMA_SHA varchar2(64) not null,EVENTS_COUNT number not null);\ncommit;")
elif action=='closed':
    assert 'WAITING_INPUT' in (N/'prefix-state.stdout.txt').read_text(), 'inspect authoritative prefix state first'
    p=json.loads((N/'actual-projection.json').read_text());e=p['evidence']
    approved=dict(baselinePath=None,capturePaths=['/inputs/84.raw','/inputs/85.raw'],evidence=e,envelopesSha256=p['envelopesSha256'],acquisitionId=ACQ,recovery=dict(prefixBindingId=9801),inputFormat='RAW_MEMBER')
    data=json.dumps(approved,separators=(',',':'));expr='||\n'.join("to_clob('"+data[i:i+1800].replace("'","''")+"')" for i in range(0,len(data),1800))
    hashes='\n'.join(str(seq)+'.raw '+sha(CAP/(str(seq)+'.raw')) for seq in [84,85])
    total=sum((CAP/(str(seq)+'.raw')).stat().st_size for seq in [84,85])
    sql('closed-binding',f"insert into {U}.SH_ORACLE_INPUT_BINDING values(9901,'oracle-d2-usage','d2-usage-closed',1,2,'v3-history-consumer','{e['schemaSha256']}','APPROVED_BOUNDED',"+expr+",systimestamp,'owned-d2-closure');\n"+f"insert into {U}.SH_CAP_WINDOW(ID,SOURCE_ID,ENGINE_TYPE,WINDOW_KEY,ISSUE_SEQ_START,ISSUE_SEQ_END,HEAD_PATH,FROZEN,FILE_SHA256,TOTAL_SIZE,DROPPED_SEGMENTS,STATUS,REGISTERED_AT) values(9902,'oracle-d2-usage','oracle','d2-usage-closed',1,2,'/inputs/84.raw',1,'{hashes}',{total},0,'PENDING',systimestamp);\ncommit;")
elif action=='repair-hash-names':
    failed=(N/'closed-state.stdout.txt').read_text()
    assert 'TARGET|0|0|0' in failed and 'GATES|0' in failed and 'ORACLE_CAPTURE_HASH_OR_SCOPE_MISMATCH' in failed
    task=next(line.split('|')[1] for line in failed.splitlines() if line.startswith('TASK|') and '|FAILED|' in line)
    hashes='\n'.join(str(seq)+'.raw '+sha(CAP/(str(seq)+'.raw')) for seq in [84,85])
    sql(label,f"declare n number;begin select count(*) into n from {U}.SH_TASK where ID={task} and STATE='FAILED';if n!=1 then raise_application_error(-20001,'task state changed');end if;select count(*) into n from {T}_APPLIED;if n!=0 then raise_application_error(-20002,'target changed');end if;update {U}.SH_CAP_WINDOW set FILE_SHA256='{hashes}' where ID=9902 and STATUS='PENDING';if sql%rowcount!=1 then raise_application_error(-20003,'window changed');end if;end;\n/\ncommit;")
    http('resume-after-hash-name-fix',f'/pipeline/tasks/{task}/resume',{})
elif action=='resume-fixed-task':
    task=next(line.split('|')[1] for line in (N/'closed-state.stdout.txt').read_text().splitlines() if line.startswith('TASK|') and '|FAILED|' in line)
    current=http('fixed-task-authority',f'/pipeline/tasks/{task}')[1]['data'];assert current['state']=='FAILED'
    http('resume-fixed-task',f'/pipeline/tasks/{task}/resume',{})
elif action=='state':
    sql(label,f"select 'TARGET|'||(select count(*) from {T}_APPLIED)||'|'||(select count(*) from {T}_MEMBERS)||'|'||(select count(*) from {T}_COMMITS) from dual;\nselect 'ROW|'||ID||'|'||TXT||'|'||NOTE from {T}_ROWS;\nselect 'EVENT|'||m.ORDINAL||'|'||m.EVENT_ID||'|'||a.PAYLOAD_SHA||'|'||a.BEFORE_IMAGE||'|'||a.AFTER_IMAGE||'|'||a.OPERATION from {T}_MEMBERS m join {T}_APPLIED a on a.EVENT_ID=m.EVENT_ID order by m.ORDINAL;\nselect 'TASK|'||to_char(ID,'FM99999999999999999999')||'|'||STATE||'|'||ATTEMPTS from {U}.SH_TASK;\nselect 'CP|'||CHECKPOINT_JSON from {U}.SH_TASK;\nselect 'BATCH|'||to_char(ID,'FM99999999999999999999')||'|'||STATE||'|'||RC||'|'||EVENT_COUNT from {U}.SH_PIPELINE_BATCH;\nselect 'WINDOW|'||WINDOW_KEY||'|'||STATUS from {U}.SH_CAP_WINDOW order by WINDOW_KEY;\nselect 'LEDGER|'||EVENT_ID from {U}.SH_EVENT_LEDGER order by EVENT_ID;\nselect 'GATES|'||count(*) from {U}.SH_PUBLISH_GATE;\nselect 'FAIL|'||STAGE||'|'||REASON||'|'||DETAIL from {U}.SH_FAILCLOSED_RECORD;")
elif action=='source-final':
    original=json.loads((O/'historical-acquire.stdout').read_text())['data']
    metadata=original['metadataJson'];expr='||\n'.join("to_clob('"+metadata[i:i+1600].replace("'","''")+"')" for i in range(0,len(metadata),1600))
    out=sql(label,"select 'LOGGING|'||supplemental_log_data_min||'|'||supplemental_log_data_all from v$database;\nselect 'SOURCE|'||ID||'|'||TXT||'|'||NOTE from ORKLAB.T_D2_USAGE_20261008;\nselect 'OLD|'||ID||'|'||TXT||'|'||NOTE from ORKLAB.T_BASELINE_20261004;\nselect 'TRIGGER|'||count(*) from dba_triggers where owner='SYS' and trigger_name='ORK_D2_USAGE_DELAY_20261008';\n"+f"select 'TARGET_ROW|'||ID||'|'||TXT||'|'||NOTE||'|'||nvl(D2STAT_COL,'NULL') from {T}_ROWS;\nselect 'META_EQUAL|'||count(*) from {U}.SH_ORACLE_ACQUISITION where ID={ACQ} and SOURCE_ID='{original['sourceId']}' and AUTHORITY='{original['authority']}' and SNAPSHOT_SHA256='{original['snapshotSha256']}' and SNAPSHOT_SCN={original['snapshotScn']} and STATE='{original['state']}' and REGISTERED_BY='{original['registeredBy']}' and REGISTERED_AT=cast(to_timestamp('{original['registeredAt']}','YYYY-MM-DD\"T\"HH24:MI:SS.FF9') as timestamp(6)) and dbms_lob.compare(METADATA_JSON,"+expr+")=0;")
    assert all(v in out for v in ['LOGGING|NO|NO','SOURCE|0|statistics-seed|anchor','OLD|11|blocking-closed|new-note','TRIGGER|0','TARGET_ROW|0|statistics-seed|anchor|NULL','META_EQUAL|1'])
elif action=='stop':
    assert run('stop-owner',['docker','inspect',NAME,'--format','{{.Config.Labels.owner}}|{{.Config.Labels.scope}}']).strip()=='OracleRedoKernel|oracle-d2-usage-product'
    run('stop',['docker','stop','--time','30',NAME]);run('stop-terminal',['docker','inspect',NAME,'--format','{{.State.Running}}|{{.State.ExitCode}}'])
else:raise ValueError(action)
