"""Read-only native diagnostic for frozen ARRAY archive107; no fixture DML or archive switch."""
from pathlib import Path
import hashlib,json,re,shutil,subprocess,sys,time
R=Path(__file__).resolve().parent.parent
N=R/'lab/out/array-normal-20261010/native-r1'
S='oraredo-19c-lab-20261004'
assert sys.argv[1:]==['--execute'];N.mkdir(exist_ok=False);shutil.copyfile(__file__,N/'executed.py')
common=(R/'lab/common_fields_source.py').read_text();exec(common[common.index('reader='):common.index('libs=N/')])
started=False
try:
    assert run('state',['docker','inspect',S,'--format','{{.Config.Labels.owner}}|{{.State.Status}}']).strip()=='OracleRedoKernel|exited'
    run('start',['docker','start',S]);started=True
    for attempt in range(30):
        try:
            out=sql('ready-'+str(attempt),"select 'READY|'||open_mode from v$database;")
            if 'READY|READ WRITE' in out:break
        except AssertionError:
            if attempt==29:raise
        time.sleep(3)
    assert 'READY|READ WRITE' in out
    out=sql('dump',"alter session set tracefile_identifier='ORK_ARRAY_NORMAL';\nalter system dump logfile '/opt/oracle/product/19c/dbhome_1/dbs/arch1_107_1245693159.dbf' scn min 3219777;\nselect 'TRACE|'||value from v$diag_info where name='Default Trace File';")
    paths=[l.split('|',1)[1] for l in out.splitlines() if l.startswith('TRACE|')];assert len(paths)==1
    run('copy',['docker','cp',S+':'+paths[0],N/'array.trc'])
    print('BOUNDED_NATIVE_DIAGNOSTIC_FROZEN',flush=True)
finally:
    if started:run('stop',['docker','stop','-t','60',S])
