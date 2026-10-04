"""One-shot generation change on this project's already frozen W5 fixture only."""
import hashlib,json,re,subprocess,sys
from pathlib import Path
ROOT=Path(__file__).resolve().parent.parent;CONTAINER='oraredo-19c-lab-20261004'
OLD=ROOT/'lab/out/oracle19-baseline-20261004';OUT=ROOT/'lab/out/oracle19-baseline-generation-20261004'
if sys.argv[1:]!=['--execute']:raise RuntimeError('explicit --execute required')
old=json.loads((OLD/'manifest.json').read_text(encoding='utf-8'))
if old['baseline_scn']!=2282688 or old['files'][0]['sha256']!=hashlib.sha256((OLD/old['files'][0]['path']).read_bytes()).hexdigest():raise RuntimeError('FROZEN_BASELINE_SOURCE_MISMATCH')
OUT.mkdir(parents=True,exist_ok=False)
def run(name,args,expected=0):
    p=subprocess.run(args,capture_output=True)
    for ext,data in [('stdout',p.stdout),('stderr',p.stderr)]: (OUT/(name+'.'+ext+'.txt')).write_bytes(data)
    (OUT/(name+'.rc.txt')).write_text(str(p.returncode));(OUT/(name+'-command.json')).write_text(json.dumps(args))
    if expected==0 and p.returncode:raise RuntimeError(name+': failed; never replay source actions')
    return p
def sql(name,body,expected=0):
    path=OUT/(name+'.sql');path.write_text('whenever sqlerror exit failure rollback\nwhenever oserror exit failure rollback\nset pages 0 lines 32767 trimspool on feedback off verify off\n'+body+'\nexit success\n',encoding='utf-8')
    remote='/tmp/ork-baseline-generation-'+name+'.sql';run('copy-'+name,['docker','cp',str(path),CONTAINER+':'+remote])
    return run(name,['docker','exec','-e','NLS_LANG=.AL32UTF8',CONTAINER,'sqlplus','-s','/','as','sysdba','@'+remote],expected)
p=run('binding',['docker','inspect',CONTAINER,'--format','{{.Config.Labels.owner}}|{{.Config.Image}}|{{.State.Status}}'])
if p.stdout.decode().strip()!='OracleRedoKernel|container-registry.oracle.com/database/enterprise:19.3.0.0|running':raise RuntimeError('DEDICATED_SOURCE_MISMATCH')
dictionary="select 'OBJECT|'||object_name||'|'||object_id||'|'||data_object_id from dba_objects where owner='ORKLAB' and object_name in ('T_BASELINE_20261004','PK_BASELINE_20261004') order by object_name;\n"
before=sql('before',"""
select 'START_SCN|'||current_scn from v$database;
declare id number; lm varchar2(30); ml varchar2(30); al varchar2(30);
begin select dbid,log_mode,supplemental_log_data_min,supplemental_log_data_all into id,lm,ml,al from v$database;
if id!=1276726821 or lm!='ARCHIVELOG' or ml!='NO' or al!='NO' then raise_application_error(-20001,'source mismatch');end if;end;
/
alter session set container=ORKPDB;
declare n number;begin select count(*) into n from dba_objects where owner='ORKLAB' and object_name='T_BASELINE_20261004' and object_id=73073 and data_object_id=73073;
if n!=1 then raise_application_error(-20002,'generation mismatch');end if;
select count(*) into n from ORKLAB.T_BASELINE_20261004 where ID=10 and TXT='gamma' and NOTE is null;
if n!=1 then raise_application_error(-20003,'fixture row mismatch');end if;end;
/
"""+dictionary).stdout.decode()
after=sql('change',"""
alter session set container=ORKPDB;
truncate table ORKLAB.T_BASELINE_20261004;
insert into ORKLAB.T_BASELINE_20261004 (ID,TXT,NOTE) values(10,'new-epoch','new-note');
select 'NEW_TX|'||dbms_transaction.local_transaction_id from dual;
commit;
"""+dictionary+"""
select 'NEW_ROW|'||dbms_rowid.rowid_object(rowid)||'|'||dbms_rowid.rowid_relative_fno(rowid)||'|'||dbms_rowid.rowid_block_number(rowid)||'|'||dbms_rowid.rowid_row_number(rowid)||'|'||ID||'|'||TXT||'|'||NOTE from ORKLAB.T_BASELINE_20261004;
alter session set container=CDB$ROOT;
select 'END_SCN|'||current_scn from v$database;
alter system archive log current;
select 'ARCHIVE|'||thread#||'|'||sequence#||'|'||first_change#||'|'||next_change#||'|'||name from v$archived_log where name is not null order by recid;
""").stdout.decode()
start=int(re.search(r'^START_SCN\|(\d+)',before,re.M)[1]);end=int(re.search(r'^END_SCN\|(\d+)',after,re.M)[1]);files=[]
for line in after.splitlines():
    p=line.strip().split('|')
    if len(p)!=6 or p[0]!='ARCHIVE' or int(p[4])<=start or int(p[3])>end:continue
    local=OUT/('archive-'+p[1]+'-'+p[2]+'.dbf');run('capture-'+p[2],['docker','cp',CONTAINER+':'+p[5],str(local)])
    files.append({'sequence':int(p[2]),'source':p[5],'path':local.name,'bytes':local.stat().st_size,'sha256':hashlib.sha256(local.read_bytes()).hexdigest()})
if not files:raise RuntimeError('ZERO_ARCHIVES')
dump=sql('dump',"alter session set tracefile_identifier='ORK_BASELINE_GENERATION';\n"+''.join("alter system dump logfile '"+f['source']+"' scn min "+str(start)+" scn max "+str(end)+";\n" for f in files)+"select value from v$diag_info where name='Default Trace File';").stdout.decode()
traces=[line.strip() for line in dump.splitlines() if line.strip().startswith('/opt/oracle/') and line.strip().endswith('.trc')]
if len(traces)!=1:raise RuntimeError('TRACE_PATH_AMBIGUOUS')
run('copy-trace',['docker','cp',CONTAINER+':'+traces[0],str(OUT/'dump.trc')])
p=sql('old-baseline-after-ddl','alter session set container=ORKPDB;\nselect ID,TXT,NOTE from ORKLAB.T_BASELINE_20261004 as of scn '+str(old['baseline_scn'])+';',1)
if p.returncode==0 or b'ORA-01466' not in p.stdout:raise RuntimeError('OLD_BASELINE_DDL_REFUSAL_NOT_OBSERVED')
(OUT/'manifest.json').write_text(json.dumps({'source':CONTAINER,'old_baseline_scn':old['baseline_scn'],'start_scn':start,'end_scn':end,'files':files,'old_baseline_after_ddl_rc':p.returncode,'scope':'dedicated generation-boundary research; production dictionary transition unaccepted'},indent=2),encoding='utf-8')
print(json.dumps({'frozen_archives':len(files),'old_baseline_after_ddl_rc':p.returncode}))
