"""New owned control/target failure experiment; original registration and inputs reused read-only."""
import datetime, hashlib, json, secrets, shutil, subprocess, sys
from pathlib import Path
R = Path(__file__).resolve().parent.parent
N = R / 'lab/out/split-history-late-http-20261007'
C = R / 'lab/out/split-history-product-20261007'
B = R / 'lab/out/v3-product-runtime-20261006'
S = 'oraredo-19c-lab-20261004'
NAME = 'ork-split-late-http-20261007'
OLD_NAME = NAME
REPAIRED_NAME = 'ork-split-late-http-r2-20261007'
U = 'ORKSPLCTL_20261007'
OLD_U = 'ORKV3CTL_20261006'
T = 'ORKTGT_CMT_20261005.SHPL7'
TARGET_ID = 'oracle-history-SHPL7-20261007'
PORT = 37263
SOURCE = 'oracle-v3-HIST'
WINDOW = 'history-70-71-late'
sha = lambda p: hashlib.sha256(p.read_bytes()).hexdigest()
action = sys.argv[1]
label = sys.argv[2] if len(sys.argv) > 2 else action
if action == 'bootstrap':
    N.mkdir(exist_ok=False)
if (N / 'repaired-helper.txt').exists():
    assert (N / 'repaired-helper.txt').read_text() == REPAIRED_NAME
    NAME = REPAIRED_NAME
assert not (N / ('executed-' + label + '.py')).exists()
shutil.copyfile(__file__, N / ('executed-' + label + '.py'))

def run(name, args, data=None):
    args = list(map(str, args))
    file = N / (name + '.command.json')
    assert not file.exists(), name
    command = dict(args=args, start_utc=datetime.datetime.now(datetime.timezone.utc).isoformat())
    file.write_text(json.dumps(command))
    p = subprocess.run(args, input=data, capture_output=True, timeout=180)
    passwords = [json.loads((B / f).read_text())['password'].encode() for f in ['admin.secret.json', 'control.secret.json']]
    if (N / 'control.secret.json').exists():
        passwords.append(json.loads((N / 'control.secret.json').read_text())['password'].encode())
    for f in [R / 'lab/out/oracle-target-20261005/target.secret', R / 'lab/out/oracle-snapshot-acquisition-20261005/source.secret']:
        passwords.append(f.read_text().splitlines()[1].encode())
    for suffix, value in [('stdout.txt', p.stdout), ('stderr.txt', p.stderr)]:
        for password in passwords:
            value = value.replace(password, b'<redacted-secret>')
        (N / (name + '.' + suffix)).write_bytes(value)
    (N / (name + '.rc.txt')).write_text(str(p.returncode))
    (N / (name + '.end.json')).write_text(json.dumps(dict(rc=p.returncode, end_utc=datetime.datetime.now(datetime.timezone.utc).isoformat())))
    assert p.returncode == 0, (name, p.returncode)
    return p.stdout.decode('utf-8')

def sql(name, text, private=False):
    body = ('whenever sqlerror exit failure rollback\nwhenever oserror exit failure rollback\n'
        'set pages 0 feedback off heading off lines 32767 long 200000 longchunksize 200000 trimspool on\n'
        'alter session set container=ORKPDB;\n' + text + '\nexit success\n')
    (N / (name + ('.secret.sql' if private else '.sql'))).write_text(body, encoding='utf-8')
    out = run(name, ['docker', 'exec', '-i', S, 'sqlplus', '-s', '/', 'as', 'sysdba'], body.encode())
    assert 'SP2-' not in out
    return out

def http(name, path, data=None):
    remote = """import json,urllib.request,urllib.error
base=BASE
def req(path,data=None,token=None):
 h={'Content-Type':'application/json'}
 if token:h['Authorization']='Bearer '+token
 try:
  with urllib.request.urlopen(urllib.request.Request(base+path,data=None if data is None else json.dumps(data).encode(),headers=h),timeout=120) as r:return [r.status,json.loads(r.read())]
 except urllib.error.HTTPError as e:return [e.code,json.loads(e.read())]
l=req('/auth/login',LOGIN);assert l[1]['code']==0
r=req(PATH,DATA,l[1]['data']['token']);print(json.dumps(r));assert r[1]['code']==0
""".replace('BASE', repr('http://127.0.0.1:' + str(PORT) + '/api/v1')).replace('LOGIN', repr(json.loads((B / 'admin.secret.json').read_text()))).replace('PATH', repr(path)).replace('DATA', repr(data))
    return json.loads(run(name, ['docker', 'exec', '-i', S, 'python3', '-'], remote.encode()))

