"""One-shot owned historical target experiment, using frozen source inputs only."""
import base64, hashlib, json, shutil, subprocess
from pathlib import Path

R = Path(__file__).resolve().parent.parent
N = R / 'lab/out/historical-target-candidate-20261006'
O = R / 'lab/out/v3-historical-raw-20261006'
Q = R / 'lab/out/historical-schema-protocol-20261006'
T = R / 'lab/out/oracle-target-20261005'
SOURCE = 'oraredo-19c-lab-20261004'
IMAGE = 'flink:1.18-java17'
sha = lambda p: hashlib.sha256(p.read_bytes()).hexdigest()
assert not (N / 'target-preflight.command.json').exists(), 'one-shot target experiment already started'
secret = (T / 'target.secret').read_text().splitlines()
assert secret[0] == 'ORKTGT_CMT_20261005' and len(secret) == 3
user, password, _ = secret
(N / 'executed-target-driver.py').write_bytes(Path(__file__).read_bytes())
shutil.copyfile(R / 'lab/OracleHistoricalTargetProbe.java', N / 'executed-target-probe.java')

def run(name, args, data=None, expected=0):
    args = list(map(str, args))
    assert not (N / (name + '.command.json')).exists(), name
    (N / (name + '.command.json')).write_text(json.dumps(args))
    result = subprocess.run(args, input=data, capture_output=True, timeout=300)
    for suffix, value in [('stdout.txt', result.stdout), ('stderr.txt', result.stderr)]:
        (N / (name + '.' + suffix)).write_bytes(value.replace(password.encode(), b'<redacted-secret>'))
    (N / (name + '.rc.txt')).write_text(str(result.returncode))
    assert result.returncode == expected, (name, result.returncode)
    return result

def sql(name, body):
    script = ('whenever sqlerror exit failure rollback\nwhenever oserror exit failure rollback\n'
        'set pages 0 lines 32767 feedback off heading off echo off trimspool on\n'
        'alter session set container=ORKPDB;\n' + body + '\nexit success\n')
    (N / (name + '.sql')).write_text(script)
    return run(name, ['docker', 'exec', '-i', SOURCE, 'sqlplus', '-s', '/', 'as', 'sysdba'], script.encode()).stdout.decode()

assert run('target-container-ownership', ['docker', 'inspect', SOURCE, '--format', '{{.Config.Labels.owner}}|{{.State.Status}}']).stdout.decode().strip() == 'OracleRedoKernel|running'
preflight = sql('target-preflight', "select 'EXISTING|'||count(*) from dba_tables where owner='" + user + "' and table_name in ('HS6_IDENT','HS6_ROWS','HS6_APPLIED','HS6_MEMBERS','HS6_COMMITS','HSL6_IDENT','HSL6_ROWS','HSL6_APPLIED','HSL6_MEMBERS','HSL6_COMMITS');\nselect 'SOURCE_ROW|'||ID||'|'||TXT||'|'||NOTE from ORKLAB.T_BASELINE_20261004;\nselect 'SOURCE_CONFIG|'||supplemental_log_data_min||'|'||supplemental_log_data_all from v$database;\nselect 'SOURCE_ID|'||dbid||'|'||activation#||'|'||resetlogs_change# from v$database;")
assert 'EXISTING|0' in preflight and 'SOURCE_ROW|11|blocking-closed|new-note' in preflight
assert 'SOURCE_CONFIG|NO|NO' in preflight and 'SOURCE_ID|1276726821|1276717861|1920977' in preflight
for name in ['actual-v3.snapshot', 'capture/sequence-68.oracap', 'capture/sequence-69.oracap']:
    path = O / name
    shutil.copyfile(path, N / path.name)
