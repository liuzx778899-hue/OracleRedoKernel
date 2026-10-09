"""One new owned RAW fixture and early V3 acquisition; never replay source DML."""
from pathlib import Path
import hashlib,json,shutil,subprocess,sys,time
R=Path('D:/work/vibecode/OracleRedoKernel');E=R/'lab/out/d4-raw-20261009';N=R/'lab/out/d4-raw-source-20261009'
S='oraredo-19c-lab-20261004';H='ork-product-runtime';TABLE='T_D4_RAW_20261009';INDEX='PK_D4_RAW_20261009'
resume=sys.argv[1:]==['--resume-acquisition']
assert resume or sys.argv[1:]==['--execute']
if resume:
 assert not (N/'executed-resume.py').exists(),'use a new recovery action; never overwrite an executed driver'
 assert (N/'acquisition.rc').read_text()=='1' and (N/'stop.rc').read_text()=='0'
 assert not (N/'source-actions-started.txt').exists() and not (N/'baseline.snapshot').exists()
 shutil.copyfile(__file__,N/'executed-resume.py')
else:
 N.mkdir(exist_ok=False);shutil.copyfile(__file__,N/'executed.py')
sha=lambda p:hashlib.sha256(Path(p).read_bytes()).hexdigest()
secret=(R/'lab/out/oracle-snapshot-acquisition-20261005/source.secret').read_text().splitlines()[1].encode()
def run(name,args,data=None):
 if resume:name='resume-'+name
 assert not (N/(name+'.rc')).exists();args=list(map(str,args));(N/(name+'.command.json')).write_text(json.dumps(args))
 p=subprocess.run(args,input=data,capture_output=True,timeout=300)
 for suffix,value in [('stdout',p.stdout),('stderr',p.stderr)]:(N/(name+'.'+suffix)).write_bytes(value.replace(secret,b'<redacted-secret>'))
 (N/(name+'.rc')).write_text(str(p.returncode));assert p.returncode==0,(name,p.returncode)
 return p.stdout.decode()
def sql(name,body):
 text='whenever sqlerror exit failure rollback\nwhenever oserror exit failure rollback\nset pages 0 heading off feedback off lines 32767 trimspool on\n'+body+'\nexit success\n'
 (N/((('resume-' if resume else '')+name)+'.sql')).write_text(text);out=run(name,['docker','exec','-i',S,'sqlplus','-s','/','as','sysdba'],text.encode());assert 'SP2-' not in out;return out
libs=N/'libs';classes=N/'classes'
old=R/'lab/out/oracle-snapshot-acquisition-20261005'
if not resume:
 libs.mkdir();classes.mkdir()
 for name in ['kernel','adapter']:shutil.copyfile(E/(name+'.jar'),libs/(name+'.jar'))
 shutil.copyfile(old/'ojdbc8.jar',libs/'ojdbc8.jar')
 shutil.copyfile(R/'lab/out/d3-stream-runtime-20261009/producer-product-r109/libs/streamhouse-spi-0.1.0-SNAPSHOT.jar',libs/'spi.jar')
 for p in (R/'lab/out/d3-stream-runtime-20261009/producer-product-r109/libs').glob('jackson-*.jar'):shutil.copyfile(p,libs/p.name)
 shutil.copyfile(old/'source.secret',N/'source.secret')
else:
 assert (N/'compile.rc').read_text()=='0'
 for name in ['kernel','adapter']:assert sha(E/(name+'.jar'))==sha(libs/(name+'.jar'))
