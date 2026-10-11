"""One owned BasicFile to SecureFile online-redefinition sample; never replay source actions."""
from pathlib import Path
import hashlib,json,re,shutil,subprocess,sys,time
R=Path(__file__).resolve().parent.parent;N=R/'lab/out/lob-migration-20261011/source'
S='oraredo-19c-lab-20261004';H='ork-product-runtime'
resume=sys.argv[1:]==['--resume-acquisition'];dependents=sys.argv[1:]==['--resume-dependents']
assert resume or dependents or sys.argv[1:]==['--execute']
if dependents:assert (N/'baseline.snapshot').exists() and (N/'migration-r2.rc').read_text().strip()!='0' and not (N/'future-r2.rc').exists()
elif resume:assert N.exists() and not (N/'actions.started').exists() and not (N/'baseline.snapshot').exists()
else:N.mkdir(parents=True,exist_ok=False);shutil.copyfile(__file__,N/'executed.py')
common=(R/'lab/common_fields_source.py').read_text();exec(common[common.index('reader='):common.index('libs=N/')])
if resume or dependents:
    original_run=run
    suffix='-r4' if dependents and (N/'state-r3.rc').exists() else '-r3' if dependents else '-r2'
    shutil.copyfile(__file__,N/('executed'+suffix+'.py'))
    def run(label,args,data=None):return original_run(label+suffix,args,data)
else:
    shutil.copytree(R/'lab/out/lob-history-20261011/source/libs',N/'libs');(N/'classes').mkdir();(N/'inputs').mkdir()
    shutil.copyfile(R/'lab/out/dictionary-baseline-candidate-20261006/source.secret',N/'source.secret')
    run('compile',['D:/work/Java/jdk-17.0.7/bin/javac.exe','-cp',str(N/'libs/*'),'-d',N/'classes',R/'lab/OracleSnapshotAcquisitionProbe.java'])
