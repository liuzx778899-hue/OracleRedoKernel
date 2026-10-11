"""One owned LOB ADD/DROP/TRUNCATE fixture; freeze stages separately and never replay actions."""
from pathlib import Path
import hashlib,json,re,shutil,subprocess,sys,time
R=Path(__file__).resolve().parent.parent;N=R/'lab/out/lob-history-20261011/source'
S='oraredo-19c-lab-20261004';H='ork-product-runtime'
assert sys.argv[1:]==['--execute'];N.mkdir(parents=True,exist_ok=False);shutil.copyfile(__file__,N/'executed.py')
common=(R/'lab/common_fields_source.py').read_text();exec(common[common.index('reader='):common.index('libs=N/')])
shutil.copytree(R/'lab/out/array-update-rollback-20261011/source/libs',N/'libs');(N/'classes').mkdir();(N/'inputs').mkdir()
shutil.copyfile(R/'lab/out/dictionary-baseline-candidate-20261006/source.secret',N/'source.secret')
run('compile',['D:/work/Java/jdk-17.0.7/bin/javac.exe','-cp',str(N/'libs/*'),'-d',N/'classes',R/'lab/OracleSnapshotAcquisitionProbe.java'])
tables=[('T_D4_HLB_20261011','PK_D4_HLB_20261011','BASICFILE'),('T_D4_HLS_20261011','PK_D4_HLS_20261011','SECUREFILE')]
started=False;archives=[]
def acquire(label,table,index):
    run(label,['docker','run','--rm','--name',H,'--label','owner=OracleRedoKernel','--network','container:'+S,
        '--mount','type=bind,source='+str(N)+',target=/evidence','--entrypoint','java','flink:1.18-java17','-cp','/evidence/classes:/evidence/libs/*',
        'OracleSnapshotAcquisitionProbe','/evidence/source.secret',label,'ORKLAB',table,index,'history-normal','/evidence/'+label+'.snapshot'])
def freeze(stage,first,minimum):
    closed=sql(stage+'-close',"select 'CURRENT|'||sequence# from v$log where status='CURRENT';\nalter system archive log current;")
    last=int(next(l.split('|')[1] for l in closed.splitlines() if l.startswith('CURRENT|')))
    for seq in range(first,last+1):
        out=sql('archive-'+str(seq),f"select 'PATH|'||name from v$archived_log where sequence#={seq} and archived='YES' and deleted='NO' and name is not null order by recid;")
        paths=set(l.split('|')[1] for l in out.splitlines() if l.startswith('PATH|'));assert len(paths)==1
        path=paths.pop();local=N/'inputs'/f'{seq}.raw';run('copy-'+str(seq),['docker','cp',S+':'+path,local])
        assert run('sha-'+str(seq),['docker','exec',S,'sha256sum',path]).split()[0]==sha(local)
        archives.append(dict(stage=stage,sequence=seq,sha256=sha(local),bytes=local.stat().st_size))
        trace=sql('native-'+str(seq),f"alter session set tracefile_identifier='ORK_LOB_HISTORY';\nalter system dump logfile '{path}' scn min {minimum};\nselect 'TRACE|'||value from v$diag_info where name='Default Trace File';")
        tracepath=next(l.split('|',1)[1] for l in trace.splitlines() if l.startswith('TRACE|'));run('native-copy-'+str(seq),['docker','cp',S+':'+tracepath,N/f'{seq}.trc'])
        (N/'archives.json').write_text(json.dumps(archives,indent=2))
    return last+1