assert sha(N / 'actual-v3.snapshot') == '226c9322176bb6fafffb9c6a2cecd093bb76a1bf2f9e6bd71570b7f4c5bc839a'
shutil.copyfile(T / 'target.secret', N / 'target.secret')
shutil.copyfile(T / 'ojdbc8.jar', N / 'ojdbc8.jar')
classes = N / 'target-classes'
classes.mkdir()
classpath = ';'.join(str(N / name) for name in ['kernel.jar', 'adapter.jar', 'spi.jar', 'ojdbc8.jar'])
run('compile-target-probe', ['D:/work/Java/jdk-17.0.7/bin/javac.exe', '-encoding', 'UTF-8', '-cp', classpath, '-d', classes, R / 'lab/OracleHistoricalTargetProbe.java'])
frozen = json.loads((Q / 'actual-schema-evidence.stdout.txt').read_text())
evidence = frozen['evidence']
binding = ':'.join(str(evidence['source'][key]) for key in ['dbid', 'activationId', 'compatibility', 'thread', 'resetlogsId', 'resetlogsScn', 'containerUid', 'containerId'])
ddl = ''
for prefix in ['HS6', 'HSL6']:
    table = user + '.' + prefix
    target_id = 'oracle-history-' + prefix + '-20261006'
    ddl += f"create table {table}_IDENT(ID number primary key,TARGET_ID varchar2(128) not null,SOURCE_BINDING varchar2(512) not null,SCHEMA_SHA varchar2(64) not null);\n"
    ddl += f"insert into {table}_IDENT values(1,'{target_id}','{binding}','{evidence['schemaSha256']}');\n"
    check = " constraint HSL6_LATE_CK check(TXT!='history-v3-restored')" if prefix == 'HSL6' else ''
    ddl += f"create table {table}_ROWS(ID number primary key,TXT varchar2(128){check},NOTE varchar2(128),EXTRA_V3 varchar2(128));\n"
    ddl += f"insert into {table}_ROWS values(0,'dictionary-restored','anchor',NULL);\n"
    ddl += f"create table {table}_APPLIED(EVENT_ID varchar2(80) primary key,PAYLOAD_SHA varchar2(64) not null,BEFORE_IMAGE varchar2(4000),AFTER_IMAGE varchar2(4000),OPERATION varchar2(16) not null);\n"
    ddl += f"create table {table}_MEMBERS(BATCH_SHA varchar2(64),ORDINAL number,EVENT_ID varchar2(80) references {table}_APPLIED(EVENT_ID),primary key(BATCH_SHA,ORDINAL),unique(BATCH_SHA,EVENT_ID));\n"
    ddl += f"create table {table}_COMMITS(BATCH_SHA varchar2(64) primary key,TARGET_ID varchar2(128) not null,SCHEMA_SHA varchar2(64) not null,EVENTS_COUNT number not null);\n"
sql('target-owned-setup', ddl + 'commit;')

def java(name, mode, prefix, expected=0):
    args = ['docker', 'run', '--rm', '--name', 'ork-history-' + name + '-20261006', '--label', 'owner=OracleRedoKernel',
        '--label', 'scope=historical-target', '--network', 'container:' + SOURCE, '--mount',
        'type=bind,source=' + str(N) + ',target=/evidence', '--entrypoint', 'java', IMAGE, '-Xmx1536m', '-cp',
        '/evidence/kernel.jar:/evidence/adapter.jar:/evidence/spi.jar:/evidence/ojdbc8.jar:/evidence/target-classes',
        'OracleHistoricalTargetProbe', mode, '/evidence/target.secret', prefix, 'oracle-history-' + prefix + '-20261006',
        '/evidence/actual-v3.snapshot', sha(N / 'actual-v3.snapshot'), '50342539']
    for sequence in [68, 69]:
        name_input = 'sequence-' + str(sequence) + '.oracap'
        args.extend(['/evidence/' + name_input, sha(N / name_input)])
    return run(name, args, expected=expected)

def image(values):
    def value(v):
        if v is None: return 'NULL'
        if isinstance(v, (int, float)): return 'NUMBER:' + str(v)
        return 'VARCHAR2:' + base64.urlsafe_b64encode(v.encode()).decode().rstrip('=')
    return ';'.join(k + '=' + value(v) for k, v in values.items())