if action == 'bootstrap':
    assert sha(C / 'application.jar') == 'ef852e719862ee41c552fb6de9370af66f9229b56faa92bb7b2dfce02fc5ceb7'
    assert run('source-owner', ['docker', 'inspect', S, '--format', '{{.Config.Labels.owner}}|{{.State.Running}}']).strip() == 'OracleRedoKernel|true'
    assert NAME not in run('helper-preflight', ['docker', 'ps', '-a', '--format', '{{.Names}}']).splitlines()
    for name in ['ork-split-history-product-20261007', 'ork-oracle-v3-product-20261006']:
        assert run('stopped-' + name, ['docker', 'inspect', name, '--format', '{{.Config.Labels.owner}}|{{.State.Running}}']).strip() == 'OracleRedoKernel|false'
    pre = sql('preflight', f"select 'CONTROL|'||count(*) from dba_users where username='{U}';\nselect 'TARGET|'||count(*) from dba_tables where owner='ORKTGT_CMT_20261005' and table_name like 'SHPL7\\_%' escape '\\';\nselect 'LOGGING|'||supplemental_log_data_min||'|'||supplemental_log_data_all from v$database;\nselect 'OLD|'||ID||'|'||TXT||'|'||NOTE from ORKLAB.T_BASELINE_20261004;")
    assert all(v in pre for v in ['CONTROL|0', 'TARGET|0', 'LOGGING|NO|NO', 'OLD|11|blocking-closed|new-note'])
    pwd = 'P' + secrets.token_hex(12) + 'a1'
    (N / 'control.secret.json').write_text(json.dumps(dict(user=U, password=pwd)))
    sql('create-control', f'create user {U} identified by "{pwd}" default tablespace USERS quota unlimited on USERS;\ngrant create session,create table,create sequence to {U};', True)
    props = dict(line.split('=', 1) for line in (B / 'runtime.secret.env').read_text().splitlines())
    props.update(SPRING_DATASOURCE_USERNAME=U, SPRING_DATASOURCE_PASSWORD=pwd, SERVER_PORT=str(PORT),
        SH_WORKER_ID='22', SH_ORACLE_BOUNDED_TARGET_PREFIX='SHPL7', SH_ORACLE_BOUNDED_TARGET_ID=TARGET_ID)
    assert props['SH_PIPELINE_AUTO_CONSUME_ENABLED'] == 'false'
    (N / 'runtime.secret.env').write_text(''.join(k + '=' + v + '\n' for k, v in props.items()))
    run('start', ['docker', 'run', '-d', '--name', NAME, '--label', 'owner=OracleRedoKernel', '--label',
        'scope=split-history-late-http', '--user', '0', '--network', 'container:' + S, '--workdir', '/evidence',
        '--env-file', N / 'runtime.secret.env', '--mount', 'type=bind,source=' + str(N) + ',target=/evidence',
        '--mount', 'type=bind,source=' + str(C) + ',target=/candidate,readonly', '--mount',
        'type=bind,source=' + str(B / 'snapshots') + ',target=/evidence/snapshots,readonly',
        '--entrypoint', 'java', 'flink:1.18-java17', '-Xmx1536m', '-jar', '/candidate/application.jar'])
elif action == 'health':
    http(label, '/system/info')
    path = '/repaired/application.jar' if NAME == REPAIRED_NAME else '/candidate/application.jar'
    expected = N / 'repaired-build-r2/application.jar' if NAME == REPAIRED_NAME else C / 'application.jar'
    assert run(label + '-sha', ['docker', 'exec', NAME, 'sha256sum', path]).split()[0] == sha(expected)
