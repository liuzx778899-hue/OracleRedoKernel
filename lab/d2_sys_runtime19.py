"""Fresh D2 historical baseline. Actions are single-attempt; no source business DML."""
from pathlib import Path
import hashlib, json, secrets, shutil, subprocess, sys

R = Path('D:/work/vibecode/OracleRedoKernel')
N = R / 'lab/out/d2-sys-runtime-20261008'
C = R / 'lab/out/d3-raw-enrollment-candidate-20261008/artifacts/application.jar'
S, U, H, PORT = 'oraredo-19c-lab-20261004', 'ORKD2SYSCTL_20261008', 'ork-d2-sys-20261008', 37293
SHA = 'c0d2ec93af275f56f462a92135c07371a0bed61422537aba56894a3f3a9e98d8'
action = sys.argv[1]
assert action in {'check', 'check-source', 'create', 'start', 'health', 'health-r2', 'diagnose', 'acquire', 'prepare-truth', 'state', 'stop'}
N.mkdir(exist_ok=True)
marker = N / ('executed-' + action + '.py')
assert not marker.exists(), 'inspect authoritative state; do not repeat action'
shutil.copyfile(__file__, marker)
props = dict(line.split('=', 1) for line in (R / 'lab/out/d2-index-savepoint-20261008/runtime.secret.env').read_text(encoding='utf-8').splitlines())
control = N / 'control.secret.json'
redactions = [v for k, v in props.items() if 'PASSWORD' in k and v]
if control.exists(): redactions.append(json.loads(control.read_text())['password'])

def run(label, args, data=None):
    assert not (N / (label + '.rc.txt')).exists()
    p = subprocess.run(list(map(str, args)), input=data, capture_output=True, timeout=180)
    for suffix, value in [('stdout.txt', p.stdout), ('stderr.txt', p.stderr)]:
        for secret in sorted(redactions, key=len, reverse=True): value = value.replace(secret.encode(), b'<redacted-secret>')
        (N / (label + '.' + suffix)).write_bytes(value)
    (N / (label + '.rc.txt')).write_text(str(p.returncode))
    assert p.returncode == 0, (label, p.returncode)
    return p.stdout.decode('utf-8')

def sql(label, body):
    text = 'whenever sqlerror exit failure rollback\nwhenever oserror exit failure rollback\nset pages 0 heading off feedback off lines 32767 long 200000 longchunksize 200000\nalter session set container=ORKPDB;\n' + body + '\nexit success\n'
    value = run(label, ['docker', 'exec', '-i', S, 'sqlplus', '-s', '/', 'as', 'sysdba'], text.encode())
    assert 'SP2-' not in value
    return value

def http(label, path, body=None):
    login = json.loads((R / 'lab/out/product-active-20261007/admin.secret.json').read_text())
    redactions.append(login['password'])
    script = '''import json,urllib.request
def req(path,body=None,token=None):
 h={'Content-Type':'application/json'}
 if token:h['Authorization']='Bearer '+token
 with urllib.request.urlopen(urllib.request.Request(BASE+path,data=None if body is None else json.dumps(body).encode(),headers=h),timeout=120) as r:return json.loads(r.read())
l=req('/auth/login',LOGIN);assert l['code']==0
print(json.dumps(req(PATH,BODY,l['data']['token'])))
'''.replace('BASE', repr(f'http://127.0.0.1:{PORT}/api/v1')).replace('LOGIN', repr(login)).replace('PATH', repr(path)).replace('BODY', repr(body))
    result = json.loads(run(label, ['docker', 'exec', '-i', S, 'python3', '-'], script.encode()))
    assert result['code'] == 0
    return result['data']

