"""Once-only dedicated-lab AFTER ALTER delay; freeze actual archives, never synthesize placement."""
from pathlib import Path
import hashlib, json, shutil, subprocess, sys, time

R = Path('D:/work/vibecode/OracleRedoKernel')
N = R / 'lab/out/d2-sys-cross-member-20261008'
B = R / 'lab/out/d2-sys-baseline-sealed-20261008'
S, TRIGGER = 'oraredo-19c-lab-20261004', 'ORK_D2_SYS_DELAY_20261008'
assert sys.argv[1:] == ['--execute']
assert not N.exists(), 'source action batch must not be replayed'
baseline = json.loads((B / 'baseline-reference.json').read_text())
assert hashlib.sha256(Path(baseline['path']).read_bytes()).hexdigest() == baseline['sha256']
assert json.loads((B / 'baseline-truth-proof.json').read_text())['differences'] == 0
N.mkdir()
shutil.copyfile(__file__, N / 'executed-source-driver.py')
(N / 'baseline-reference.json').write_text(json.dumps(baseline, indent=2))

def run(label, args, data=None):
    assert not (N / (label + '.rc.txt')).exists()
    args = list(map(str, args))
    (N / (label + '.command.json')).write_text(json.dumps(args))
    p = subprocess.run(args, input=data, capture_output=True, timeout=180)
    for suffix, value in [('stdout.txt', p.stdout), ('stderr.txt', p.stderr), ('rc.txt', str(p.returncode).encode())]: (N / (label + '.' + suffix)).write_bytes(value)
    assert p.returncode == 0, (label, p.returncode)
    return p.stdout.decode('utf-8')

def sql_text(body):
    return 'whenever sqlerror exit failure rollback\nwhenever oserror exit failure rollback\nset pages 0 lines 32767 feedback off heading off trimspool on\n' + body + '\nexit success\n'

def sql(label, body):
    text = sql_text(body)
    (N / (label + '.sql')).write_bytes(text.encode())
    out = run(label, ['docker', 'exec', '-i', S, 'sqlplus', '-s', '/', 'as', 'sysdba'], text.encode())
    assert 'SP2-' not in out
    return out

