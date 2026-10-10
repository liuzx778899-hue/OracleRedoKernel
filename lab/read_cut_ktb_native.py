"""One bounded owned native dump for the actual new KTB refusal. Diagnostic only; no DML or parser input."""
from pathlib import Path
import sys
R=Path(__file__).resolve().parent.parent
sys.argv=[str(__file__),'ktb-native-r1']
exec((R/'lab/read_cut_runtime.py').read_text().split('try:\n    assert (N/')[0])
try:
    assert run('source-state',['docker','inspect',S,'--format','{{.Config.Labels.owner}}|{{.State.Status}}']).strip()=='OracleRedoKernel|exited'
    run('source-start',['docker','start',S]);started=True
    for i in range(30):
        try:
            if 'READY|1' in sql('ready-'+str(i),"select 'READY|'||count(*) from ORKLAB.T_D2_BLOCKS_20261008 where ID=1 and NOTE='readcut-next-1';"):break
        except AssertionError:
            if i==29:raise
        time.sleep(3)
    source=sql('identify',"alter session set container=CDB$ROOT;\nselect 'MEMBER102|'||member from v$logfile f join v$log l on f.group#=l.group# where l.sequence#=102;")
    paths=[line.split('|',1)[1] for line in source.splitlines() if line.startswith('MEMBER102|')];assert len(paths)==1
    path=paths[0];assert path.startswith('/opt/oracle/oradata/ORK19/') and path.endswith('.log') and "'" not in path
    out=sql('dump',f"alter session set container=CDB$ROOT;\nalter system dump logfile '{path}' scn min 3095856 scn max 3095856;\nselect 'TRACE|'||value from v$diag_info where name='Default Trace File';")
    traces=[line.split('|',1)[1] for line in out.splitlines() if line.startswith('TRACE|')];assert len(traces)==1 and traces[0].startswith('/opt/oracle/')
    run('trace',['docker','exec',S,'cat',traces[0]])
finally:
    if started:run('source-stop',['docker','stop','-t','60',S])
