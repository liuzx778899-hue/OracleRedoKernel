"""Owned historical HTTP integration; frozen baseline/captures, no source DML."""
import hashlib, json, shutil, subprocess, sys
from pathlib import Path

R = Path(__file__).resolve().parent.parent
N = R / 'lab/out/split-history-product-20261007'
B = R / 'lab/out/v3-product-runtime-20261006'
H = R / 'lab/out/v3-product-history-20261006'
S = 'oraredo-19c-lab-20261004'
OLD = 'ork-oracle-v3-product-20261006'
NAME = 'ork-split-history-product-20261007'
U = 'ORKV3CTL_20261006'
SOURCE = 'oracle-v3-HIST'
WINDOW = 'history-70-71'
T = 'ORKTGT_CMT_20261005.SHP7'
PORT = 37262
TARGET_ID = 'oracle-history-SHP7-20261007'
sha = lambda p: hashlib.sha256(p.read_bytes()).hexdigest()
action = sys.argv[1]
label = sys.argv[2] if len(sys.argv) > 2 else action
assert not (N / ('executed-runtime-' + label + '.py')).exists()
shutil.copyfile(__file__, N / ('executed-runtime-' + label + '.py'))

def run(name, args, data=None):
    assert not (N / (name + '.command.json')).exists(), name
    args = list(map(str, args))
    (N / (name + '.command.json')).write_text(json.dumps(args))
    p = subprocess.run(args, input=data, capture_output=True, timeout=180)
    secrets = [json.loads((B / f).read_text())['password'].encode()
        for f in ['control.secret.json', 'admin.secret.json']]
    for f in [R / 'lab/out/oracle-snapshot-acquisition-20261005/source.secret',
              R / 'lab/out/oracle-target-20261005/target.secret']:
        secrets.append(f.read_text().splitlines()[1].encode())
    for suffix, value in [('stdout.txt', p.stdout), ('stderr.txt', p.stderr)]:
        for password in secrets:
            value = value.replace(password, b'<redacted-secret>')
        (N / (name + '.' + suffix)).write_bytes(value)
    (N / (name + '.rc.txt')).write_text(str(p.returncode))
    assert p.returncode == 0, (name, p.returncode)
    return p.stdout.decode()

def sql(name, text):
    body = ('whenever sqlerror exit failure rollback\nwhenever oserror exit failure rollback\n'
        'set pages 0 feedback off heading off lines 32767 long 200000 longchunksize 200000 trimspool on\n'
        'alter session set container=ORKPDB;\n' + text + '\nexit success\n')
    (N / (name + '.sql')).write_text(body)
    output = run(name, ['docker', 'exec', '-i', S, 'sqlplus', '-s', '/', 'as', 'sysdba'], body.encode())
    assert 'SP2-' not in output
    return output

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

if action == 'audit':
    assert run(label + '-source', ['docker', 'inspect', S, '--format', '{{.Config.Labels.owner}}|{{.State.Status}}']).strip() == 'OracleRedoKernel|running'
    assert run(label + '-old-owner', ['docker', 'inspect', OLD, '--format', '{{.Config.Labels.owner}}|{{.Config.Labels.scope}}|{{.State.Running}}']).strip() == 'OracleRedoKernel|oracle-v3-product|true'
    expected = json.loads((B / 'build-proof.json').read_text())['product_sha256']
    assert run(label + '-old-sha', ['docker', 'exec', OLD, 'sha256sum', '/evidence/application.jar']).split()[0] == expected
    out = sql(label + '-state', "select 'BINDINGS|'||count(*) from " + U + ".SH_ORACLE_INPUT_BINDING;\n"
        "select 'EVENTS|'||count(*) from " + U + ".SH_EVENT_LEDGER;\n"
        "select 'ACQ|'||to_char(ID,'FM99999999999999999999')||'|'||STATE||'|'||SNAPSHOT_SHA256||'|'||SNAPSHOT_SCN from " + U + ".SH_ORACLE_ACQUISITION;\n"
        "select 'TARGET_TABLES|'||count(*) from dba_tables where owner='ORKTGT_CMT_20261005' and table_name like 'SHP7\\_%' escape '\\';\n"
        "select 'LOGGING|'||supplemental_log_data_min||'|'||supplemental_log_data_all from v$database;\n"
        "select 'OLD|'||ID||'|'||TXT||'|'||NOTE from ORKLAB.T_BASELINE_20261004;\n"
        "select 'BASE|'||ID||'|'||TXT||'|'||NOTE from ORKLAB.T_V2_HIST_20261006;")
    assert all(v in out for v in ['BINDINGS|0', 'EVENTS|0', 'TARGET_TABLES|0', 'LOGGING|NO|NO',
        'OLD|11|blocking-closed|new-note', 'BASE|0|history-product-restored|anchor',
        'ACQ|100747510573780992|ACQUIRED_ONLY|323fd35a8ceee43545c28a9fb32f55cedd41409a3e1b5fb63e08606380cd24a2|2566695'])
    print('OWNED_PREFLIGHT_VERIFIED')
