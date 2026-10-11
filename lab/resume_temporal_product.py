"""Resume the already frozen time window; no source DDL/DML or acquisition replay."""
from pathlib import Path
import hashlib,json,os,shutil,subprocess,sys,time,zipfile
R=Path(__file__).resolve().parent.parent;BASE=R/'lab/out/array-temporal-product-20261011'
BUILD=BASE/'build-r2';B=BASE/'runtime-r1';N=BASE/'runtime-r2'
S='oraredo-19c-lab-20261004';H='ork-product-runtime';PORT=37301
assert sys.argv[1:]==['--execute'];N.mkdir(exist_ok=False);shutil.copyfile(__file__,N/'executed.py')
sha=lambda p:hashlib.sha256(Path(p).read_bytes()).hexdigest()
candidate=json.loads((BUILD/'candidate.json').read_text());assert sha(BUILD/'application.jar')==candidate['applicationSha256']
shutil.copyfile(BUILD/'application.jar',B/'application-r2.jar')
props=dict(line.split('=',1) for line in (B/'T-runtime.secret.env').read_text().splitlines())
passwords=[v.encode() for k,v in props.items() if 'PASSWORD' in k]
(N/'libs').mkdir()
with zipfile.ZipFile(B/'application-r2.jar') as archive:
    for name in archive.namelist():
        if name.startswith('BOOT-INF/lib/') and name.endswith('.jar'):(N/'libs'/Path(name).name).write_bytes(archive.read(name))
for name,version in [('kernel',candidate['kernelVersion']),('adapter',candidate['adapterVersion'])]:
    artifact='oracle-redo-kernel' if name=='kernel' else 'streamhouse-engine-oracle'
    assert sha(N/'libs'/(artifact+'-'+version+'.jar'))==candidate[name+'Sha256']
cp=os.pathsep.join([str(B/'classes'),str(N/'libs/*')])
common=(R/'lab/common_fields_product.py').read_text();exec(common[common.index('def run('):common.index("run('compile'")])
kind='T';U='ORKATTCTL_20261011';SOURCE='oracle-array-time-t';prefix='ATHT20261011'
owner=props['SH_ORACLE_BOUNDED_TARGET_OWNER'];T=owner+'.'+prefix;TARGET=SOURCE+'-target'
table='T_D4_TPROD_20261011';window='array-time-t';count=3;ACQ=102575967029239808
baseline=B/'snapshots'/f'{ACQ}.snapshot';captures=[B/'inputs/115.raw'];proofs=[]
assert (B/'T-source-action.rc').read_text().strip()=='0'
assert not (B/'T-ledger.rc').exists() and not (B/'T-enroll.rc').exists()
assert sha(baseline)=='b5fd3acc8debc0921672b12edd6af8e1b9b1913c6c84b6f765f6304639ac334e'
started=False;helper=False
try:
    assert H not in run('helpers',['docker','ps','-a','--format','{{.Names}}']).splitlines()
    assert run('state',['docker','inspect',S,'--format','{{.Config.Labels.owner}}|{{.State.Status}}']).strip()=='OracleRedoKernel|exited'
    run('start',['docker','start',S]);started=True
    for attempt in range(30):
        try:
            if 'READY|READ WRITE' in sql('ready-'+str(attempt),"select 'READY|'||open_mode from v$pdbs where name='ORKPDB';"):break
        except AssertionError:
            if attempt==29:raise
        time.sleep(3)
    else:raise AssertionError('PDB_NOT_READY')
    pre=sql('reuse-guard',f"alter session set container=ORKPDB;\nselect 'ROWS|'||count(*) from {T}_ROWS;\nselect 'IDENT|'||count(*) from dba_tables where owner='{owner}' and table_name='{prefix}_IDENT';\nselect 'BINDING|'||count(*) from {U}.SH_ORACLE_INPUT_BINDING;\nselect 'WINDOW|'||count(*) from {U}.SH_CAP_WINDOW;\nselect 'LEDGER|'||count(*) from {U}.SH_EVENT_LEDGER;")
    assert all(v in pre for v in ['ROWS|3','IDENT|0','BINDING|0','WINDOW|0','LEDGER|0'])
    run('helper-start',['docker','run','-d','--name',H,'--label','owner=OracleRedoKernel','--label','scope=oracle-array-time-product','--user','0','--network','container:'+S,'--workdir','/evidence','--env-file',B/'T-runtime.secret.env','--mount','type=bind,source='+str(B)+',target=/evidence','--entrypoint','java','flink:1.18-java17','-Xmx1536m','-jar','/evidence/application-r2.jar']);helper=True
    for attempt in range(40):
        try:http('health-'+str(attempt),'/system/info');break
        except (AssertionError,ValueError):
            if attempt==39:raise
            time.sleep(3)
    acquired=http('verified',f'/oracle/acquisitions/{ACQ}');assert acquired['snapshotSha256']==sha(baseline)
    prepared=json.loads(run('preparation',['D:/work/Java/jdk-17.0.7/bin/java.exe','-Xmx1536m','-cp',cp,'ArrayTemporalProductProbe',baseline,sha(baseline),count,*captures]));e=prepared['evidence']
    assert len(prepared['events'])==count and all(x['operation']=='UPDATE' for x in prepared['events'])
    # The same enrollment/publication/proof path as the first candidate, without its source actions.
    original=(R/'lab/array_temporal_product.py').read_text()
    body=original[original.index("        identity="):original.index("        run(kind+'-logs'")]
    body='\n'.join(line[8:] for line in body.splitlines()).replace("ack=N/'acks'", "ack=B/'acks'")
    exec(body)
    (N/'proof.json').write_text(json.dumps(dict(candidate=candidate,scenarios=proofs,sourceDml=0,oldSourceDmlReplayed=0,sqlUsedAsEventInput=False),indent=2))
    print('TEMPORAL_HTTP_TARGET_CPACK_PASS',flush=True)
finally:
    try:
        if helper:run('logs',['docker','logs',H]);run('stop',['docker','stop','-t','20',H]);run('remove',['docker','rm',H])
    finally:
        if started:run('source-stop',['docker','stop','-t','60',S])