if action == 'check':
    assert hashlib.sha256(C.read_bytes()).hexdigest() == SHA
    assert run('owner', ['docker', 'inspect', S, '--format', '{{.Config.Labels.owner}}|{{.State.Running}}']).strip() == 'OracleRedoKernel|true'
    assert H not in run('helper', ['docker', 'ps', '-a', '--format', '{{.Names}}']).splitlines()
    value = sql('resources', f"select 'CONTROL|'||count(*) from dba_users where username='{U}';\nselect 'LOGGING|'||supplemental_log_data_min||'|'||supplemental_log_data_all from v$database;\nselect 'ROW|'||ID||'|'||TXT||'|'||NOTE from ORKLAB.T_V2_HIST_20261006 order by ID;")
    assert 'CONTROL|0' in value and 'LOGGING|NO|NO' in value and 'ROW|0|history-product-restored|anchor' in value
    assert run('port', ['docker', 'exec', S, 'python3', '-c', f"import socket;s=socket.socket();s.settimeout(2);print(s.connect_ex(('127.0.0.1',{PORT})));s.close()"]).strip() == '111'
elif action == 'create':
    assert (N / 'resources-r2.rc.txt').read_text() == '0'
    assert 'COUNT|0' in sql('control-recheck', f"select 'COUNT|'||count(*) from dba_users where username='{U}';")
    password = 'P' + secrets.token_hex(12) + 'a1'
    assert not control.exists()
    control.write_text(json.dumps(dict(user=U, password=password)))
    redactions.append(password)
    sql('create-control', f'create user {U} identified by "{password}" default tablespace USERS quota unlimited on USERS;\ngrant create session,create table,create sequence to {U};')
elif action == 'check-source':
    assert props['SH_ORACLE_ACQUISITION_OWNER'] == 'ORKLAB'
    assert H not in run('helper-r2', ['docker', 'ps', '-a', '--format', '{{.Names}}']).splitlines()
    value = sql('resources-r2', f"select 'CONTROL|'||count(*) from dba_users where username='{U}';\nselect 'LOGGING|'||supplemental_log_data_min||'|'||supplemental_log_data_all from v$database;\nselect 'ROW|'||ID||'|'||TXT||'|'||NOTE from ORKLAB.T_V2_HIST_20261006 order by ID;")
    assert 'CONTROL|0' in value and 'LOGGING|NO|NO' in value and 'ROW|0|history-product-restored|anchor' in value
    assert run('port-r2', ['docker', 'exec', S, 'python3', '-c', f"import socket;s=socket.socket();s.settimeout(2);print(s.connect_ex(('127.0.0.1',{PORT})));s.close()"]).strip() == '111'
elif action == 'start':
    assert (N / 'create-control.rc.txt').read_text() == '0'
    assert hashlib.sha256(C.read_bytes()).hexdigest() == SHA
    assert H not in run('start-owner-check', ['docker', 'ps', '-a', '--format', '{{.Names}}']).splitlines()
    for folder in ['snapshots', 'prefix-state', 'prefix-input', 'acks', 'failures']: (N / folder).mkdir(exist_ok=True)
    props.update(SPRING_DATASOURCE_USERNAME=U, SPRING_DATASOURCE_PASSWORD=json.loads(control.read_text())['password'], SERVER_PORT=str(PORT), SH_WORKER_ID='49', SH_PIPELINE_AUTO_CONSUME_ENABLED='false', SH_ORACLE_RAW_PLAN_ENABLED='false', SH_ORACLE_RAW_PLAN_REGISTRATION_ENABLED='false', SH_ORACLE_ACQUISITION_DIRECTORY='/evidence/snapshots', SH_ORACLE_PREFIX_INPUT_DIRECTORY='/evidence/prefix-input', SH_ORACLE_PREFIX_CHECKPOINT_DIRECTORY='/evidence/prefix-state', SH_ORACLE_BOUNDED_ACK_DIRECTORY='/evidence/acks', SH_PIPELINE_TARGET_APPLY_FAIL_DIR='/evidence/failures')
    env = N / 'runtime.secret.env'
    assert not env.exists()
    env.write_bytes(''.join(k + '=' + v + '\n' for k, v in props.items()).encode())
    run('start', ['docker', 'run', '-d', '--name', H, '--label', 'owner=OracleRedoKernel', '--label', 'scope=oracle-d2-sys', '--user', '0', '--network', 'container:' + S, '--workdir', '/evidence', '--env-file', env, '--mount', 'type=bind,source=' + str(N) + ',target=/evidence', '--mount', 'type=bind,source=' + str(C.parent) + ',target=/candidate,readonly', '--entrypoint', 'java', 'flink:1.18-java17', '-Xmx1536m', '-jar', '/candidate/application.jar'])