elif action == 'start':
    assert (N / 'audit-state.rc.txt').read_text() == '0'
    proof = json.loads((N / 'product-proof.json').read_text())
    assert sha(N / 'application.jar') == proof['product_sha256']
    assert run(label + '-old-owner', ['docker', 'inspect', OLD, '--format', '{{.Config.Labels.owner}}|{{.Config.Labels.scope}}|{{.State.Running}}']).strip() == 'OracleRedoKernel|oracle-v3-product|true'
    assert NAME not in run(label + '-inventory', ['docker', 'ps', '-a', '--format', '{{.Names}}']).splitlines()
    run(label + '-stop-old', ['docker', 'stop', '--time', '30', OLD])
    props = dict(line.split('=', 1) for line in (B / 'runtime.secret.env').read_text().splitlines())
    assert props['SH_PIPELINE_AUTO_CONSUME_ENABLED'] == 'false'
    props.update(SERVER_PORT=str(PORT), SH_ORACLE_BOUNDED_TARGET_PREFIX='SHP7',
        SH_ORACLE_BOUNDED_TARGET_ID=TARGET_ID)
    (N / 'runtime.secret.env').write_text(''.join(k + '=' + v + '\n' for k, v in props.items()))
    (N / 'runtime-pin.json').write_text(json.dumps(dict(product_sha256=proof['product_sha256'],
        container=NAME, old_container=OLD, control=U, port=PORT, target=TARGET_ID,
        acquisition_id=100747510573780992, snapshots_readonly=True, source_actions=0), indent=2))
    run(label + '-container', ['docker', 'run', '-d', '--name', NAME, '--label', 'owner=OracleRedoKernel',
        '--label', 'scope=split-history-product', '--user', '0', '--network', 'container:' + S,
        '--workdir', '/evidence', '--env-file', N / 'runtime.secret.env', '--mount',
        'type=bind,source=' + str(N) + ',target=/evidence', '--mount',
        'type=bind,source=' + str(B / 'snapshots') + ',target=/evidence/snapshots,readonly',
        '--entrypoint', 'java', 'flink:1.18-java17', '-Xmx1536m', '-jar', '/evidence/application.jar'])
elif action == 'health':
    http(label, '/system/info')
    http(label + '-acquisition', '/oracle/acquisitions/100747510573780992')
    assert run(label + '-running-sha', ['docker', 'exec', NAME, 'sha256sum', '/evidence/application.jar']).split()[0] == sha(N / 'application.jar')