table='T_D4_MIG_20261011';interim='T_D4_MIG_INT_20261011';index='PK_D4_MIG_20261011';started=False
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
    out=sql('pre',f"select 'CURRENT|'||sequence# from v$log where status='CURRENT';\nselect 'LOGGING|'||supplemental_log_data_min||'|'||supplemental_log_data_all from v$database;\nalter session set container=ORKPDB;\nselect 'FREE|'||count(*) from dba_tables where owner='ORKLAB' and table_name in ('{table}','{interim}');")
    assert ('FREE|2' if dependents else 'FREE|1' if resume else 'FREE|0') in out and 'LOGGING|NO|NO' in out
    original_pre=(N/'pre.stdout').read_text() if resume or dependents else out
    first=int(next(l.split('|')[1] for l in original_pre.splitlines() if l.startswith('CURRENT|')))
    if not resume and not dependents:sql('fixture',f"alter session set container=ORKPDB;\ncreate table ORKLAB.{table}(ID number not null,TXT varchar2(128),B blob,C clob,constraint {index} primary key(ID)) lob(B) store as BASICFILE(enable storage in row nocache logging) lob(C) store as BASICFILE(enable storage in row nocache logging);\ninsert into ORKLAB.{table} values(1,'migration-seed',hextoraw('0080FF'),to_clob('migration-text'));\ncommit;\ngrant select,flashback on ORKLAB.{table} to {reader[0]};")
    if not dependents:run('baseline',['docker','run','--rm','--name',H,'--label','owner=OracleRedoKernel','--network','container:'+S,
        '--mount','type=bind,source='+str(N)+',target=/evidence','--entrypoint','java','flink:1.18-java17','-cp','/evidence/classes:/evidence/libs/*',
        'OracleSnapshotAcquisitionProbe','/evidence/source.secret','migration-normal','ORKLAB',table,index,'history-normal','/evidence/baseline.snapshot'])
    if not dependents:(N/'snapshot.json').write_text(json.dumps(dict(path='baseline.snapshot',sha256=sha(N/'baseline.snapshot')),indent=2))
    minimum=int(next(l.split('|')[1] for l in (N/'baseline.snapshot').read_text().splitlines() if l.startswith('SNAPSHOT_SCN|')))
    if not dependents:(N/'actions.started').write_text('one BasicFile to SecureFile online redefinition; never replay\n')
    start_sql=f"alter table ORKLAB.{interim} modify(ID null);" if dependents else f"""create table ORKLAB.{interim}(ID number,TXT varchar2(128),B blob,C clob) lob(B) store as SECUREFILE(enable storage in row nocache logging) lob(C) store as SECUREFILE(enable storage in row nocache logging);
begin dbms_redefinition.can_redef_table('ORKLAB','{table}',dbms_redefinition.cons_use_pk);dbms_redefinition.start_redef_table('ORKLAB','{table}','{interim}','ID ID, TXT TXT, B B, C C',dbms_redefinition.cons_use_pk);end;
/"""
    sql('migration',f"""alter session set container=ORKPDB;
{start_sql}
declare errors pls_integer;begin dbms_redefinition.copy_table_dependents('ORKLAB','{table}','{interim}',1,true,true,true,false,errors);if errors<>0 then raise_application_error(-20001,'MIGRATION_DEPENDENCY_ERRORS');end if;end;
/
begin dbms_redefinition.finish_redef_table('ORKLAB','{table}','{interim}');end;
/
select 'LOB|'||column_name||'|'||securefile||'|'||segment_name||'|'||index_name from dba_lobs where owner='ORKLAB' and table_name='{table}' order by column_name;
select 'GEN|'||object_name||'|'||object_id||'|'||data_object_id from dba_objects where owner='ORKLAB' and (object_name in ('{table}','{index}') or object_name in(select segment_name from dba_lobs where owner='ORKLAB' and table_name='{table}') or object_name in(select index_name from dba_lobs where owner='ORKLAB' and table_name='{table}')) order by object_name;
select 'ROW|'||ID||'|'||TXT||'|'||rawtohex(dbms_lob.substr(B,1000,1))||'|'||dbms_lob.substr(C,1000,1) from ORKLAB.{table};""")
    sql('future',f"alter session set container=ORKPDB;\nupdate ORKLAB.{table} set TXT='after-migration',B=hextoraw('00AAFF80'),C=to_clob('after-migration-text') where ID=1;\ncommit;\nselect 'ROW|'||ID||'|'||TXT||'|'||rawtohex(dbms_lob.substr(B,1000,1))||'|'||dbms_lob.substr(C,1000,1) from ORKLAB.{table};")
    closed=sql('close',"select 'CURRENT|'||sequence# from v$log where status='CURRENT';\nalter system archive log current;")
    last=int(next(l.split('|')[1] for l in closed.splitlines() if l.startswith('CURRENT|')));archives=[]
    for seq in range(first,last+1):
        out=sql('archive-'+str(seq),f"select 'PATH|'||name from v$archived_log where sequence#={seq} and archived='YES' and deleted='NO' and name is not null order by recid;")
        paths=set(l.split('|')[1] for l in out.splitlines() if l.startswith('PATH|'));assert len(paths)==1
        path=paths.pop();local=N/'inputs'/f'{seq}.raw';run('copy-'+str(seq),['docker','cp',S+':'+path,local])
        assert run('sha-'+str(seq),['docker','exec',S,'sha256sum',path]).split()[0]==sha(local)
        archives.append(dict(sequence=seq,sha256=sha(local),bytes=local.stat().st_size))
        trace=sql('native-'+str(seq),f"alter session set tracefile_identifier='ORK_LOB_MIGRATION';\nalter system dump logfile '{path}' scn min {minimum};\nselect 'TRACE|'||value from v$diag_info where name='Default Trace File';")
        tracepath=next(l.split('|',1)[1] for l in trace.splitlines() if l.startswith('TRACE|'));run('native-copy-'+str(seq),['docker','cp',S+':'+tracepath,N/f'{seq}.trc'])
        (N/'archives.json').write_text(json.dumps(archives,indent=2))
    print('LOB_MIGRATION_FROZEN',flush=True)
finally:
    if started:run('source-stop',['docker','stop','-t','60',S])