assert run('owner', ['docker', 'inspect', S, '--format', '{{.Config.Labels.owner}}|{{.State.Running}}']).strip() == 'OracleRedoKernel|true'
pre = sql('preflight', "select 'CURRENT|'||sequence# from v$log where status='CURRENT';\nselect 'LOGGING|'||supplemental_log_data_min||'|'||supplemental_log_data_all from v$database;\nalter session set container=ORKPDB;\nselect 'ROW|'||ID||'|'||TXT||'|'||NOTE from ORKLAB.T_V2_HIST_20261006;\nselect 'COLUMNS|'||count(*) from dba_tab_cols where owner='ORKLAB' and table_name='T_V2_HIST_20261006';\nselect 'TRIGGER|'||count(*) from dba_triggers where owner='SYS' and trigger_name='ORK_D2_SYS_DELAY_20261008';")
assert all(x in pre for x in ['LOGGING|NO|NO', 'ROW|0|history-product-restored|anchor', 'COLUMNS|3', 'TRIGGER|0'])
start = int(next(l.split('|')[1] for l in pre.splitlines() if l.startswith('CURRENT|')))
created = False
p = None
try:
    (N / 'source-actions-started.txt').write_text('new experiment only; never replay this batch\n')
    sql('create-delay', "alter session set container=ORKPDB;\ncreate trigger SYS.ORK_D2_SYS_DELAY_20261008 after alter on database\nbegin\n if ora_dict_obj_owner='ORKLAB' and ora_dict_obj_name='T_V2_HIST_20261006' and sys_context('USERENV','CLIENT_IDENTIFIER')='ork-d2-sys-ddl-20261008' then\n  dbms_session.set_identifier('ork-d2-sys-held-20261008');\n  dbms_session.sleep(60);\n end if;\nend;\n/\nselect 'VALID|'||status from dba_objects where owner='SYS' and object_name='ORK_D2_SYS_DELAY_20261008' and object_type='TRIGGER';")
    created = True
    assert 'VALID|VALID' in (N / 'create-delay.stdout.txt').read_text()
    ddl = sql_text("alter session set container=ORKPDB;\nbegin dbms_session.set_identifier('ork-d2-sys-ddl-20261008');end;\n/\nalter table ORKLAB.T_V2_HIST_20261006 add (D2SYS_COL varchar2(80));\nprompt ADD_FINISHED")
    (N / 'held-ddl.sql').write_bytes(ddl.encode())
    with (N / 'held-ddl.stdout.txt').open('wb') as out, (N / 'held-ddl.stderr.txt').open('wb') as err:
        p = subprocess.Popen(['docker', 'exec', '-i', S, 'sqlplus', '-s', '/', 'as', 'sysdba'], stdin=subprocess.PIPE, stdout=out, stderr=err)
        p.stdin.write(ddl.encode()); p.stdin.close()
        held = False
        for attempt in range(15):
            state = sql('held-state-' + str(attempt), "select 'HELD|'||s.sid||'|'||s.serial#||'|'||s.event||'|'||nvl(to_char(t.xidusn),'-')||'.'||nvl(to_char(t.xidslot),'-')||'.'||nvl(to_char(t.xidsqn),'-') from v$session s left join v$transaction t on s.taddr=t.addr where s.client_identifier='ork-d2-sys-held-20261008';")
            if 'HELD|' in state:
                (N / 'held-observation.txt').write_text(state)
                held = True
                break
            assert p.poll() is None, 'DDL finished before delay observed; do not replay'
            time.sleep(1)
        assert held, 'delay not observed; not a pending SYS acceptance'
        print('HELD_OBSERVED', flush=True)
        sql('switch-prefix', 'alter system archive log current;')
        prefix = sql('prefix-archive', f"select 'ARCHIVE|'||name from v$archived_log where sequence#={start} and archived='YES' and deleted='NO' and name is not null order by recid;")
        paths = sorted(set(l.split('|')[1] for l in prefix.splitlines() if l.startswith('ARCHIVE|')))
        assert len(paths) == 1
        run('prefix-copy', ['docker', 'cp', S + ':' + paths[0], N / (str(start) + '.raw')])
        actual = run('prefix-source-sha', ['docker', 'exec', S, 'sha256sum', paths[0]]).split()[0]
        assert hashlib.sha256((N / (str(start) + '.raw')).read_bytes()).hexdigest() == actual
        print('PREFIX_FROZEN sequence=' + str(start), flush=True)
        rc = p.wait(timeout=110)
        (N / 'held-ddl.rc.txt').write_text(str(rc))
        assert rc == 0 and 'ADD_FINISHED' in (N / 'held-ddl.stdout.txt').read_text() and 'SP2-' not in (N / 'held-ddl.stdout.txt').read_text()
    sql('suffix', "alter session set container=ORKPDB;\nbegin update ORKLAB.T_V2_HIST_20261006 set TXT='d2-sys-added' where ID=0 and TXT='history-product-restored' and NOTE='anchor' and D2SYS_COL is null;if sql%rowcount!=1 then raise_application_error(-20001,'rowcount');end if;end;\n/\ncommit;\nalter table ORKLAB.T_V2_HIST_20261006 drop column D2SYS_COL;\nbegin update ORKLAB.T_V2_HIST_20261006 set TXT='history-product-restored' where ID=0 and TXT='d2-sys-added' and NOTE='anchor';if sql%rowcount!=1 then raise_application_error(-20002,'rowcount');end if;end;\n/\ncommit;")
    current = sql('end-sequence', "select 'CURRENT|'||sequence# from v$log where status='CURRENT';")
    end = int(next(l.split('|')[1] for l in current.splitlines() if l.startswith('CURRENT|')))
    sql('switch-closed', 'alter system archive log current;')
    archives = []
    for seq in range(start, end + 1):
        out = sql('archive-' + str(seq), f"select 'ARCHIVE|'||name from v$archived_log where sequence#={seq} and archived='YES' and deleted='NO' and name is not null order by recid;")
        paths = sorted(set(l.split('|')[1] for l in out.splitlines() if l.startswith('ARCHIVE|')))
        assert len(paths) == 1
        file = N / (str(seq) + '.raw')
        if not file.exists(): run('copy-' + str(seq), ['docker', 'cp', S + ':' + paths[0], file])
        sha = hashlib.sha256(file.read_bytes()).hexdigest()
        assert run('source-sha-' + str(seq), ['docker', 'exec', S, 'sha256sum', paths[0]]).split()[0] == sha
        archives.append(dict(sequence=seq, path=str(file), sourcePath=paths[0], sha256=sha, bytes=file.stat().st_size))
    (N / 'archives.json').write_text(json.dumps(archives, indent=2))
    final = sql('source-final', "select 'LOGGING|'||supplemental_log_data_min||'|'||supplemental_log_data_all from v$database;\nalter session set container=ORKPDB;\nselect 'ROW|'||ID||'|'||TXT||'|'||NOTE from ORKLAB.T_V2_HIST_20261006;\nselect 'COLUMNS|'||count(*) from dba_tab_cols where owner='ORKLAB' and table_name='T_V2_HIST_20261006';\nselect 'OLD|'||ID||'|'||TXT||'|'||NOTE from ORKLAB.T_BASELINE_20261004;")
    assert all(x in final for x in ['LOGGING|NO|NO', 'ROW|0|history-product-restored|anchor', 'COLUMNS|3', 'OLD|11|blocking-closed|new-note'])
    print('RAW_FROZEN source restored; pending SYS parser acceptance unproven', flush=True)
finally:
    if p is not None and p.poll() is None:
        rc = p.wait(timeout=110)
        (N / 'held-ddl-finally.rc.txt').write_text(str(rc))
    if created:
        sql('drop-delay', 'alter session set container=ORKPDB;\ndrop trigger SYS.ORK_D2_SYS_DELAY_20261008;')
