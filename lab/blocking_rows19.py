"""One-shot concurrent owned row transactions from a pre-acquired baseline; preserve source actions."""
import hashlib,json,queue,re,subprocess,sys,threading,time
from pathlib import Path
ROOT=Path(__file__).resolve().parent.parent;CONTAINER='oraredo-19c-lab-20261004';OUT=ROOT/'lab/out/oracle19-blocking-rows-20261005'
if sys.argv[1:]!=['--execute']:raise RuntimeError('explicit one-shot --execute required')
OUT.mkdir(parents=True,exist_ok=False)
def run(name,args):
 (OUT/(name+'-command.json')).write_text(json.dumps(args))
 p=subprocess.run(args,capture_output=True,timeout=240)
 for ext,data in [('stdout',p.stdout),('stderr',p.stderr)]:(OUT/(name+'.'+ext+'.txt')).write_bytes(data)
 (OUT/(name+'.rc.txt')).write_text(str(p.returncode))
 if p.returncode:raise RuntimeError(name+': preserve evidence, do not replay source actions')
 return p.stdout.decode('utf-8')
def sql(name,body):
 p=OUT/(name+'.sql');p.write_text('whenever sqlerror exit failure rollback\nwhenever oserror exit failure rollback\nset pages 0 lines 32767 trimspool on feedback off heading off\n'+body+'\nexit success\n')
 remote='/tmp/ork-blocking-rows-'+name+'.sql';run('copy-'+name,['docker','cp',str(p),CONTAINER+':'+remote])
 return run(name,['docker','exec','-e','NLS_LANG=.AL32UTF8',CONTAINER,'sqlplus','-s','/','as','sysdba','@'+remote])
def rows(label,asof=''):
 return "select '"+label+"|'||dbms_rowid.rowid_object(rowid)||'|'||dbms_rowid.rowid_relative_fno(rowid)||'|'||dbms_rowid.rowid_block_number(rowid)||'|'||dbms_rowid.rowid_row_number(rowid)||'|'||rawtohex(utl_raw.cast_from_number(ID))||'|'||case when TXT is null then 'NULL' else rawtohex(utl_raw.cast_to_raw(TXT)) end||'|'||case when NOTE is null then 'NULL' else rawtohex(utl_raw.cast_to_raw(NOTE)) end from ORKLAB.T_BASELINE_20261004 "+asof+" order by rowid;\n"
binding=run('binding',['docker','inspect',CONTAINER,'--format','{{.Config.Labels.owner}}|{{.Config.Image}}|{{.State.Status}}'])
if binding.strip()!='OracleRedoKernel|container-registry.oracle.com/database/enterprise:19.3.0.0|running':raise RuntimeError('DEDICATED_SOURCE_MISMATCH')
pre=sql('preflight',"""
select 'START_SCN|'||current_scn from v$database;
declare n number;begin select count(*) into n from v$database where dbid=1276726821 and activation#=1276717861 and resetlogs_change#=1920977 and log_mode='ARCHIVELOG' and supplemental_log_data_min='NO' and supplemental_log_data_all='NO';if n!=1 then raise_application_error(-20001,'source mismatch');end if;end;
/
alter session set container=ORKPDB;
declare n number;begin select count(*) into n from dba_objects where owner='ORKLAB' and ((object_name='T_BASELINE_20261004' and object_id=73073 and data_object_id=73076) or (object_name='PK_BASELINE_20261004' and object_id=73074 and data_object_id=73075));if n!=2 then raise_application_error(-20002,'generation mismatch');end if;select count(*) into n from ORKLAB.T_BASELINE_20261004;if n!=1 then raise_application_error(-20003,'fixture count mismatch');end if;select count(*) into n from ORKLAB.T_BASELINE_20261004 where ID=11 and TXT='concurrent-closed' and NOTE='new-note';if n!=1 then raise_application_error(-20004,'fixture value mismatch');end if;end;
/
""")
run('acquire-baseline',[sys.executable,str(ROOT/'lab/capture_heap_baseline19.py'),'--execute',str(OUT/'baseline')])
data=(OUT/'baseline/snapshot.stdout.txt').read_text();receipt=json.loads((OUT/'baseline/receipt.json').read_text());scn=int(re.search(r'^SNAPSHOT_SCN\|(\d+)',data,re.M)[1])
expected='ROW|73076|12|403|0|C10C|636F6E63757272656E742D636C6F736564|6E65772D6E6F7465'
if expected not in data or hashlib.sha256((OUT/'baseline/snapshot.stdout.txt').read_bytes()).hexdigest()!=receipt['snapshot_sha256']:raise RuntimeError('INDEPENDENT_SNAPSHOT_OLD_ROW_OR_RECEIPT_MISMATCH')
args=['docker','exec','-i','-e','NLS_LANG=.AL32UTF8',CONTAINER,'sqlplus','-s','/','as','sysdba'];(OUT/'held-command.json').write_text(json.dumps(args))
p=subprocess.Popen(args,stdin=subprocess.PIPE,stdout=subprocess.PIPE,stderr=subprocess.PIPE);messages=queue.Queue()
def drain(stream,name,deliver):
 with (OUT/name).open('wb') as f:
  for line in iter(stream.readline,b''):
   f.write(line);f.flush()
   if deliver:messages.put(line.decode('utf-8').strip())
  if deliver:messages.put(None)
