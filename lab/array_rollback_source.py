"""One owned after-acquisition ARRAY transaction; authenticated acquisition and original failures preserved."""
from pathlib import Path
import hashlib,json,os,shutil,subprocess,sys,time
R=Path(__file__).resolve().parent.parent;BASE=R/'lab/out/array-rollback-20261011'
resume=sys.argv[1:]==['--resume-before-array'];N=BASE/('source/resume-r1' if resume else 'source')
S='oraredo-19c-lab-20261004';H='ork-product-runtime';PORT=37301
assert resume or sys.argv[1:]==['--execute']
if resume:
    assert (BASE/'source/source-stop.rc').read_text().strip()=='0'
    assert (BASE/'acquisition-diagnostic-r1/source-stop.rc').read_text().strip()=='0'
    assert not (BASE/'source/array-insert.started').exists()
N.mkdir(parents=True,exist_ok=False);shutil.copyfile(__file__,N/'executed.py')
mixed=R/'lab/out/common-fields-20261010';candidate=json.loads((mixed/'product-build-r3/candidate.json').read_text())
props=dict(l.split('=',1) for l in (mixed/'product-runtime-r3/B-runtime.secret.env').read_text().splitlines())
reader=(R/'lab/out/dictionary-baseline-candidate-20261006/source.secret').read_text().splitlines()
passwords=[v.encode() for k,v in props.items() if 'PASSWORD' in k]+[reader[1].encode()]
sha=lambda p:hashlib.sha256(Path(p).read_bytes()).hexdigest()
common=(R/'lab/common_fields_product.py').read_text()
exec(common[common.index('def run('):common.index("run('compile'")])
app=mixed/'product-build-r3/application.jar';assert sha(app)==candidate['applicationSha256'];shutil.copyfile(app,N/'application.jar')
for folder in ['snapshots','acks','prefix-state','failures','inputs']:(N/folder).mkdir()
table='T_D2_ARRAY_RB_20261011';source='oracle-array-rollback-20261011';started=False;helper=False
try:
    assert H not in run('helpers',['docker','ps','-a','--format','{{.Names}}']).splitlines()
    assert run('state',['docker','inspect',S,'--format','{{.Config.Labels.owner}}|{{.State.Status}}']).strip()=='OracleRedoKernel|exited'
    run('start',['docker','start',S]);started=True
    for attempt in range(30):
        try:
            pre=sql('ready-'+str(attempt),"select 'LOGGING|'||supplemental_log_data_min||'|'||supplemental_log_data_all from v$database;\nselect 'CURRENT|'||sequence# from v$log where status='CURRENT';\nalter session set container=ORKPDB;\nselect 'FREE|'||count(*) from dba_tables where owner='ORKLAB' and table_name='T_D2_ARRAY_RB_20261011';")
            if 'LOGGING|NO|NO' in pre:break
        except AssertionError:
            if attempt==29:raise
        time.sleep(3)
    assert ('FREE|1' if resume else 'FREE|0') in pre;first=int(next(l.split('|')[1] for l in pre.splitlines() if l.startswith('CURRENT|')))
    if not resume:
        sql('fixture',f"alter session set container=ORKPDB;\ncreate table ORKLAB.{table}(ID number not null,TXT varchar2(128),NOTE varchar2(128),constraint PK_D2_ARRAY_RB_20261011 primary key(ID)) pctfree 10;\ninsert into ORKLAB.{table} values(1,'array-seed','anchor');\ncommit;")
    else:
        assert 'SOURCE_SEED|1' in sql('seed-guard',f"alter session set container=ORKPDB;\nselect 'SOURCE_SEED|'||count(*) from ORKLAB.{table} where ID=1 and TXT='array-seed' and NOTE='anchor';\nselect 'TOTAL|'||count(*) from ORKLAB.{table};")
        assert 'TOTAL|1' in (N/'seed-guard.stdout').read_text()
    assert reader[0].replace('_','').isalnum()
    sql('reader-grant',f"alter session set container=ORKPDB;\ngrant select,flashback on ORKLAB.{table} to {reader[0]};")
    props.update(SH_PIPELINE_AUTO_CONSUME_ENABLED='false',SH_PIPELINE_AUTO_CONSUME_SOURCE_IDS=source,SH_ORACLE_ACQUISITION_SOURCE_ID=source,SH_ORACLE_ACQUISITION_AUTHORITY='owned-array-normal-acquisition',SH_ORACLE_ACQUISITION_TABLE=table,SH_ORACLE_ACQUISITION_INDEX='PK_D2_ARRAY_RB_20261011',SH_ORACLE_ACQUISITION_DIRECTORY='/evidence/snapshots',SH_ORACLE_RAW_DISCOVERY_ENABLED='false',SH_ORACLE_RAW_PLAN_ENABLED='false')
    (N/'runtime.secret.env').write_text(''.join(k+'='+v+'\n' for k,v in props.items()))
    run('helper-start',['docker','run','-d','--name',H,'--label','owner=OracleRedoKernel','--label','scope=oracle-array-acquisition','--user','0','--network','container:'+S,'--workdir','/evidence','--env-file',N/'runtime.secret.env','--mount','type=bind,source='+str(N)+',target=/evidence','--entrypoint','java','flink:1.18-java17','-Xmx1024m','-jar','/evidence/application.jar']);helper=True
    for attempt in range(40):
        try:http('health-'+str(attempt),'/system/info');break
        except (AssertionError,ValueError):
            if run('health-state-'+str(attempt),['docker','inspect',H,'--format','{{.State.Status}}']).strip()=='exited':raise
            if attempt==39:raise
            time.sleep(3)
    acquired=http('acquire','/oracle/acquisitions/historical',dict(sourceId=source))
    verified=http('verified','/oracle/acquisitions/'+str(acquired['id']))
    assert {k:v for k,v in verified.items() if k!='registeredAt'}=={k:v for k,v in acquired.items() if k!='registeredAt'}
    snapshot=N/'snapshots'/(str(acquired['id'])+'.snapshot');assert sha(snapshot)==acquired['snapshotSha256']
    (N/'acquisition.json').write_text(json.dumps(acquired,indent=2))
    (N/'array-insert.started').write_text('one new INSERT SELECT after authenticated acquisition; never replay\n')
    sql('array-insert',f"alter session set container=ORKPDB;\nbegin insert into ORKLAB.{table} select 99+level,'array-full-rollback', 'rolled-back' from dual connect by level<=20;if sql%rowcount<>20 then raise_application_error(-20001,'ROW_COUNT');end if;end;\n/\nrollback;\nbegin insert into ORKLAB.{table} select 199+level,'array-kept','committed' from dual connect by level<=20;savepoint keep_array;insert into ORKLAB.{table} select 299+level,'array-savepoint-rollback','rolled-back' from dual connect by level<=20;rollback to keep_array;end;\n/\ncommit;")
    truth=sql('truth',f"alter session set container=ORKPDB;\nselect 'ROW|'||ID||'|'||TXT||'|'||nvl(NOTE,'<NULL>') from ORKLAB.{table} order by ID;")
    rows=[l.split('|')[1:] for l in truth.splitlines() if l.startswith('ROW|')];assert len(rows)==21
    (N/'truth.json').write_text(json.dumps(rows,indent=2))
    out=sql('close',"select 'CURRENT|'||sequence# from v$log where status='CURRENT';\nalter system archive log current;")
    last=int(next(l.split('|')[1] for l in out.splitlines() if l.startswith('CURRENT|')));archives=[]
    for seq in range(first,last+1):
        paths=sql('archive-'+str(seq),f"select 'PATH|'||name from v$archived_log where sequence#={seq} and archived='YES' and deleted='NO' and name is not null order by recid;")
        matches=set(l.split('|')[1] for l in paths.splitlines() if l.startswith('PATH|'));assert len(matches)==1;path=matches.pop();local=N/'inputs'/f'{seq}.raw'
        run('copy-'+str(seq),['docker','cp',S+':'+path,local]);assert run('sha-'+str(seq),['docker','exec',S,'sha256sum',path]).split()[0]==sha(local)
        archives.append(dict(sequence=seq,path=str(local),sourcePath=path,sha256=sha(local),bytes=local.stat().st_size))
    (N/'archives.json').write_text(json.dumps(archives,indent=2));print('ARRAY_ROLLBACK_SOURCE_FROZEN',flush=True)
finally:
    try:
        if helper:
            run('helper-logs',['docker','logs',H]);run('helper-stop',['docker','stop','-t','20',H]);run('helper-remove',['docker','rm',H])
    finally:
        if started:run('source-stop',['docker','stop','-t','60',S])
