"""Read-only native diagnostic for one frozen record; no source business DML."""
from pathlib import Path
import hashlib,json,re,subprocess,time,sys
R=Path(__file__).resolve().parent.parent;N=R/'lab/out/lob-unified-20261012'/sys.argv[1];S='oraredo-19c-lab-20261004'
N.mkdir(exist_ok=False)
common=(R/'lab/common_fields_source.py').read_text();exec(common[common.index('reader='):common.index('libs=N/')])
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
    out=sql('archive',"select 'PATH|'||name from v$archived_log where sequence#=121 and archived='YES' and deleted='NO' and name is not null;")
    paths=set(l.split('|')[1] for l in out.splitlines() if l.startswith('PATH|'));assert len(paths)==1;path=paths.pop()
    assert run('sha',['docker','exec',S,'sha256sum',path]).split()[0]=='7f018972abff0d55189c92e3062d1dc0742e4b60308700321d4f190449a59b91'
    out=sql('dump',f"alter session set tracefile_identifier='ORK_FUTURE_ZERO';\nalter system dump logfile '{path}' scn min 3287131 scn max 3287131;\nselect 'TRACE|'||value from v$diag_info where name='Default Trace File';")
    trace=next(l.split('|',1)[1] for l in out.splitlines() if l.startswith('TRACE|'))
    run('copy',['docker','cp',S+':'+trace,N/'record.trc'])
finally:
    if started:run('source-stop',['docker','stop','-t','60',S])