elif action == 'diagnose':
    run('startup-logs', ['docker', 'logs', H])
elif action in {'health', 'health-r2'}:
    http(action, '/system/info')
    assert run('running-sha', ['docker', 'exec', H, 'sha256sum', '/candidate/application.jar']).split()[0] == SHA
    value = sql('flyway', f"select 'TABLES|'||count(*) from dba_tables where owner='{U}';\nselect 'ACQUISITIONS|'||count(*) from {U}.SH_ORACLE_ACQUISITION;\nselect 'TASKS|'||count(*) from {U}.SH_TASK;")
    assert 'ACQUISITIONS|0' in value and 'TASKS|0' in value
elif action == 'acquire':
    assert (N / 'running-sha.rc.txt').read_text() == '0'
    result = http('historical-acquire', '/oracle/acquisitions/historical', dict(sourceId='oracle-v3-HIST'))
    assert result['state'] == 'ACQUIRED_ONLY'
    snapshot = N / 'snapshots' / (str(result['id']) + '.snapshot')
    assert hashlib.sha256(snapshot.read_bytes()).hexdigest() == result['snapshotSha256']
    (N / 'baseline-reference.json').write_text(json.dumps(dict(path=str(snapshot), scn=result['snapshotScn'], sha256=result['snapshotSha256'], acquisition_id=str(result['id']), sourceActions=0, targetActions=0, trustedHistoricalHttp=True), indent=2))
elif action == 'prepare-truth':
    old = R / 'lab/out/d2-index-savepoint-20261008'
    text = (old / 'fresh-baseline-truth-r3.py').read_text(encoding='utf-8')
    assert text.count("O=R/'lab/out/d2-index-savepoint-20261008'") == 1
    text = text.replace("O=R/'lab/out/d2-index-savepoint-20261008'", "O=R/'lab/out/d2-sys-runtime-20261008'")
    (N / 'independent-baseline-truth.py').write_text(text, encoding='utf-8')
    for name in ['source.secret', 'ojdbc8.jar']: shutil.copyfile(old / name, N / name)
elif action == 'state':
    proof = json.loads((N / 'baseline-truth-proof.json').read_text())
    assert proof['differences'] == 0 and proof['source_actions'] == 0
    reference = json.loads((N / 'baseline-reference.json').read_text())
    value = sql('final-state', f"select 'ACQUISITION|'||to_char(ID,'FM99999999999999999999')||'|'||SNAPSHOT_SCN||'|'||SNAPSHOT_SHA256||'|'||STATE from {U}.SH_ORACLE_ACQUISITION;\nselect 'COUNTS|'||(select count(*) from {U}.SH_TASK)||'|'||(select count(*) from {U}.SH_ORACLE_INPUT_BINDING)||'|'||(select count(*) from {U}.SH_PIPELINE_BATCH)||'|'||(select count(*) from {U}.SH_EVENT_LEDGER)||'|'||(select count(*) from {U}.SH_PUBLISH_GATE) from dual;\nselect 'LOGGING|'||supplemental_log_data_min||'|'||supplemental_log_data_all from v$database;")
    assert f"ACQUISITION|{reference['acquisition_id']}|{reference['scn']}|{reference['sha256']}|ACQUIRED_ONLY" in value
    assert 'COUNTS|0|0|0|0|0' in value and 'LOGGING|NO|NO' in value
elif action == 'stop':
    assert run('stop-owner', ['docker', 'inspect', H, '--format', '{{.Config.Labels.owner}}|{{.Config.Labels.scope}}']).strip() == 'OracleRedoKernel|oracle-d2-sys'
    run('stop', ['docker', 'stop', H])
print(json.dumps(dict(action=action, sourceBusinessActions=0, automaticConsumption=False)))