threads=[threading.Thread(target=drain,args=(p.stdout,'held.stdout.txt',True)),threading.Thread(target=drain,args=(p.stderr,'held.stderr.txt',False))]
for t in threads:t.start()
def send(name,text):
 (OUT/(name+'.sql')).write_text(text);p.stdin.write(text.encode('utf-8'));p.stdin.flush()
def wait_for(prefix):
 deadline=time.monotonic()+90
 while True:
  line=messages.get(timeout=max(0.01,deadline-time.monotonic()))
  if line is None:raise RuntimeError('held session ended before '+prefix)
  if line.startswith(prefix):return line
  if time.monotonic()>=deadline:raise RuntimeError('held handshake timeout')
try:
 send('first',"""whenever sqlerror exit failure rollback
whenever oserror exit failure rollback
set pages 0 lines 32767 trimspool on feedback off heading off
alter session set container=ORKPDB;
exec dbms_application_info.set_module('ORK_BLOCK_A','held');
begin update ORKLAB.T_BASELINE_20261004 set TXT='blocking-open' where ID=11 and TXT='concurrent-closed' and NOTE='new-note';if sql%rowcount!=1 then raise_application_error(-20005,'first CAS mismatch');end if;end;
/
select 'READY|'||dbms_transaction.local_transaction_id from dual;
""")
 xid=wait_for('READY|').split('|')[1];(OUT/'ready.json').write_text(json.dumps({'xid':xid,'state':'uncommitted'}))
 sql('first-archive','alter system archive log current;')
 bresult={}
 def run_b():
  try:
   bresult['truth']=sql('b-commit',"""alter session set container=ORKPDB;
exec dbms_application_info.set_module('ORK_BLOCK_B','waiting');
declare prior varchar2(100);begin select TXT into prior from ORKLAB.T_BASELINE_20261004 where ID=11 and NOTE='new-note' for update;if prior!='blocking-a-final' then raise_application_error(-20007,'B locked before value mismatch');end if;update ORKLAB.T_BASELINE_20261004 set TXT='blocking-closed' where ID=11 and TXT='blocking-a-final' and NOTE='new-note';if sql%rowcount!=1 then raise_application_error(-20008,'B CAS mismatch');end if;end;
/
select 'B_TX|'||dbms_transaction.local_transaction_id from dual;
commit;
"""+rows('AFTER_B_COMMIT'))
  except BaseException as e:bresult['error']=repr(e)
 bt=threading.Thread(target=run_b);bt.start();blocked=False
 for attempt in range(20):
  observation=sql('wait-'+str(attempt),"""alter session set container=ORKPDB;
select 'WAIT|'||b.sid||'|'||b.serial#||'|'||b.event||'|'||b.state||'|'||b.blocking_session||'|'||a.sid from v$session b join v$session a on a.sid=b.blocking_session where b.module='ORK_BLOCK_B' and a.module='ORK_BLOCK_A' and b.event='enq: TX - row lock contention' and b.state='WAITING';
"""+rows('WHILE_BLOCKED'))
  if re.search(r'^WAIT\|',observation,re.M):
   if 'WHILE_BLOCKED|73076|12|403|0|C10C|636F6E63757272656E742D636C6F736564|6E65772D6E6F7465' not in observation:raise RuntimeError('UNCOMMITTED_A_VALUE_LEAKED')
   blocked=True;break
  if not bt.is_alive():raise RuntimeError('B_COMPLETED_WITHOUT_PROVEN_BLOCK')
  time.sleep(1)
 if not blocked:raise RuntimeError('ROW_LOCK_WAIT_NOT_PROVEN')
 send('second',"""begin update ORKLAB.T_BASELINE_20261004 set TXT='blocking-a-final' where ID=11 and TXT='blocking-open' and NOTE='new-note';if sql%rowcount!=1 then raise_application_error(-20006,'second CAS mismatch');end if;end;
/
commit;
prompt COMMITTED
exit success
""")
 wait_for('COMMITTED');p.stdin.close();rc=p.wait(timeout=30);(OUT/'held.rc.txt').write_text(str(rc))
 if rc:raise RuntimeError('held session failure after commit, do not replay')
 bt.join(timeout=60)
 if bt.is_alive() or 'error' in bresult:raise RuntimeError('B failure or timeout after A committed; never replay')
 btruth=bresult['truth']
