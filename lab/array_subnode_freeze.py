"""Read-only reconciliation/freezing after committed DML and a console-marker failure. Never replay DML."""
from pathlib import Path
import hashlib,json,re,subprocess,time
R=Path(__file__).resolve().parent.parent;N=R/'lab/out/array-subnode-20261012/source';S='oraredo-19c-lab-20261004'
assert (N/'actions.started').exists() and 'SUBNODE_COMMITTED' in (N/'actions.stdout').read_text() and not (N/'archives.json').exists()
common=(R/'lab/common_fields_source.py').read_text();exec(common[common.index('reader='):common.index('libs=N/')])
initial_run=run
def run(label,args,data=None):return initial_run(label+'-r3',args,data)
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
    truth=sql('truth',"alter session set container=ORKPDB;\nselect 'ROW|'||ID||'|'||TXT||'|'||NOTE from ORKLAB.T_D2_SUBNODE_20261012 order by ID;")
    rows=[l.split('|')[1:] for l in truth.splitlines() if l.startswith('ROW|')]
    assert rows==[['1','seed','anchor']]+[[str(i),'kept-subnode','partial-array'] for i in range(200,220) if i not in (210,216)]
    (N/'truth.json').write_text(json.dumps(rows,indent=2))
    first=int(next(l.split('|')[1] for l in (N/'pre.stdout').read_text().splitlines() if l.startswith('CURRENT|')))
    minimum=int(next(l.split('|')[1] for l in (N/'baseline.snapshot').read_text().splitlines() if l.startswith('SNAPSHOT_SCN|')))
    closed=sql('close',"select 'CURRENT|'||sequence# from v$log where status='CURRENT';\nalter system archive log current;")
    last=int(next(l.split('|')[1] for l in closed.splitlines() if l.startswith('CURRENT|')));archives=[]
    for sequence in range(first,last+1):
        out=sql('archive-'+str(sequence),f"select 'PATH|'||name from v$archived_log where sequence#={sequence} and archived='YES' and deleted='NO' and name is not null;")
        paths=set(l.split('|')[1] for l in out.splitlines() if l.startswith('PATH|'));assert len(paths)==1;path=paths.pop();local=N/'inputs'/f'{sequence}.raw'
        assert not local.exists();run('copy-'+str(sequence),['docker','cp',S+':'+path,local]);assert run('sha-'+str(sequence),['docker','exec',S,'sha256sum',path]).split()[0]==sha(local)
        archives.append(dict(sequence=sequence,sha256=sha(local),bytes=local.stat().st_size))
        out=sql('native-'+str(sequence),f"alter session set tracefile_identifier='ORK_ARRAY_SUBNODE';\nalter system dump logfile '{path}' scn min {minimum};\nselect 'TRACE|'||value from v$diag_info where name='Default Trace File';")
        trace=next(l.split('|',1)[1] for l in out.splitlines() if l.startswith('TRACE|'));run('native-copy-'+str(sequence),['docker','cp',S+':'+trace,N/f'{sequence}.trc'])
    (N/'archives.json').write_text(json.dumps(archives,indent=2));print('COMMITTED_SUBNODE_FROZEN|pending_window_not_created=true',flush=True)
finally:
    if started:run('source-stop',['docker','stop','-t','60',S])
