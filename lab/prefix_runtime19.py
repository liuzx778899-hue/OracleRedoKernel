"""Owned product prefix registration experiment; original source inputs remain read-only."""
import hashlib, json, secrets, shutil, subprocess, sys
from pathlib import Path

R = Path(__file__).resolve().parent.parent
N = R / 'lab/out/product-prefix-runtime-20261007'
A = R / 'lab/out/product-active-20261007'
C = R / 'lab/out/product-prefix-registration-20261007'
S = 'oraredo-19c-lab-20261004'
NAME, U, OLD, PORT = 'ork-prefix-runtime-20261007', 'ORKPFXCTL_20261007', 'ORKACTCTL_20261007', 37267
ACQ = 101320533446070272
action = sys.argv[1]
label = sys.argv[2] if len(sys.argv) > 2 else action
sha = lambda p: hashlib.sha256(p.read_bytes()).hexdigest()
if action == 'bootstrap':
    N.mkdir(exist_ok=False)
assert not (N / ('executed-' + label + '.py')).exists()
shutil.copyfile(__file__, N / ('executed-' + label + '.py'))

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

if action == 'bootstrap':
    assert sha(C / 'application.jar') == json.loads((C / 'product-proof.json').read_text())['product_sha256']
    assert run('source-owner', ['docker', 'inspect', S, '--format', '{{.Config.Labels.owner}}|{{.State.Running}}']).strip() == 'OracleRedoKernel|true'
    assert NAME not in run('helper-preflight', ['docker', 'ps', '-a', '--format', '{{.Names}}']).splitlines()
    out = sql('control-preflight', f"select 'COUNT|'||count(*) from dba_users where username='{U}';\nselect 'LOGGING|'||supplemental_log_data_min||'|'||supplemental_log_data_all from v$database;")
    assert 'COUNT|0' in out and 'LOGGING|NO|NO' in out
    pwd = 'P' + secrets.token_hex(12) + 'a1'
    (N / 'control.secret.json').write_text(json.dumps(dict(user=U, password=pwd)))
    sql('create-control', f'create user {U} identified by "{pwd}" default tablespace USERS quota unlimited on USERS;\ngrant create session,create table,create sequence to {U};', True)
    props = dict(line.split('=', 1) for line in (A / 'runtime.secret.env').read_text().splitlines())
    props.update(SPRING_DATASOURCE_USERNAME=U, SPRING_DATASOURCE_PASSWORD=pwd, SERVER_PORT=str(PORT), SH_WORKER_ID='26', SH_ORACLE_BOUNDED_TARGET_PREFIX='SHPX7', SH_ORACLE_BOUNDED_TARGET_ID='oracle-active-SHPX7-20261007', SH_ORACLE_PREFIX_ENABLED='true', SH_ORACLE_PREFIX_INPUT_DIRECTORY='/inputs', SH_ORACLE_PREFIX_CHECKPOINT_DIRECTORY='/evidence/prefix-checkpoints', SH_PIPELINE_TARGET_APPLY_FAIL_DIR='/evidence/failures')
    assert props['SH_PIPELINE_AUTO_CONSUME_ENABLED'] == 'false'
    (N / 'runtime.secret.env').write_text(''.join(k + '=' + v + '\n' for k, v in props.items()))
    run('start', ['docker', 'run', '-d', '--name', NAME, '--label', 'owner=OracleRedoKernel', '--label', 'scope=oracle-prefix-runtime', '--user', '0', '--network', 'container:' + S, '--workdir', '/evidence', '--env-file', N / 'runtime.secret.env', '--mount', 'type=bind,source=' + str(N) + ',target=/evidence', '--mount', 'type=bind,source=' + str(C) + ',target=/candidate,readonly', '--mount', 'type=bind,source=' + str(A / 'capture') + ',target=/inputs,readonly', '--mount', 'type=bind,source=' + str(A / 'snapshots') + ',target=/evidence/snapshots,readonly', '--entrypoint', 'java', 'flink:1.18-java17', '-Xmx1536m', '-jar', '/candidate/application.jar'])
elif action == 'health':
    http(label, '/system/info')
    assert run(label + '-sha', ['docker', 'exec', NAME, 'sha256sum', '/candidate/application.jar']).split()[0] == sha(C / 'application.jar')