finally:
 if p.poll() is None:
  try:
   p.stdin.write(b'rollback;\nexit failure\n');p.stdin.flush();p.stdin.close();p.wait(timeout=30)
  except (OSError,subprocess.TimeoutExpired):
   p.kill();p.wait(timeout=30)
  (OUT/'held.rc.txt').write_text(str(p.returncode))
 for t in threads:t.join(timeout=30)
after=sql('final',"""alter session set container=ORKPDB;
"""+rows('OLD_BASELINE','as of scn '+str(scn))+rows('FINAL')+"""alter session set container=CDB$ROOT;
select 'END_SCN|'||current_scn from v$database;
alter system archive log current;
select 'ARCHIVE|'||thread#||'|'||sequence#||'|'||first_change#||'|'||next_change#||'|'||name from v$archived_log where name is not null order by recid;
""")
start=int(re.search(r'^START_SCN\|(\d+)',pre,re.M)[1]);end=int(re.search(r'^END_SCN\|(\d+)',after,re.M)[1]);files=[]
for line in after.splitlines():
 a=line.strip().split('|')
 if len(a)!=6 or a[0]!='ARCHIVE' or int(a[4])<=start or int(a[3])>end:continue
 local=OUT/('archive-'+a[1]+'-'+a[2]+'.dbf');run('capture-'+a[2],['docker','cp',CONTAINER+':'+a[5],str(local)])
 files.append({'sequence':int(a[2]),'source':a[5],'path':local.name,'first_scn':int(a[3]),'next_scn':int(a[4]),'bytes':local.stat().st_size,'sha256':hashlib.sha256(local.read_bytes()).hexdigest()})
if len(files)!=2 or files[0]['next_scn']!=files[1]['first_scn']:raise RuntimeError('EXPECTED_TWO_CONTINUOUS_ARCHIVES; preserve committed actions')
trace=sql('dump',"alter session set tracefile_identifier='ORK_BLOCKING_ROWS';\n"+''.join("alter system dump logfile '"+f['source']+"' scn min "+str(start)+" scn max "+str(end)+";\n" for f in files)+"select value from v$diag_info where name='Default Trace File';")
paths=[x.strip() for x in trace.splitlines() if x.strip().startswith('/opt/oracle/') and x.strip().endswith('.trc')]
if len(paths)!=1:raise RuntimeError('TRACE_PATH_AMBIGUOUS')
run('copy-trace',['docker','cp',CONTAINER+':'+paths[0],str(OUT/'dump.trc')])
result={'a_xid':xid,'b_xid':re.search(r'B_TX\|([^\r\n]+)',btruth)[1],'start_scn':start,'snapshot_scn':scn,'end_scn':end,'acquisition_sha256':receipt['snapshot_sha256'],'files':files,'scope':'dedicated real same-row lock wait, no CDC authorization'}
(OUT/'manifest.json').write_text(json.dumps(result,indent=2)+'\n');print(json.dumps(result))