elif action == 'start-repaired':
    assert run(label + '-old-owner', ['docker', 'inspect', OLD_NAME, '--format', '{{.Config.Labels.owner}}|{{.Config.Labels.scope}}|{{.State.Running}}']).strip() == 'OracleRedoKernel|split-history-late-http|false'
    assert REPAIRED_NAME not in run(label + '-inventory', ['docker', 'ps', '-a', '--format', '{{.Names}}']).splitlines()
    A = N / 'repaired-build-r2'
    proof = json.loads((A / 'proof.json').read_text())
    assert sha(A / 'application.jar') == proof['product_sha256'] and proof['tests'] == [752, 0, 0, 0]
    props = dict(line.split('=', 1) for line in (N / 'runtime.secret.env').read_text().splitlines())
    props['SH_PIPELINE_TARGET_APPLY_FAIL_DIR'] = '/evidence/failures'
    (N / 'repaired.secret.env').write_text(''.join(k + '=' + v + '\n' for k, v in props.items()))
    run(label, ['docker', 'run', '-d', '--name', REPAIRED_NAME, '--label', 'owner=OracleRedoKernel', '--label',
        'scope=split-history-late-http', '--user', '0', '--network', 'container:' + S, '--workdir', '/evidence',
        '--env-file', N / 'repaired.secret.env', '--mount', 'type=bind,source=' + str(N) + ',target=/evidence',
        '--mount', 'type=bind,source=' + str(C) + ',target=/candidate,readonly', '--mount',
        'type=bind,source=' + str(A) + ',target=/repaired,readonly', '--mount',
        'type=bind,source=' + str(B / 'snapshots') + ',target=/evidence/snapshots,readonly',
        '--entrypoint', 'java', 'flink:1.18-java17', '-Xmx1536m', '-jar', '/repaired/application.jar'])
    (N / 'repaired-helper.txt').write_text(REPAIRED_NAME)
elif action == 'enroll':
    e = json.loads((C / 'approved-binding.json').read_text())['evidence']
    registration = sql('copy-original-registration', f"begin\ninsert into {U}.SH_ORACLE_ACQUISITION select * from {OLD_U}.SH_ORACLE_ACQUISITION where ID=100747510573780992 and SNAPSHOT_SCN=2566695 and SNAPSHOT_SHA256='{e['baselineSha256']}' and STATE='ACQUIRED_ONLY';\nif SQL%ROWCOUNT!=1 then raise_application_error(-20001,'ORIGINAL_REGISTRATION_REQUIRED'); end if;\nend;\n/\ncommit;\nselect 'META_EQUAL|'||count(*) from {U}.SH_ORACLE_ACQUISITION n join {OLD_U}.SH_ORACLE_ACQUISITION o on n.ID=o.ID where n.SOURCE_ID=o.SOURCE_ID and n.AUTHORITY=o.AUTHORITY and n.SNAPSHOT_SHA256=o.SNAPSHOT_SHA256 and n.SNAPSHOT_SCN=o.SNAPSHOT_SCN and n.STATE=o.STATE and n.REGISTERED_AT=o.REGISTERED_AT and n.REGISTERED_BY=o.REGISTERED_BY and dbms_lob.compare(n.METADATA_JSON,o.METADATA_JSON)=0;")
    assert 'META_EQUAL|1' in registration
    http('original-acquisition-get', '/oracle/acquisitions/100747510573780992')
    binding = ':'.join(str(e['source'][k]) for k in ['dbid', 'activationId', 'compatibility', 'thread', 'resetlogsId', 'resetlogsScn', 'containerUid', 'containerId'])
    ddl = f"create table {T}_IDENT(ID number primary key,TARGET_ID varchar2(128) not null,SOURCE_BINDING varchar2(512) not null,SCHEMA_SHA varchar2(64) not null);\ninsert into {T}_IDENT values(1,'{TARGET_ID}','{binding}','{e['schemaSha256']}');\n"
    ddl += f"create table {T}_ROWS(ID number primary key,TXT varchar2(128) constraint SHPL7_LATE_CK check(TXT!='history-product-restored'),NOTE varchar2(128),EXTRA_HP varchar2(128));\ninsert into {T}_ROWS values(0,'history-v3-restored','anchor',NULL);\n"
    ddl += f"create table {T}_APPLIED(EVENT_ID varchar2(80) primary key,PAYLOAD_SHA varchar2(64) not null,BEFORE_IMAGE varchar2(4000),AFTER_IMAGE varchar2(4000),OPERATION varchar2(16) not null);\n"
    ddl += f"create table {T}_MEMBERS(BATCH_SHA varchar2(64),ORDINAL number,EVENT_ID varchar2(80) references {T}_APPLIED(EVENT_ID),primary key(BATCH_SHA,ORDINAL),unique(BATCH_SHA,EVENT_ID));\n"
    ddl += f"create table {T}_COMMITS(BATCH_SHA varchar2(64) primary key,TARGET_ID varchar2(128) not null,SCHEMA_SHA varchar2(64) not null,EVENTS_COUNT number not null);\ncommit;"
    sql('target-setup', ddl)
    approved = json.loads((C / 'approved-binding.json').read_text())
    approved['capturePaths'] = ['/candidate/' + Path(path).name for path in approved['capturePaths']]
    (N / 'approved-binding.json').write_text(json.dumps(approved, indent=2))
    text = json.dumps(approved, separators=(',', ':'))
    expr = '||\n'.join("to_clob('" + text[i:i+1800].replace("'", "''") + "')" for i in range(0, len(text), 1800))
    shas = '\n'.join(Path(path).name + ' ' + c['journalSha256'] for path, c in zip(approved['capturePaths'], e['captures']))
    sql('enroll-binding-poisoned', f"insert into {U}.SH_ORACLE_INPUT_BINDING values(8801,'{SOURCE}','{WINDOW}',1,1,'wrong-consumer','{e['schemaSha256']}','APPROVED_BOUNDED'," + expr + ",systimestamp,'owned-original-registration-fault');\n"
        f"insert into {U}.SH_CAP_WINDOW(ID,SOURCE_ID,ENGINE_TYPE,WINDOW_KEY,ISSUE_SEQ_START,ISSUE_SEQ_END,HEAD_PATH,FROZEN,FILE_SHA256,TOTAL_SIZE,DROPPED_SEGMENTS,STATUS,REGISTERED_AT) values(8802,'{SOURCE}','oracle','{WINDOW}',1,1,'{approved['capturePaths'][0]}',1,'{shas}',{sum(c['journalBytes'] for c in e['captures'])},0,'PENDING',systimestamp);\ncommit;")
