"""Read-only bounded dump of frozen maintenance end markers; no source DML or switch."""
from pathlib import Path
import hashlib,json,re,shutil,subprocess,sys,time
R=Path(__file__).resolve().parent.parent
N=R/'lab/out/common-fields-20261010/native-maintenance'
S='oraredo-19c-lab-20261004'
assert sys.argv[1:] in [['--execute'],['--resume-prestart'],['--control-profile']]
if sys.argv[1:]==['--resume-prestart']:
    assert (N/'executed.py').exists() and not (N/'start.rc').exists()
    N=N/'resume-r2'
if sys.argv[1:]==['--control-profile']:
    assert (N/'resume-r2/stop.rc').read_text().strip()=='0'
    N=N/'control-r3'
N.mkdir(exist_ok=False);shutil.copyfile(__file__,N/'executed.py')
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
    low,high=(3175459,3175459) if sys.argv[1:]==['--control-profile'] else (3175472,3175593)
    out=sql('dump',f"alter session set tracefile_identifier='ORK_COMMON_FIELDS_MAINT';\nalter system dump logfile '/opt/oracle/product/19c/dbhome_1/dbs/arch1_104_1245693159.dbf' scn min {low} scn max {high};\nselect 'TRACE|'||value from v$diag_info where name='Default Trace File';")
    paths=[l.split('|',1)[1] for l in out.splitlines() if l.startswith('TRACE|')];assert len(paths)==1
    run('copy',['docker','cp',S+':'+paths[0],N/'maintenance.trc'])
    print('BOUNDED_MAINTENANCE_DIAGNOSTIC_FROZEN',flush=True)
finally:
    if started:run('stop',['docker','stop','-t','60',S])
