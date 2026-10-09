"""One new owned temporal fixture, actual early V3, then frozen normal events. Never replay DML."""
from pathlib import Path
import hashlib,json,shutil,subprocess,sys,time
R=Path('D:/work/vibecode/OracleRedoKernel');E=R/'lab/out/d4-temporal-20261009/build-r2';N=R/'lab/out/d4-temporal-source-20261009'
S='oraredo-19c-lab-20261004';H='ork-product-runtime';TABLE='T_D4_TIME_20261009';INDEX='PK_D4_TIME_20261009';resume=False
assert sys.argv[1:]==['--execute'];assert (E/'candidate-proof.json').exists()
N.mkdir(exist_ok=False);shutil.copyfile(__file__,N/'executed.py')
common=(R/'lab/d4_raw_source.py').read_text();exec(common[common.index('sha=lambda'):common.index('libs=N/')])
libs=N/'libs';classes=N/'classes';libs.mkdir();classes.mkdir()
for name in ['kernel','adapter']:shutil.copyfile(E/(name+'.jar'),libs/(name+'.jar'))
old=R/'lab/out/d4-raw-source-20261009/parser-r4/libs'
for p in old.glob('*.jar'):
 if p.name not in ['kernel.jar','adapter.jar']:shutil.copyfile(p,libs/p.name)
