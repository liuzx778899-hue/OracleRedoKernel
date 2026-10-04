"""One-shot INSERT and separate committed DELETE of an owned new row after a real fixed-SCN acquisition."""
import hashlib,json,re,subprocess,sys
from pathlib import Path
ROOT=Path(__file__).resolve().parent.parent;CONTAINER='oraredo-19c-lab-20261004';OUT=ROOT/'lab/out/oracle19-insert-delete-rows-20261004'
if sys.argv[1:]!=['--execute']:raise RuntimeError('explicit one-shot --execute required')
OUT.mkdir(parents=True,exist_ok=False)
def run(name,args):
 (OUT/(name+'-command.json')).write_text(json.dumps(args));p=subprocess.run(args,capture_output=True,timeout=240)
 for ext,data in [('stdout',p.stdout),('stderr',p.stderr)]:(OUT/(name+'.'+ext+'.txt')).write_bytes(data)
 (OUT/(name+'.rc.txt')).write_text(str(p.returncode))
 if p.returncode:raise RuntimeError(name+': preserve evidence, never replay committed source actions')
 return p.stdout.decode('utf-8')
def sql(name,body):
 path=OUT/(name+'.sql');path.write_text('whenever sqlerror exit failure rollback\nwhenever oserror exit failure rollback\nset pages 0 lines 32767 trimspool on feedback off heading off\n'+body+'\nexit success\n');remote='/tmp/ork-insert-delete-rows-'+name+'.sql'
 run('copy-'+name,['docker','cp',str(path),CONTAINER+':'+remote]);return run(name,['docker','exec','-e','NLS_LANG=.AL32UTF8',CONTAINER,'sqlplus','-s','/','as','sysdba','@'+remote])
def rows(label,asof=''):
 return "select '"+label+"|'||dbms_rowid.rowid_object(rowid)||'|'||dbms_rowid.rowid_relative_fno(rowid)||'|'||dbms_rowid.rowid_block_number(rowid)||'|'||dbms_rowid.rowid_row_number(rowid)||'|'||rawtohex(utl_raw.cast_from_number(ID))||'|'||case when TXT is null then 'NULL' else rawtohex(utl_raw.cast_to_raw(TXT)) end||'|'||case when NOTE is null then 'NULL' else rawtohex(utl_raw.cast_to_raw(NOTE)) end from ORKLAB.T_BASELINE_20261004 "+asof+" order by rowid;\n"