cp=str(libs/'*')+';'+str(classes)
if not resume:run('compile',['D:/work/Java/jdk-17.0.7/bin/javac.exe','-cp',cp,'-d',classes,R/'lab/OracleSnapshotAcquisitionProbe.java',R/'lab/RawValueEventsProbe.java'])
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
 assert 'LOGGING|NO|NO' in pre and ('FREE|1' if resume else 'FREE|0') in pre
 if not resume:sql('fixture',f"alter session set container=ORKPDB;\ncreate table ORKLAB.{TABLE}(ID number not null,BIN raw(256),constraint {INDEX} primary key(ID));\ninsert into ORKLAB.{TABLE} values(1,hextoraw('00FF80C328'));\ncommit;")
 else:
  seed=sql('seed',f"alter session set container=ORKPDB;\nselect 'SEED|'||ID||'|'||rawtohex(BIN) from ORKLAB.{TABLE};")
  assert [line for line in seed.splitlines() if line.startswith('SEED|')]==['SEED|1|00FF80C328']
 reader=(N/'source.secret').read_text().splitlines()[0]
 assert reader.replace('_','').isalnum()
 sql('fixture-reader-grant',f"alter session set container=ORKPDB;\ngrant select,flashback on ORKLAB.{TABLE} to {reader};")
 helper=True
 acquisition=run('acquisition',['docker','run','--name',H,'--label','owner=OracleRedoKernel','--label','scope=raw-acquisition','--network','container:'+S,'--mount','type=bind,source='+str(N)+',target=/evidence','--entrypoint','java','flink:1.18-java17','-cp','/evidence/classes:/evidence/libs/*','OracleSnapshotAcquisitionProbe','/evidence/source.secret','raw-v3','ORKLAB',TABLE,INDEX,'history-normal','/evidence/baseline.snapshot'])
 parts=acquisition.strip().split('|');assert parts[:2]==['ACQUIRED','raw-v3'] and parts[4:]==['2','1','0']
 baseline=N/'baseline.snapshot';assert sha(baseline)==parts[3]
 (N/'baseline-reference.json').write_text(json.dumps(dict(path=str(baseline),sha256=parts[3],scn=int(parts[2]),acquisitionRc=0,sourceFixture=TABLE),indent=2))
 run('helper-logs',['docker','logs',H]);run('helper-remove',['docker','rm',H]);helper=False
 pre=sql('before-dml',"select 'CURRENT|'||sequence# from v$log where status='CURRENT';")
 first=int(next(line.split('|')[1] for line in pre.splitlines() if line.startswith('CURRENT|')))
 (N/'source-actions-started.txt').write_text('New RAW case only. Never replay source DML.\n')
 truth=sql('dml',f"""alter session set container=ORKPDB;
update ORKLAB.{TABLE} set BIN=hextoraw('010080FF') where ID=1;
commit;
select 'TRUTH|U1|'||ID||'|'||rawtohex(BIN)||'|'||dump(BIN,16) from ORKLAB.{TABLE} where ID=1;
insert into ORKLAB.{TABLE} values(2,hextoraw('C32800FF'));
commit;
select 'TRUTH|I2|'||ID||'|'||rawtohex(BIN)||'|'||dump(BIN,16) from ORKLAB.{TABLE} where ID=2;
update ORKLAB.{TABLE} set BIN=null where ID=2;
commit;
select 'TRUTH|U2|'||ID||'|'||nvl(rawtohex(BIN),'NULL') from ORKLAB.{TABLE} where ID=2;
delete from ORKLAB.{TABLE} where ID=2;
commit;
update ORKLAB.{TABLE} set BIN=hextoraw('FFFFFFFF') where ID=1;
rollback;
select 'FINAL|'||ID||'|'||rawtohex(BIN) from ORKLAB.{TABLE};
select 'COUNT|'||count(*) from ORKLAB.{TABLE};
""")
 assert 'FINAL|1|010080FF' in truth and 'COUNT|1' in truth and 'TRUTH|U2|2|NULL' in truth
 lastOut=sql('after-dml',"select 'CURRENT|'||sequence# from v$log where status='CURRENT';\nselect 'LOGGING|'||supplemental_log_data_min||'|'||supplemental_log_data_all from v$database;")
 assert 'LOGGING|NO|NO' in lastOut
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
 projected=json.loads((N/(('resume-' if resume else '')+'events.stdout')).read_text());events=projected['events']
 expected=[('UPDATE',{'ID':1,'BIN':{'hex':'00ff80c328'}},{'ID':1,'BIN':{'hex':'010080ff'}}),('INSERT',None,{'ID':2,'BIN':{'hex':'c32800ff'}}),('UPDATE',{'ID':2,'BIN':{'hex':'c32800ff'}},{'ID':2,'BIN':None}),('DELETE',{'ID':2,'BIN':None},None)]
 assert [(event['operation'],event['before'],event['after']) for event in events]==expected
 assert len({event['eventId'] for event in events})==4
 (N/'normal-proof.json').write_text(json.dumps(dict(events=4,mismatches=0,rollbackEvents=0,sourceFixture=TABLE,sourceDmlReplayed=False,productIntegrated=False,baselineSha256=parts[3],archives=archives,leaf=projected['leaf']),indent=2))
finally:
 try:
  if helper:
   run('failed-helper-logs',['docker','logs',H]);run('failed-helper-remove',['docker','rm',H])
 finally:
  if started:run('stop',['docker','stop','-t','60',S])