shutil.copyfile(R/'lab/out/oracle-snapshot-acquisition-20261005/source.secret',N/'source.secret')
cp=str(libs/'*')+';'+str(classes)
run('compile',['D:/work/Java/jdk-17.0.7/bin/javac.exe','-cp',cp,'-d',classes,R/'lab/OracleSnapshotAcquisitionProbe.java',R/'lab/RawValueEventsProbe.java'])
started=False;helper=False
try:
 assert run('source-state',['docker','inspect',S,'--format','{{.Config.Labels.owner}}|{{.State.Status}}']).strip()=='OracleRedoKernel|exited'
 assert H not in run('helpers',['docker','ps','-a','--format','{{.Names}}']).splitlines()
 run('start',['docker','start',S]);started=True
 for attempt in range(30):
  try:
   pre=sql('ready-'+str(attempt),"select 'LOGGING|'||supplemental_log_data_min||'|'||supplemental_log_data_all from v$database;\nalter session set container=ORKPDB;\nselect 'FREE|'||count(*) from dba_tables where owner='ORKLAB' and table_name='"+TABLE+"';")
   if 'FREE|' in pre:break
  except AssertionError:
   if attempt==29:raise
  time.sleep(5)
 assert 'LOGGING|NO|NO' in pre and 'FREE|0' in pre
 sql('fixture',f"alter session set container=ORKPDB;\ncreate table ORKLAB.{TABLE}(ID number not null,D date,TS timestamp(9),constraint {INDEX} primary key(ID));\ninsert into ORKLAB.{TABLE} values(1,to_date('2024-02-29 23:59:58','YYYY-MM-DD HH24:MI:SS'),timestamp '2024-02-29 23:59:58.123456789');\ncommit;")
 reader=(N/'source.secret').read_text().splitlines()[0];assert reader.replace('_','').isalnum()
 sql('fixture-reader-grant',f"alter session set container=ORKPDB;\ngrant select,flashback on ORKLAB.{TABLE} to {reader};")
 helper=True
 acquisition=run('acquisition',['docker','run','--name',H,'--label','owner=OracleRedoKernel','--label','scope=temporal-acquisition','--network','container:'+S,'--mount','type=bind,source='+str(N)+',target=/evidence','--entrypoint','java','flink:1.18-java17','-cp','/evidence/classes:/evidence/libs/*','OracleSnapshotAcquisitionProbe','/evidence/source.secret','temporal-v3','ORKLAB',TABLE,INDEX,'history-normal','/evidence/baseline.snapshot'])
 parts=acquisition.strip().split('|');assert parts[:2]==['ACQUIRED','temporal-v3'] and parts[4:]==['3','1','0']
 baseline=N/'baseline.snapshot';assert sha(baseline)==parts[3]
 assert '|C102|787C021D183C3B|787C021D183C3B075BCD15' in baseline.read_text()
 (N/'baseline-reference.json').write_text(json.dumps(dict(path=str(baseline),sha256=parts[3],scn=int(parts[2]),acquisitionRc=0,sourceFixture=TABLE),indent=2))
 run('helper-logs',['docker','logs',H]);run('helper-remove',['docker','rm',H]);helper=False
 sql('seed-independent',f"alter session set container=ORKPDB;\nselect 'SEED|'||ID||'|'||dump(D,16)||'|'||dump(TS,16) from ORKLAB.{TABLE};")
 firstOut=sql('before-dml',"select 'CURRENT|'||sequence# from v$log where status='CURRENT';")
 first=int(next(line.split('|')[1] for line in firstOut.splitlines() if line.startswith('CURRENT|')))
 (N/'source-actions-started.txt').write_text('New temporal fixture only. Never replay source DML.\n')
 truth=sql('dml',f"""alter session set container=ORKPDB;
update ORKLAB.{TABLE} set D=to_date('2026-10-09 12:13:14','YYYY-MM-DD HH24:MI:SS'),TS=timestamp '2026-10-09 12:13:14.987654321' where ID=1;
commit;
select 'TRUTH|U1|'||ID||'|'||to_char(D,'YYYY-MM-DD"T"HH24:MI:SS')||'|'||to_char(TS,'YYYY-MM-DD"T"HH24:MI:SS.FF9')||'|'||dump(D,16)||'|'||dump(TS,16) from ORKLAB.{TABLE} where ID=1;
insert into ORKLAB.{TABLE} values(2,to_date('1900-02-28 00:00:00','YYYY-MM-DD HH24:MI:SS'),timestamp '1999-12-31 23:59:59');
commit;
select 'TRUTH|I2|'||ID||'|'||to_char(D,'YYYY-MM-DD"T"HH24:MI:SS')||'|'||to_char(TS,'YYYY-MM-DD"T"HH24:MI:SS.FF9')||'|'||dump(D,16)||'|'||dump(TS,16) from ORKLAB.{TABLE} where ID=2;
update ORKLAB.{TABLE} set D=null,TS=null where ID=2;
commit;
select 'TRUTH|U2|'||ID||'|'||nvl(to_char(D,'YYYY-MM-DD'),'NULL')||'|'||nvl(to_char(TS,'YYYY-MM-DD'),'NULL') from ORKLAB.{TABLE} where ID=2;
delete from ORKLAB.{TABLE} where ID=2;
commit;
update ORKLAB.{TABLE} set D=to_date('2025-01-01 00:00:00','YYYY-MM-DD HH24:MI:SS'),TS=timestamp '2025-01-01 00:00:00.000000001' where ID=1;
rollback;
select 'FINAL|'||ID||'|'||to_char(D,'YYYY-MM-DD"T"HH24:MI:SS')||'|'||to_char(TS,'YYYY-MM-DD"T"HH24:MI:SS.FF9') from ORKLAB.{TABLE};
select 'COUNT|'||count(*) from ORKLAB.{TABLE};
""")
 assert 'FINAL|1|2026-10-09T12:13:14|2026-10-09T12:13:14.987654321' in truth and 'COUNT|1' in truth and 'TRUTH|U2|2|NULL|NULL' in truth
 lastOut=sql('after-dml',"select 'CURRENT|'||sequence# from v$log where status='CURRENT';\nselect 'LOGGING|'||supplemental_log_data_min||'|'||supplemental_log_data_all from v$database;");assert 'LOGGING|NO|NO' in lastOut
 last=int(next(line.split('|')[1] for line in lastOut.splitlines() if line.startswith('CURRENT|')))
 sql('archive','alter system archive log current;');args=[baseline,parts[3]];archives=[]
 for seq in range(first,last+1):
  out=sql('archive-path-'+str(seq),f"select 'PATH|'||name from v$archived_log where sequence#={seq} and archived='YES' and deleted='NO' and name is not null order by recid;")
  paths={line.split('|',1)[1] for line in out.splitlines() if line.startswith('PATH|')};assert len(paths)==1
  path=paths.pop();local=N/(str(seq)+'.raw');run('copy-'+str(seq),['docker','cp',S+':'+path,local])
  assert run('hash-'+str(seq),['docker','exec',S,'sha256sum',path]).split()[0]==sha(local)
  args.extend([local,sha(local)]);archives.append(dict(sequence=seq,path=str(local),sha256=sha(local)))
 (N/'archives.json').write_text(json.dumps(archives,indent=2))
 run('events',['D:/work/Java/jdk-17.0.7/bin/java.exe','-Xmx3g','-cp',cp,'RawValueEventsProbe',*args])
 projected=json.loads((N/'events.stdout').read_text());events=projected['events']
 def row(i,d,ts):return dict(ID=i,D=None if d is None else dict(type='DATE',iso=d),TS=None if ts is None else dict(type='TIMESTAMP',iso=ts))
 seed=row(1,'2024-02-29T23:59:58','2024-02-29T23:59:58.123456789');final=row(1,'2026-10-09T12:13:14','2026-10-09T12:13:14.987654321');insert=row(2,'1900-02-28T00:00:00','1999-12-31T23:59:59.000000000');null=row(2,None,None)
 assert [(v['operation'],v['before'],v['after']) for v in events]==[('UPDATE',seed,final),('INSERT',None,insert),('UPDATE',insert,null),('DELETE',null,None)]
 assert len({v['eventId'] for v in events})==4
 (N/'normal-proof.json').write_text(json.dumps(dict(events=4,mismatches=0,rollbackEvents=0,baselineSha256=parts[3],archives=archives,leaf=projected['leaf'],sourceDmlReplayed=False,productIntegrated=False),indent=2))
finally:
 try:
  if helper:run('failed-helper-logs',['docker','logs',H]);run('failed-helper-remove',['docker','rm',H])
 finally:
  if started:run('stop',['docker','stop','-t','60',S])