try:
    assert run('state',['docker','inspect',S,'--format','{{.Config.Labels.owner}}|{{.State.Status}}']).strip()=='OracleRedoKernel|exited'
    assert H not in run('helpers',['docker','ps','-a','--format','{{.Names}}']).splitlines()
    run('start',['docker','start',S]);started=True
    for attempt in range(30):
        try:
            if 'READY|READ WRITE' in sql('ready-'+str(attempt),"select 'READY|'||open_mode from v$pdbs where name='ORKPDB';"):break
        except AssertionError:
            if attempt==29:raise
        time.sleep(3)
    else:raise AssertionError('PDB_NOT_READY')
    pre=sql('pre',"select 'CURRENT|'||sequence# from v$log where status='CURRENT';\nselect 'LOGGING|'||supplemental_log_data_min||'|'||supplemental_log_data_all from v$database;\nalter session set container=ORKPDB;\nselect 'FREE|'||count(*) from dba_tables where owner='ORKLAB' and table_name in ('T_D4_HLB_20261011','T_D4_HLS_20261011');")
    assert 'FREE|0' in pre and 'LOGGING|NO|NO' in pre
    first=int(next(l.split('|')[1] for l in pre.splitlines() if l.startswith('CURRENT|')))
    snapshots={}
    for table,index,storage in tables:
        sql(table+'-fixture',f"alter session set container=ORKPDB;\ncreate table ORKLAB.{table}(ID number not null,TXT varchar2(128),B blob,C clob,constraint {index} primary key(ID)) lob(B) store as {storage}(enable storage in row nocache logging) lob(C) store as {storage}(enable storage in row nocache logging);\ninsert into ORKLAB.{table} values(1,'seed',hextoraw('0080FF'),to_clob('seed-text'));\ncommit;\ngrant select,flashback on ORKLAB.{table} to {reader[0]};")
        label=table+'-baseline';acquire(label,table,index)
        snapshots[table]=dict(path=label+'.snapshot',sha256=sha(N/(label+'.snapshot')))
    (N/'snapshots.json').write_text(json.dumps(snapshots,indent=2))
    minimum=min(int(next(l.split('|')[1] for l in (N/v['path']).read_text().splitlines() if l.startswith('SNAPSHOT_SCN|'))) for v in snapshots.values())
    (N/'actions.started').write_text('one new ADD/DROP/TRUNCATE experiment; never replay\n')
    for table,index,storage in tables:
        sql(table+'-add',f"alter session set container=ORKPDB;\nalter table ORKLAB.{table} add EXTRA varchar2(32);\ndeclare b blob;c clob;begin update ORKLAB.{table} set TXT='added',EXTRA='extra-kept',B=empty_blob(),C=empty_clob() where ID=1 returning B,C into b,c;if sql%rowcount<>1 then raise_application_error(-20001,'ADD_ROW_COUNT');end if;dbms_lob.writeappend(b,10000,hextoraw(rpad('00FF80',20000,'00FF80')));dbms_lob.writeappend(c,5000,rpad('history-text-',5000,'history-text-'));end;\n/\ncommit;")
        acquire(table+'-after-add',table,index)
    first=freeze('add',first,minimum)
    for table,index,storage in tables:
        sql(table+'-drop',f"alter session set container=ORKPDB;\nalter table ORKLAB.{table} drop column EXTRA;\nupdate ORKLAB.{table} set TXT='after-drop' where ID=1;\ncommit;\nselect 'ROW|'||ID||'|'||TXT||'|'||dbms_lob.getlength(B)||'|'||dbms_lob.getlength(C) from ORKLAB.{table};")
    first=freeze('drop',first,minimum)
    for table,index,storage in tables:
        sql(table+'-truncate',f"alter session set container=ORKPDB;\ntruncate table ORKLAB.{table};\ninsert into ORKLAB.{table}(ID,TXT,B,C) values(2,'new-generation',hextoraw('00AAFF'),to_clob('new-generation-text'));\ncommit;\nselect 'GEN|'||object_name||'|'||object_id||'|'||data_object_id from dba_objects where owner='ORKLAB' and (object_name in ('{table}','{index}') or object_name in (select segment_name from dba_lobs where owner='ORKLAB' and table_name='{table}') or object_name in(select index_name from dba_lobs where owner='ORKLAB' and table_name='{table}')) order by object_name;\nselect 'ROW|'||ID||'|'||TXT||'|'||rawtohex(dbms_lob.substr(B,1000,1))||'|'||dbms_lob.substr(C,1000,1) from ORKLAB.{table};")
    freeze('truncate',first,minimum);print('LOB_HISTORY_FROZEN',flush=True)
finally:
    if started:run('source-stop',['docker','stop','-t','60',S])
