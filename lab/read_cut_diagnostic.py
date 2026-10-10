"""Reproduce the existing refusal without source actions; retain the full nested cause."""
from pathlib import Path
import sys
R=Path(__file__).resolve().parent.parent
sys.argv=[str(__file__),'diagnostic-r2']
exec((R/'lab/read_cut_runtime.py').read_text().split('try:\n    assert (N/')[0])
directory=N/'diagnostic-classes';directory.mkdir()
libs=N/'diagnostic/libs'
cp=os.pathsep.join([str(P/'server/streamhouse-app/target/classes'),*[str(p) for p in libs.glob('*.jar')]])
run('compile',['D:/work/Java/jdk-17.0.7/bin/javac.exe','-cp',cp,'-d',directory,R/'lab/ReadCutRuntimeDiagnostic.java'])
try:
    assert HELPER not in run('containers',['docker','ps','-a','--format','{{.Names}}']).splitlines()
    assert run('source-state',['docker','inspect',S,'--format','{{.Config.Labels.owner}}|{{.State.Status}}']).strip()=='OracleRedoKernel|exited'
    run('source-start',['docker','start',S]);started=True
    for i in range(30):
        try:
            if 'READY|1' in sql('ready-'+str(i),"select 'READY|'||count(*) from ORKLAB.T_D2_BLOCKS_20261008 where ID=0 and NOTE='online-next';"):break
        except AssertionError:
            if i==29:raise
        time.sleep(3)
    run('start',['docker','run','-d','--name',HELPER,'--label','owner=OracleRedoKernel','--label','scope=oracle-read-cut-diagnostic','--user','0','--network','container:'+S,'--env-file',N/'runtime.secret.env',
        '--mount','type=bind,source='+str(O)+',target=/evidence,readonly','--mount','type=bind,source='+str(N)+',target=/read-cut',
        '--mount','type=bind,source='+str(N/'central-raw')+',target=/evidence/raw-ready','--mount','type=bind,source='+str(N/'acks')+',target=/evidence/acks',
        '--mount','type=bind,source='+str(N/'prefix-state')+',target=/evidence/prefix-state','--mount','type=bind,source='+str(N/'failures')+',target=/evidence/failures',
        '--entrypoint','java','flink:1.18-java17','-Xmx1536m','-Dloader.path=/read-cut/diagnostic-classes','-Dloader.main=ReadCutRuntimeDiagnostic','-cp','/read-cut/application.jar','org.springframework.boot.loader.launch.PropertiesLauncher']);helper=True
    rc=run('wait',['docker','wait',HELPER]).strip();assert rc=='1',rc
finally:
    try:
        if helper:stop_app('cleanup')
    finally:
        if started:run('source-stop',['docker','stop','-t','60',S])
