"""One-shot owned fixture UPDATE+PK UPDATE after the genuinely acquired snapshot; never repeat committed source actions."""
import hashlib,json,re,subprocess,sys
from pathlib import Path
ROOT=Path(__file__).resolve().parent.parent;CONTAINER='oraredo-19c-lab-20261004';OUT=ROOT/'lab/out/oracle19-typed-generation-replay-20261004';BASE=ROOT/'lab/out/oracle19-baseline-acquisition-20261004/snapshot.stdout.txt'
TRUSTED='36e3c954dd0844367bbb2bb9bec8c9b1f63f4069b1c1b01aa6239f62792f220d'
if sys.argv[1:]!=['--execute']:raise RuntimeError('explicit one-shot --execute required')
if hashlib.sha256(BASE.read_bytes()).hexdigest()!=TRUSTED:raise RuntimeError('ACQUIRED_BASELINE_CHANGED')
OUT.mkdir(parents=True,exist_ok=False)
def run(name,args):
 p=subprocess.run(args,capture_output=True)
 for ext,data in [('stdout',p.stdout),('stderr',p.stderr)]: (OUT/(name+'.'+ext+'.txt')).write_bytes(data)
 (OUT/(name+'.rc.txt')).write_text(str(p.returncode));(OUT/(name+'-command.json')).write_text(json.dumps(args))
 if p.returncode:raise RuntimeError(name+': preserve evidence; never replay source DML')
 return p.stdout.decode('utf-8')
def sql(name,body):
 path=OUT/(name+'.sql');path.write_text('whenever sqlerror exit failure rollback\nwhenever oserror exit failure rollback\nset pages 0 lines 32767 trimspool on feedback off verify off\n'+body+'\nexit success\n',encoding='utf-8');remote='/tmp/ork-typed-generation-'+name+'.sql'
 run('copy-'+name,['docker','cp',str(path),CONTAINER+':'+remote]);return run(name,['docker','exec','-e','NLS_LANG=.AL32UTF8',CONTAINER,'sqlplus','-s','/','as','sysdba','@'+remote])
binding=run('binding',['docker','inspect',CONTAINER,'--format','{{.Config.Labels.owner}}|{{.Config.Image}}|{{.State.Status}}'])
if binding.strip()!='OracleRedoKernel|container-registry.oracle.com/database/enterprise:19.3.0.0|running':raise RuntimeError('DEDICATED_SOURCE_MISMATCH')
def rows(label,asof=''):
 return "select '"+label+"|'||dbms_rowid.rowid_object(rowid)||'|'||dbms_rowid.rowid_relative_fno(rowid)||'|'||dbms_rowid.rowid_block_number(rowid)||'|'||dbms_rowid.rowid_row_number(rowid)||'|'||rawtohex(utl_raw.cast_from_number(ID))||'|'||case when TXT is null then 'NULL' else rawtohex(utl_raw.cast_to_raw(TXT)) end||'|'||case when NOTE is null then 'NULL' else rawtohex(utl_raw.cast_to_raw(NOTE)) end from ORKLAB.T_BASELINE_20261004 "+asof+" order by rowid;\n"
preflight=sql('preflight',"""
select 'START_SCN|'||current_scn from v$database;
declare n number;begin select count(*) into n from v$database where dbid=1276726821 and activation#=1276717861 and resetlogs_change#=1920977 and log_mode='ARCHIVELOG' and supplemental_log_data_min='NO' and supplemental_log_data_all='NO';if n!=1 then raise_application_error(-20001,'source mismatch');end if;end;
/
alter session set container=ORKPDB;
declare n number;begin select count(*) into n from dba_objects where owner='ORKLAB' and ((object_name='T_BASELINE_20261004' and object_id=73073 and data_object_id=73076) or (object_name='PK_BASELINE_20261004' and object_id=73074 and data_object_id=73075));if n!=2 then raise_application_error(-20002,'generation mismatch');end if;select count(*) into n from ORKLAB.T_BASELINE_20261004;if n!=1 then raise_application_error(-20003,'fixture count mismatch');end if;end;
/
"""+rows('BASELINE','as of scn 2341293'))
after=sql('change',"""
alter session set container=ORKPDB;
begin update ORKLAB.T_BASELINE_20261004 set TXT='typed-epoch' where ID=10 and TXT='new-epoch' and NOTE='new-note';if sql%rowcount!=1 then raise_application_error(-20004,'first CAS mismatch');end if;end;
/
begin update ORKLAB.T_BASELINE_20261004 set ID=11 where ID=10 and TXT='typed-epoch' and NOTE='new-note';if sql%rowcount!=1 then raise_application_error(-20005,'key CAS mismatch');end if;end;
/
select 'UPDATE_TX|'||dbms_transaction.local_transaction_id from dual;
commit;
"""+rows('OLD_BASELINE','as of scn 2341293')+rows('FINAL')+"""
alter session set container=CDB$ROOT;
select 'END_SCN|'||current_scn from v$database;
alter system archive log current;
select 'ARCHIVE|'||thread#||'|'||sequence#||'|'||first_change#||'|'||next_change#||'|'||name from v$archived_log where name is not null order by recid;
""")
start=int(re.search(r'^START_SCN\|(\d+)',preflight,re.M)[1]);end=int(re.search(r'^END_SCN\|(\d+)',after,re.M)[1]);files=[]
for line in after.splitlines():
 p=line.strip().split('|')
 if len(p)!=6 or p[0]!='ARCHIVE' or int(p[4])<=start or int(p[3])>end:continue
 local=OUT/('archive-'+p[1]+'-'+p[2]+'.dbf');run('capture-'+p[2],['docker','cp',CONTAINER+':'+p[5],str(local)]);files.append({'sequence':int(p[2]),'source':p[5],'path':local.name,'bytes':local.stat().st_size,'sha256':hashlib.sha256(local.read_bytes()).hexdigest()})
if len(files)!=1:raise RuntimeError('EXPECTED_SINGLE_BOUNDED_ARCHIVE; preserve committed actions')
dump=sql('dump',"alter session set tracefile_identifier='ORK_TYPED_GENERATION';\n"+''.join("alter system dump logfile '"+f['source']+"' scn min "+str(start)+" scn max "+str(end)+";\n" for f in files)+"select value from v$diag_info where name='Default Trace File';")
traces=[line.strip() for line in dump.splitlines() if line.strip().startswith('/opt/oracle/') and line.strip().endswith('.trc')]
if len(traces)!=1:raise RuntimeError('TRACE_PATH_AMBIGUOUS')
run('copy-trace',['docker','cp',CONTAINER+':'+traces[0],str(OUT/'dump.trc')]);(OUT/'manifest.json').write_text(json.dumps({'baseline_scn':2341293,'baseline_acquisition_sha256':TRUSTED,'start_scn':start,'end_scn':end,'files':files,'scope':'one-shot dedicated typed generation replay; not public CDC'},indent=2)+'\n',encoding='utf-8');print(json.dumps({'frozen_archives':files,'transaction':re.search(r'UPDATE_TX\|([^\r\n]+)',after)[1]}))
