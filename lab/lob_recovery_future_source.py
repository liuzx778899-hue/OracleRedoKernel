"""Two new future windows on owned, already frozen LOB fixtures. Never replay actions."""
from pathlib import Path
import hashlib,json,re,subprocess,sys,time
R=Path(__file__).resolve().parent.parent
N=R/'lab/out/lob-unified-20261012/source'
S='oraredo-19c-lab-20261004'
assert sys.argv[1:]==['--execute']
N.mkdir(parents=True,exist_ok=False)
(N/'executed.py').write_bytes(Path(__file__).read_bytes())
common=(R/'lab/common_fields_source.py').read_text()
exec(common[common.index('reader='):common.index('libs=N/')])
(N/'inputs').mkdir()
tables=[('T_D4_MIG_20261011',1,'after-migration'),('T_D4_HLB_20261011',2,'new-generation'),('T_D4_HLS_20261011',2,'new-generation')]
started=False
try:
    assert run('state',['docker','inspect',S,'--format','{{.Config.Labels.owner}}|{{.State.Status}}']).strip()=='OracleRedoKernel|exited'
    run('start',['docker','start',S]);started=True
    for attempt in range(30):
        try:
            if 'READY|READ WRITE' in sql('ready-'+str(attempt),"select 'READY|'||open_mode from v$pdbs where name='ORKPDB';"):break
        except AssertionError:
            if attempt==29:raise
        time.sleep(3)
    else:raise AssertionError('PDB_NOT_READY')
    for table,key,value in tables:
        out=sql('pre-'+table,f"alter session set container=ORKPDB;\nselect 'ROW|'||ID||'|'||TXT from ORKLAB.{table};")
        assert [l for l in out.splitlines() if l.startswith('ROW|')]==[f'ROW|{key}|{value}']
    pre=sql('pre',"select 'CURRENT|'||sequence# from v$log where status='CURRENT';\nselect 'LOGGING|'||supplemental_log_data_min||'|'||supplemental_log_data_all from v$database;")
    assert 'LOGGING|NO|NO' in pre
    first=121
    copied=set();archives=[]
    (N/'actions.started').write_text('two unique future windows; never replay\n')
    for window in (1,2):
        for table,key,value in tables:
            out=sql(f'window-{window}-{table}',f"alter session set container=ORKPDB;\nupdate ORKLAB.{table} set TXT='recovery-window-{window}',B=hextoraw('00AAFF80{window:02X}'),C=to_clob('recovery-text-{window}') where ID={key};\ncommit;\nselect 'ROW|'||ID||'|'||TXT||'|'||rawtohex(dbms_lob.substr(B,1000,1))||'|'||dbms_lob.substr(C,1000,1) from ORKLAB.{table};")
            assert f'ROW|{key}|recovery-window-{window}|00AAFF80{window:02X}|recovery-text-{window}' in out
        closed=sql(f'close-{window}',"select 'CURRENT|'||sequence# from v$log where status='CURRENT';\nalter system archive log current;")
        last=int(next(l.split('|')[1] for l in closed.splitlines() if l.startswith('CURRENT|')))
        for sequence in range(first,last+1):
            if sequence in copied:continue
            out=sql('archive-'+str(sequence),f"select 'PATH|'||name from v$archived_log where sequence#={sequence} and archived='YES' and deleted='NO' and name is not null;")
            paths=set(l.split('|')[1] for l in out.splitlines() if l.startswith('PATH|'));assert len(paths)==1
            path=paths.pop();local=N/'inputs'/f'{sequence}.raw'
            run('copy-'+str(sequence),['docker','cp',S+':'+path,local])
            assert run('sha-'+str(sequence),['docker','exec',S,'sha256sum',path]).split()[0]==sha(local)
            archives.append(dict(sequence=sequence,sha256=sha(local),bytes=local.stat().st_size,window=window));copied.add(sequence)
        (N/'archives.json').write_text(json.dumps(archives,indent=2))
    print('LOB_FUTURE_WINDOWS_FROZEN',flush=True)
finally:
    if started:run('source-stop',['docker','stop','-t','60',S])