elif action == 'submit':
    result = http(label, '/pipeline/tasks', dict(sourceId=SOURCE))
    (N / 'task-id.txt').write_text(str(result[1]['data']['id']))
elif action == 'resume':
    http(label, '/pipeline/tasks/' + (N / 'task-id.txt').read_text() + '/resume', {})
elif action == 'restore-binding':
    sql(label, f"begin\nupdate {U}.SH_ORACLE_INPUT_BINDING set CONSUMER_ID='v3-history-consumer' where ID=8801 and CONSUMER_ID='wrong-consumer';\nif SQL%ROWCOUNT!=1 then raise_application_error(-20001,'BINDING_RESTORE_PRECONDITION'); end if;\nend;\n/\ncommit;")
elif action == 'clear-late':
    sql(label, f"alter table {T}_ROWS drop constraint SHPL7_LATE_CK;")
elif action == 'block-ack':
    assert not (N / 'acks').exists()
    (N / 'acks').write_bytes(b'owned ACK directory blocker fixture\n')
elif action == 'unblock-ack':
    assert (N / 'acks').is_file() and (N / 'acks').read_bytes() == b'owned ACK directory blocker fixture\n'
    assert not (N / 'ack-directory-blocker.fixture').exists()
    (N / 'acks').rename(N / 'ack-directory-blocker.fixture')
elif action == 'restart-repaired':
    assert NAME == REPAIRED_NAME
    assert run(label + '-owner', ['docker', 'inspect', NAME, '--format', '{{.Config.Labels.owner}}|{{.Config.Labels.scope}}|{{.State.Running}}']).strip() == 'OracleRedoKernel|split-history-late-http|true'
    run(label + '-stop', ['docker', 'stop', '--time', '30', NAME])
    run(label + '-start', ['docker', 'start', NAME])