elif action == 'prepare':
    p = json.loads((R / 'lab/out/split-history-candidate-20261007/projection-r2/actual-projection.json').read_text())
    e = p['evidence']
    assert e['baselineSha256'] == sha(B / 'snapshots/100747510573780992.snapshot')
    assert e['baselineScn'] == 2566695
    assert 'COUNT|0' in sql(label + '-target-preflight', "select 'COUNT|'||count(*) from dba_tables where owner='ORKTGT_CMT_20261005' and table_name like 'SHP7\\_%' escape '\\';")
    binding = ':'.join(str(e['source'][k]) for k in ['dbid', 'activationId', 'compatibility', 'thread', 'resetlogsId', 'resetlogsScn', 'containerUid', 'containerId'])
    ddl = f"create table {T}_IDENT(ID number primary key,TARGET_ID varchar2(128) not null,SOURCE_BINDING varchar2(512) not null,SCHEMA_SHA varchar2(64) not null);\n"
    ddl += f"insert into {T}_IDENT values(1,'{TARGET_ID}','{binding}','{e['schemaSha256']}');\n"
    ddl += f"create table {T}_ROWS(ID number primary key,TXT varchar2(128),NOTE varchar2(128),EXTRA_HP varchar2(128));\ninsert into {T}_ROWS values(0,'history-v3-restored','anchor',NULL);\n"
    ddl += f"create table {T}_APPLIED(EVENT_ID varchar2(80) primary key,PAYLOAD_SHA varchar2(64) not null,BEFORE_IMAGE varchar2(4000),AFTER_IMAGE varchar2(4000),OPERATION varchar2(16) not null);\n"
    ddl += f"create table {T}_MEMBERS(BATCH_SHA varchar2(64),ORDINAL number,EVENT_ID varchar2(80) references {T}_APPLIED(EVENT_ID),primary key(BATCH_SHA,ORDINAL),unique(BATCH_SHA,EVENT_ID));\n"
    ddl += f"create table {T}_COMMITS(BATCH_SHA varchar2(64) primary key,TARGET_ID varchar2(128) not null,SCHEMA_SHA varchar2(64) not null,EVENTS_COUNT number not null);\ncommit;"
    sql(label + '-target-once', ddl)
    captures = []
    for c in e['captures']:
        file = 'sequence-' + str(c['sequence']) + '.oracap'
        assert sha(H / 'capture' / file) == c['journalSha256']
        shutil.copyfile(H / 'capture' / file, N / file)
        captures.append('/evidence/' + file)
    approved = dict(baselinePath=None, capturePaths=captures, evidence=e,
        envelopesSha256=p['envelopesSha256'], acquisitionId=100747510573780992)
    (N / 'approved-binding.json').write_text(json.dumps(approved, indent=2))
    text = json.dumps(approved, separators=(',', ':'))
    expr = '||\n'.join("to_clob('" + text[i:i+1800].replace("'", "''") + "')" for i in range(0, len(text), 1800))
    shas = '\n'.join(Path(path).name + ' ' + c['journalSha256'] for path, c in zip(captures, e['captures']))
    sql(label + '-enroll', f"insert into {U}.SH_ORACLE_INPUT_BINDING values(8701,'{SOURCE}','{WINDOW}',1,1,'v3-history-consumer','{e['schemaSha256']}','APPROVED_BOUNDED'," + expr + ",systimestamp,'owned-frozen-historical-window');\n"
        f"insert into {U}.SH_CAP_WINDOW(ID,SOURCE_ID,ENGINE_TYPE,WINDOW_KEY,ISSUE_SEQ_START,ISSUE_SEQ_END,HEAD_PATH,FROZEN,FILE_SHA256,TOTAL_SIZE,DROPPED_SEGMENTS,STATUS,REGISTERED_AT) values(8702,'{SOURCE}','oracle','{WINDOW}',1,1,'{captures[0]}',1,'{shas}',{sum(c['journalBytes'] for c in e['captures'])},0,'PENDING',systimestamp);\ncommit;")
elif action == 'submit':
    result = http(label, '/pipeline/tasks', dict(sourceId=SOURCE))
    (N / 'task-id.txt').write_text(str(result[1]['data']['id']))
elif action == 'resume':
    http(label, '/pipeline/tasks/' + (N / 'task-id.txt').read_text() + '/resume', {})