elif action == 'enroll':
    out = sql('copy-original-registration', f"insert into {U}.SH_ORACLE_ACQUISITION select * from {OLD}.SH_ORACLE_ACQUISITION where ID={ACQ} and STATE='ACQUIRED_ONLY';\ncommit;\nselect 'META_EQUAL|'||count(*) from {U}.SH_ORACLE_ACQUISITION n join {OLD}.SH_ORACLE_ACQUISITION o on n.ID=o.ID where n.SOURCE_ID=o.SOURCE_ID and n.AUTHORITY=o.AUTHORITY and n.SNAPSHOT_SHA256=o.SNAPSHOT_SHA256 and n.SNAPSHOT_SCN=o.SNAPSHOT_SCN and n.STATE=o.STATE and n.REGISTERED_AT=o.REGISTERED_AT and n.REGISTERED_BY=o.REGISTERED_BY and dbms_lob.compare(n.METADATA_JSON,o.METADATA_JSON)=0;")
    assert 'META_EQUAL|1' in out
    http('original-acquisition-get', '/oracle/acquisitions/' + str(ACQ))
    binding = dict(acquisitionId=ACQ, leaf=50342539, captures=[dict(path='/inputs/sequence-75.oracap', sha256=sha(A / 'capture/sequence-75.oracap'))], checkpoint=None)
    (N / 'prefix-approved.json').write_text(json.dumps(binding, indent=2))
    text = json.dumps(binding, separators=(',', ':')).replace("'", "''")
    sql('enroll-prefix', f"insert into {U}.SH_ORACLE_INPUT_BINDING values(9101,'oracle-v3-HIST','active-75-prefix',1,1,'v3-history-consumer','9a0aac66a1babbb575e9ce68fb60383fecd6006707bc6570af5a065e079dbde0','APPROVED_PREFIX',to_clob('{text}'),systimestamp,'owned-prefix-registration');\ncommit;")
elif action in ['checkpoint', 'checkpoint-refused', 'get', 'get-refused']:
    get = action.startswith('get')
    http(label, '/oracle/prefixes/9101' + ('' if get else '/checkpoint'), None if get else {}, not action.endswith('refused'))
elif action in ['poison', 'restore']:
    files = list((N / 'prefix-checkpoints').glob('*.state'))
    assert len(files) == 1
    file = files[0]
    original = R / 'lab/out/product-active-marker2-20261007/active.state'
    assert sha(original) == '7d6eef2ad4439bdad1ec93a853de95b36810cb334637db81fe588022ba1b6512'
    if action == 'poison':
        assert file.read_bytes() == original.read_bytes()
        file.write_bytes(original.read_bytes() + b'owned prefix poison\n')
    else:
        assert file.read_bytes() == original.read_bytes() + b'owned prefix poison\n'
        shutil.copyfile(file, N / (label + '-poisoned.state'))
        file.write_bytes(original.read_bytes())
elif action == 'block-register':
    sql(label, f"alter table {U}.SH_ORACLE_INPUT_BINDING add constraint PFX_REGISTER_FAULT check (STATE!='CHECKPOINTED_PREFIX');")
elif action == 'unblock-register':
    sql(label, f'alter table {U}.SH_ORACLE_INPUT_BINDING drop constraint PFX_REGISTER_FAULT;')
elif action == 'state':
    sql(label, f"select 'PREFIX|'||STATE||'|'||BINDING_JSON from {U}.SH_ORACLE_INPUT_BINDING where ID=9101;\nselect 'ZERO|'||(select count(*) from {U}.SH_TASK)||'|'||(select count(*) from {U}.SH_CAP_WINDOW)||'|'||(select count(*) from {U}.SH_PIPELINE_BATCH)||'|'||(select count(*) from {U}.SH_EVENT_LEDGER)||'|'||(select count(*) from {U}.SH_PUBLISH_GATE) from dual;\nselect 'AUDIT|'||count(*) from {U}.SH_AUDIT_LOG where ACTION='oracle.prefix.checkpoint';\nselect 'FAIL|'||STAGE||'|'||REASON||'|'||DETAIL from {U}.SH_FAILCLOSED_RECORD;")
    files = list((N / 'prefix-checkpoints').glob('*')) if (N / 'prefix-checkpoints').exists() else []
    proof = dict(files=[dict(name=f.name, sha256=sha(f), bytes=f.stat().st_size) for f in files], acks=len(list((N / 'acks').glob('*'))))
    (N / (label + '-files.json')).write_text(json.dumps(proof, indent=2))
elif action in ['kill', 'restart', 'stop', 'logs']:
    assert run(label + '-owner', ['docker', 'inspect', NAME, '--format', '{{.Config.Labels.owner}}|{{.Config.Labels.scope}}']).strip() == 'OracleRedoKernel|oracle-prefix-runtime'
    args = {'kill': ['kill', '--signal', 'KILL'], 'restart': ['start'], 'stop': ['stop', '--time', '30'], 'logs': ['logs']}[action]
    run(label, ['docker'] + args + [NAME])
    if action in ['kill', 'stop']: run(label + '-terminal', ['docker', 'inspect', NAME, '--format', '{{.State.Running}}|{{.State.ExitCode}}'])
else:
    raise ValueError(action)