elif action == 'final-source':
    sql(label, "select 'LOGGING|'||supplemental_log_data_min||'|'||supplemental_log_data_all from v$database;\nselect 'OLD|'||ID||'|'||TXT||'|'||NOTE from ORKLAB.T_BASELINE_20261004;\nselect 'BASE|'||ID||'|'||TXT||'|'||NOTE from ORKLAB.T_V2_HIST_20261006;\n"
        f"select 'META_EQUAL|'||count(*) from {U}.SH_ORACLE_ACQUISITION n join {OLD_U}.SH_ORACLE_ACQUISITION o on n.ID=o.ID where n.SOURCE_ID=o.SOURCE_ID and n.AUTHORITY=o.AUTHORITY and n.SNAPSHOT_SHA256=o.SNAPSHOT_SHA256 and n.SNAPSHOT_SCN=o.SNAPSHOT_SCN and n.STATE=o.STATE and n.REGISTERED_AT=o.REGISTERED_AT and n.REGISTERED_BY=o.REGISTERED_BY and dbms_lob.compare(n.METADATA_JSON,o.METADATA_JSON)=0;")
elif action == 'state':
    sql(label, f"select 'TARGET|'||(select count(*) from {T}_APPLIED)||'|'||(select count(*) from {T}_MEMBERS)||'|'||(select count(*) from {T}_COMMITS) from dual;\nselect 'ROW|'||ID||'|'||TXT||'|'||NOTE||'|'||nvl(EXTRA_HP,'<NULL>') from {T}_ROWS;\n"
        f"select 'EVENT|'||m.ORDINAL||'|'||m.EVENT_ID||'|'||a.PAYLOAD_SHA||'|'||a.BEFORE_IMAGE||'|'||a.AFTER_IMAGE||'|'||a.OPERATION from {T}_MEMBERS m join {T}_APPLIED a on a.EVENT_ID=m.EVENT_ID order by m.ORDINAL;\nselect 'COMMIT|'||BATCH_SHA||'|'||TARGET_ID||'|'||SCHEMA_SHA||'|'||EVENTS_COUNT from {T}_COMMITS;\n"
        f"select 'BATCH|'||to_char(ID,'FM99999999999999999999')||'|'||STATE||'|'||RC||'|'||EVENT_COUNT||'|'||RC_REASON from {U}.SH_PIPELINE_BATCH order by ID;\nselect 'TASK|'||to_char(ID,'FM99999999999999999999')||'|'||STATE from {U}.SH_TASK;\nselect 'CP|'||CHECKPOINT_JSON from {U}.SH_TASK;\nselect 'WINDOW|'||STATUS from {U}.SH_CAP_WINDOW;\nselect 'LEDGER|'||EVENT_ID from {U}.SH_EVENT_LEDGER order by EVENT_ID;\nselect 'GATES|'||count(*) from {U}.SH_PUBLISH_GATE;\nselect 'FAIL|'||STAGE||'|'||REASON||'|'||DETAIL from {U}.SH_FAILCLOSED_RECORD;\nselect 'AUDIT|'||ACTION||'|'||DETAIL from {U}.SH_AUDIT_LOG where ACTION in ('pipeline.task.done','pipeline.task.failed') order by ID;")
elif action == 'forensic':
    sql(label, f"select 'FORENSIC|'||to_char(ID,'FM99999999999999999999')||'|'||nvl(to_char(APPLY_RC),'NULL')||'|'||nvl(APPLY_REASON_FILE,'NULL')||'|'||nvl(APPLY_REASON,'NULL') from {U}.SH_PIPELINE_BATCH order by ID;")
elif action == 'logs':
    run(label, ['docker', 'logs', NAME])
elif action == 'stop':
    assert run(label + '-owner', ['docker', 'inspect', NAME, '--format', '{{.Config.Labels.owner}}|{{.Config.Labels.scope}}|{{.State.Running}}']).strip() == 'OracleRedoKernel|split-history-late-http|true'
    run(label, ['docker', 'stop', '--time', '30', NAME])
    run(label + '-state', ['docker', 'inspect', NAME, '--format', '{{.State.Running}}|{{.State.ExitCode}}|{{.State.Status}}'])
else:
    raise ValueError(action)