elif action == 'state':
    sql(label, f"select 'TARGET|'||(select count(*) from {T}_APPLIED)||'|'||(select count(*) from {T}_MEMBERS)||'|'||(select count(*) from {T}_COMMITS) from dual;\n"
        f"select 'ROW|'||ID||'|'||TXT||'|'||NOTE||'|'||nvl(EXTRA_HP,'<NULL>') from {T}_ROWS;\n"
        f"select 'EVENT|'||m.ORDINAL||'|'||m.EVENT_ID||'|'||a.PAYLOAD_SHA||'|'||a.BEFORE_IMAGE||'|'||a.AFTER_IMAGE||'|'||a.OPERATION from {T}_MEMBERS m join {T}_APPLIED a on a.EVENT_ID=m.EVENT_ID order by m.ORDINAL;\n"
        f"select 'COMMIT|'||BATCH_SHA||'|'||TARGET_ID||'|'||SCHEMA_SHA||'|'||EVENTS_COUNT from {T}_COMMITS;\n"
        f"select 'BATCH|'||ID||'|'||STATE||'|'||RC||'|'||EVENT_COUNT||'|'||RC_REASON from {U}.SH_PIPELINE_BATCH order by ID;\n"
        f"select 'TASK|'||ID||'|'||STATE from {U}.SH_TASK;\nselect 'CP|'||CHECKPOINT_JSON from {U}.SH_TASK;\n"
        f"select 'WINDOW|'||STATUS from {U}.SH_CAP_WINDOW;\nselect 'LEDGER|'||EVENT_ID from {U}.SH_EVENT_LEDGER order by EVENT_ID;\n"
        f"select 'FAIL|'||STAGE||'|'||REASON||'|'||DETAIL from {U}.SH_FAILCLOSED_RECORD;\n")
elif action == 'logs':
    run(label, ['docker', 'logs', NAME])
elif action == 'task-audit':
    task = (N / 'task-id.txt').read_text()
    sql(label, f"select 'AUDIT|'||to_char(ID,'FM99999999999999999999')||'|'||ACTION||'|'||DETAIL from {U}.SH_AUDIT_LOG where TARGET='{task}' and ACTION in ('pipeline.task.done','pipeline.task.failed','pipeline.task.resume') order by ID;\n")
elif action in ['poison-receipt', 'restore-receipt']:
    e = json.loads((N / 'approved-binding.json').read_text())['evidence']
    before, after = (e['schemaSequenceSha256'], '0' * 64) if action == 'poison-receipt' else ('0' * 64, e['schemaSequenceSha256'])
    sql(label, f"begin\nupdate {T}_COMMITS set SCHEMA_SHA='{after}' where BATCH_SHA='{e['batchSha256']}' and SCHEMA_SHA='{before}';\nif SQL%ROWCOUNT!=1 then raise_application_error(-20001,'OWNED_RECEIPT_PRECONDITION'); end if;\nend;\n/\ncommit;")
elif action == 'final-source':
    sql(label, "select 'LOGGING|'||supplemental_log_data_min||'|'||supplemental_log_data_all from v$database;\nselect 'OLD|'||ID||'|'||TXT||'|'||NOTE from ORKLAB.T_BASELINE_20261004;\nselect 'BASE|'||ID||'|'||TXT||'|'||NOTE from ORKLAB.T_V2_HIST_20261006;\n"
        f"select 'ACQ|'||to_char(ID,'FM99999999999999999999')||'|'||STATE||'|'||SNAPSHOT_SHA256||'|'||SNAPSHOT_SCN from {U}.SH_ORACLE_ACQUISITION;\n")
elif action == 'stop':
    assert run(label + '-owner', ['docker', 'inspect', NAME, '--format', '{{.Config.Labels.owner}}|{{.Config.Labels.scope}}|{{.State.Running}}']).strip() == 'OracleRedoKernel|split-history-product|true'
    run(label, ['docker', 'stop', '--time', '30', NAME])
    run(label + '-state', ['docker', 'inspect', NAME, '--format', '{{.State.Running}}|{{.State.ExitCode}}|{{.State.Status}}'])
elif action == 'restart':
    assert run(label + '-owner', ['docker', 'inspect', NAME, '--format', '{{.Config.Labels.owner}}|{{.Config.Labels.scope}}|{{.State.Running}}']).strip() == 'OracleRedoKernel|split-history-product|true'
    ack = list((N / 'acks').glob('*.ack.json'))
    assert len(ack) == 1
    (N / 'restart-pin.json').write_text(json.dumps(dict(application_sha256=sha(N / 'application.jar'),
        ack_sha256=sha(ack[0]), before_state_sha256=sha(N / 'after-drive.stdout.txt')), indent=2))
    run(label + '-stop', ['docker', 'stop', '--time', '30', NAME])
    run(label + '-start', ['docker', 'start', NAME])
else:
    raise ValueError(action)
