"""Read-only same-candidate acquisition failure diagnosis; no fixture replay or publication."""
from pathlib import Path
import hashlib,json,os,shutil,subprocess,sys,time
R=Path(__file__).resolve().parent.parent;BASE=R/'lab/out/array-normal-20261010';N=BASE/'acquisition-diagnostic-r1'
S='oraredo-19c-lab-20261004';H='ork-product-runtime';PORT=37301
assert sys.argv[1:]==['--execute'];assert (BASE/'source/source-stop.rc').read_text().strip()=='0'
assert not (BASE/'source/array-insert.started').exists();N.mkdir(exist_ok=False);shutil.copyfile(__file__,N/'executed.py')
reader=(R/'lab/out/dictionary-baseline-candidate-20261006/source.secret').read_text().splitlines();passwords=[reader[1].encode()]
common=(R/'lab/common_fields_product.py').read_text();exec(common[common.index('def run('):common.index("run('compile'")])
mixed=R/'lab/out/common-fields-20261010';classes=N/'classes';classes.mkdir()
cp=os.pathsep.join(map(str,[mixed/'candidate-r15/kernel.jar',mixed/'candidate-r15/adapter.jar',mixed/'source/libs/*']))
run('compile',['D:/work/Java/jdk-17.0.7/bin/javac.exe','-cp',cp,'-d',classes,R/'lab/OracleSnapshotAcquisitionProbe.java'])
shutil.copyfile(R/'lab/out/dictionary-baseline-candidate-20261006/source.secret',N/'source.secret');started=False
try:
    assert H not in run('helpers',['docker','ps','-a','--format','{{.Names}}']).splitlines()
    assert run('state',['docker','inspect',S,'--format','{{.Config.Labels.owner}}|{{.State.Status}}']).strip()=='OracleRedoKernel|exited'
    run('start',['docker','start',S]);started=True
    for attempt in range(30):
        try:
            if 'READY|1' in sql('ready-'+str(attempt),"alter session set container=ORKPDB;\nselect 'READY|'||count(*) from ORKLAB.T_D2_ARRAY_20261010;"):break
        except AssertionError:
            if attempt==29:raise
        time.sleep(3)
    run('acquire',['docker','run','--rm','--name',H,'--label','owner=OracleRedoKernel','--network','container:'+S,'--mount','type=bind,source='+str(mixed)+',target=/candidate,readonly','--mount','type=bind,source='+str(N)+',target=/evidence','--entrypoint','java','flink:1.18-java17','-cp','/candidate/candidate-r15/kernel.jar:/candidate/candidate-r15/adapter.jar:/candidate/source/libs/*:/evidence/classes','OracleSnapshotAcquisitionProbe','/evidence/source.secret','array-read-only','ORKLAB','T_D2_ARRAY_20261010','PK_D2_ARRAY_20261010','history-normal','/evidence/diagnostic.snapshot'])
finally:
    if started:run('source-stop',['docker','stop','-t','60',S])
