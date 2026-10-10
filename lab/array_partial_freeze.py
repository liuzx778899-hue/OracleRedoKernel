"""Freeze the already executed ARRAY fixture after the preserved SQLPlus output assertion failure."""
from pathlib import Path
import hashlib,json,re,shutil,subprocess,sys,time
R=Path(__file__).resolve().parent.parent;BASE=R/'lab/out/array-partial-20261011/source';N=BASE/'freeze-r1'
S='oraredo-19c-lab-20261004';assert sys.argv[1:]==['--execute']
assert (BASE/'source-stop.rc').read_text().strip()=='0'
assert 'ARRAY_COMMITTED' in (BASE/'actions.stdout').read_text()
assert (BASE/'prefix-rescue.rc').read_text().strip()=='0'
N.mkdir(exist_ok=False);shutil.copyfile(__file__,N/'executed.py');(N/'inputs').mkdir()
common=(R/'lab/common_fields_source.py').read_text();exec(common[common.index('reader='):common.index('libs=N/')])
scn=json.loads((BASE/'snapshot.json').read_text())['scn'];started=False
try:
    assert run('state',['docker','inspect',S,'--format','{{.Config.Labels.owner}}|{{.State.Status}}']).strip()=='OracleRedoKernel|exited'
    run('start',['docker','start',S]);started=True
    for attempt in range(30):
        try:
            out=sql('ready-'+str(attempt),"select 'READY|'||open_mode from v$pdbs where name='ORKPDB';")
            if 'READY|READ WRITE' in out:break
        except AssertionError:
            if attempt==29:raise
        time.sleep(3)
    truth=sql('truth',"alter session set container=ORKPDB;\nselect 'ROW|'||ID||'|'||TXT||'|'||NOTE from ORKLAB.T_D2_PARTIAL_20261011 order by ID;")
    rows=[l.split('|')[1:] for l in truth.splitlines() if l.startswith('ROW|')]
    assert len(rows)==21 and [int(r[0]) for r in rows]==[1]+list(range(200,220))
    assert rows[0][1:]==['seed','anchor'] and all(r[1:]==['kept','committed'] for r in rows[1:])
    (N/'truth.json').write_text(json.dumps(rows,indent=2))
    first=int(next(l.split('|')[1] for l in (BASE/'pre.stdout').read_text().splitlines() if l.startswith('CURRENT|')))
    out=sql('close',"select 'CURRENT|'||sequence# from v$log where status='CURRENT';\nalter system archive log current;")
    last=int(next(l.split('|')[1] for l in out.splitlines() if l.startswith('CURRENT|')));archives=[]
    for seq in range(first,last+1):
        out=sql('archive-'+str(seq),f"select 'PATH|'||name from v$archived_log where sequence#={seq} and archived='YES' and deleted='NO' and name is not null order by recid;")
        paths=set(l.split('|')[1] for l in out.splitlines() if l.startswith('PATH|'));assert len(paths)==1;path=paths.pop();local=N/'inputs'/f'{seq}.raw'
        run('copy-'+str(seq),['docker','cp',S+':'+path,local]);assert run('sha-'+str(seq),['docker','exec',S,'sha256sum',path]).split()[0]==sha(local)
        archives.append(dict(sequence=seq,path=str(local),sourcePath=path,sha256=sha(local),bytes=local.stat().st_size))
        out=sql('native-'+str(seq),f"alter session set tracefile_identifier='ORK_ARRAY_PARTIAL';\nalter system dump logfile '{path}' scn min {scn};\nselect 'TRACE|'||value from v$diag_info where name='Default Trace File';")
        trace=next(l.split('|',1)[1] for l in out.splitlines() if l.startswith('TRACE|'));run('native-copy-'+str(seq),['docker','cp',S+':'+trace,N/f'{seq}.trc'])
    (N/'archives.json').write_text(json.dumps(archives,indent=2));print('ARRAY_EXISTING_SOURCE_FROZEN',flush=True)
finally:
    if started:run('source-stop',['docker','stop','-t','60',S])