binding=run('binding',['docker','inspect',CONTAINER,'--format','{{.Config.Labels.owner}}|{{.Config.Image}}|{{.State.Status}}'])
if binding.strip()!='OracleRedoKernel|container-registry.oracle.com/database/enterprise:19.3.0.0|running':raise RuntimeError('DEDICATED_SOURCE_MISMATCH')
pre=sql('preflight',"""select 'START_SCN|'||current_scn from v$database;
declare n number;begin select count(*) into n from v$database where dbid=1276726821 and activation#=1276717861 and resetlogs_change#=1920977 and log_mode='ARCHIVELOG' and supplemental_log_data_min='NO' and supplemental_log_data_all='NO';if n!=1 then raise_application_error(-20001,'source mismatch');end if;end;
/
alter session set container=ORKPDB;
declare n number;begin select count(*) into n from dba_objects where owner='ORKLAB' and ((object_name='T_BASELINE_20261004' and object_id=73073 and data_object_id=73076) or (object_name='PK_BASELINE_20261004' and object_id=73074 and data_object_id=73075));if n!=2 then raise_application_error(-20002,'generation mismatch');end if;select count(*) into n from ORKLAB.T_BASELINE_20261004;if n!=1 then raise_application_error(-20003,'fixture count mismatch');end if;select count(*) into n from ORKLAB.T_BASELINE_20261004 where ID=11 and TXT='boundary-closed' and NOTE='new-note';if n!=1 then raise_application_error(-20004,'fixture value mismatch');end if;end;
/
""")
run('acquire-baseline',[sys.executable,str(ROOT/'lab/capture_heap_baseline19.py'),'--execute',str(OUT/'baseline')]);data=(OUT/'baseline/snapshot.stdout.txt').read_text();receipt=json.loads((OUT/'baseline/receipt.json').read_text());scn=int(re.search(r'^SNAPSHOT_SCN\|(\d+)',data,re.M)[1])
if 'ROW|73076|12|403|0|C10C|626F756E646172792D636C6F736564|6E65772D6E6F7465' not in data or hashlib.sha256((OUT/'baseline/snapshot.stdout.txt').read_bytes()).hexdigest()!=receipt['snapshot_sha256']:raise RuntimeError('ACTUAL_BASELINE_MISMATCH')
after=sql('change',"""alter session set container=ORKPDB;
begin insert into ORKLAB.T_BASELINE_20261004(ID,TXT,NOTE) values(12,'insert-proof','inserted-note');if sql%rowcount!=1 then raise_application_error(-20005,'insert count mismatch');end if;end;
/
select 'INSERT_TX|'||dbms_transaction.local_transaction_id from dual;
commit;
"""+rows('AFTER_INSERT')+"""
begin delete from ORKLAB.T_BASELINE_20261004 where ID=12 and TXT='insert-proof' and NOTE='inserted-note';if sql%rowcount!=1 then raise_application_error(-20006,'delete CAS mismatch');end if;end;
/
select 'DELETE_TX|'||dbms_transaction.local_transaction_id from dual;
commit;
"""+rows('OLD_BASELINE','as of scn '+str(scn))+rows('FINAL')+"""alter session set container=CDB$ROOT;
select 'END_SCN|'||current_scn from v$database;
alter system archive log current;
select 'ARCHIVE|'||thread#||'|'||sequence#||'|'||first_change#||'|'||next_change#||'|'||name from v$archived_log where name is not null order by recid;
""")
start=int(re.search(r'^START_SCN\|(\d+)',pre,re.M)[1]);end=int(re.search(r'^END_SCN\|(\d+)',after,re.M)[1]);files=[]
for line in after.splitlines():
 p=line.strip().split('|')
 if len(p)!=6 or p[0]!='ARCHIVE' or int(p[4])<=start or int(p[3])>end:continue
 local=OUT/('archive-'+p[1]+'-'+p[2]+'.dbf');run('capture-'+p[2],['docker','cp',CONTAINER+':'+p[5],str(local)]);files.append({'sequence':int(p[2]),'source':p[5],'path':local.name,'first_scn':int(p[3]),'next_scn':int(p[4]),'bytes':local.stat().st_size,'sha256':hashlib.sha256(local.read_bytes()).hexdigest()})
if not files or any(a['next_scn']!=b['first_scn'] for a,b in zip(files,files[1:])):raise RuntimeError('ARCHIVE_COVERAGE_GAP; preserve committed actions')
trace=sql('dump',"alter session set tracefile_identifier='ORK_INSERT_DELETE_ROWS';\n"+''.join("alter system dump logfile '"+f['source']+"' scn min "+str(start)+" scn max "+str(end)+";\n" for f in files)+"select value from v$diag_info where name='Default Trace File';")
paths=[x.strip() for x in trace.splitlines() if x.strip().startswith('/opt/oracle/') and x.strip().endswith('.trc')]
if len(paths)!=1:raise RuntimeError('TRACE_PATH_AMBIGUOUS')
run('copy-trace',['docker','cp',CONTAINER+':'+paths[0],str(OUT/'dump.trc')]);m={'snapshot_scn':scn,'acquisition_sha256':receipt['snapshot_sha256'],'start_scn':start,'end_scn':end,'insert_xid':re.search(r'INSERT_TX\|([^\r\n]+)',after)[1],'delete_xid':re.search(r'DELETE_TX\|([^\r\n]+)',after)[1],'files':files,'scope':'dedicated two committed logical operations; not public CDC'}
(OUT/'manifest.json').write_text(json.dumps(m,indent=2)+'\n');print(json.dumps(m))