def state(name, prefix, committed):
    table = user + '.' + prefix
    text = sql(name, f"select 'STATE|'||(select count(*) from {table}_APPLIED)||'|'||(select count(*) from {table}_MEMBERS)||'|'||(select count(*) from {table}_COMMITS) from dual;\n"
        f"select 'ROW|'||ID||'|'||TXT||'|'||NOTE||'|'||nvl(EXTRA_V3,'<NULL>') from {table}_ROWS order by ID;\n"
        f"select 'EVENT|'||m.ORDINAL||'|'||m.EVENT_ID||'|'||a.PAYLOAD_SHA||'|'||a.BEFORE_IMAGE||'|'||a.AFTER_IMAGE||'|'||a.OPERATION from {table}_MEMBERS m join {table}_APPLIED a on a.EVENT_ID=m.EVENT_ID order by m.ORDINAL;\n"
        f"select 'COMMIT|'||BATCH_SHA||'|'||TARGET_ID||'|'||SCHEMA_SHA||'|'||EVENTS_COUNT from {table}_COMMITS;\n"
        f"select 'IDENT|'||TARGET_ID||'|'||SOURCE_BINDING||'|'||SCHEMA_SHA from {table}_IDENT;")
    assert 'STATE|' + ('2|2|1' if committed else '0|0|0') in text
    assert 'ROW|0|' + ('history-v3-restored' if committed else 'dictionary-restored') + '|anchor|<NULL>' in text
    assert f"IDENT|oracle-history-{prefix}-20261006|{binding}|{evidence['schemaSha256']}" in text
    actual_events = [line.strip() for line in text.splitlines() if line.strip().startswith('EVENT|')]
    expected_events = []
    if committed:
        for i, envelope in enumerate(frozen['envelopes']):
            expected_events.append('|'.join(['EVENT', str(i), envelope['eventId'], evidence['events'][i]['payloadSha256'], image(envelope['before']), image(envelope['after']), envelope['operation']]))
        assert f"COMMIT|{evidence['batchSha256']}|oracle-history-{prefix}-20261006|{evidence['schemaSequenceSha256']}|2" in text
    assert actual_events == expected_events, (name, actual_events, expected_events)
    return text

result = java('apply', 'apply', 'HS6')
assert ('COMMITTED|2|' + evidence['batchSha256'] + '|' + evidence['schemaSha256'] + '|' + evidence['schemaSequenceSha256'] + '|2').encode() in result.stdout
before = state('applied-independent-state', 'HS6', True)
java('verify', 'verify', 'HS6')
repeat = java('repeat', 'repeat', 'HS6')
assert b'COMMITTED|0|' in repeat.stdout
assert state('repeated-independent-state', 'HS6', True) == before
late = java('late', 'apply', 'HSL6', 1)
assert not late.stdout and b'ORA-02290' in late.stderr and b'OBSERVED_ROW_UPDATES=1;ROLLBACK=true' in late.stderr
state('late-independent-state', 'HSL6', False)
after = sql('source-readonly-after', "select 'SOURCE_CONFIG|'||supplemental_log_data_min||'|'||supplemental_log_data_all from v$database;\nselect 'SOURCE_ROW|'||ID||'|'||TXT||'|'||NOTE from ORKLAB.T_BASELINE_20261004;")
assert 'SOURCE_CONFIG|NO|NO' in after and 'SOURCE_ROW|11|blocking-closed|new-note' in after
proof = dict(events=2, event_columns=[4, 3], complete_ordered_ledger_matches_frozen_factory=True,
    commit_schema_sequence_sha256=evidence['schemaSequenceSha256'], identity_baseline_sha256=evidence['schemaSha256'],
    duplicate_new_applications=0, duplicate_independent_state_unchanged=True, late_successful_row_updates_before_failure=1,
    late_failure='ORA-02290', atomic_rollback_row_and_all_ledgers=True, source_dml=0, source_configuration_changes=0,
    same_instance_owned_target=True, nullable_prebuilt_union_only=True, product_http_verified=False, checkpoint_ack_verified=False)
(N / 'historical-target-proof.json').write_text(json.dumps(proof, indent=2))
print(json.dumps(proof))
