"""Finish only the already queued member after the observer timeout; no replay."""
from pathlib import Path
import sys,time,json,subprocess
R=Path(__file__).resolve().parent.parent
sys.argv=[str(R/'lab/d3_stream_runtime.py'),'inspect','d3-retention-finish-20261010']
exec((R/'lab/d3_stream_runtime.py').read_text().split("if action=='prepare':")[0])
N=R/'lab/out/d3-retention-20261010';O=R/'lab/out/d3-online-product-20261010'
assert not (N/'executed-finish.py').exists();shutil.copyfile(__file__,N/'executed-finish.py')
original_run=run
def run(name,args,data=None,private=False):return original_run('finish-'+name,args,data,private)
props=dict(line.split('=',1) for line in (N/'runtime.secret.env').read_text().splitlines())
secrets.extend(v.encode() for k,v in props.items() if 'PASSWORD' in k and v)
secrets.append((N/'machine.secret.env').read_text().strip().split('=',1)[1].encode())
source='oracle-online-normal';raw=O/'raw-ready/raw-members'/source
first='f2ba9de4e018da2a4e4753a047e70439d3de4e98fd444cd0d2249aa6c99b1820'
second='3112c82f72e267174eef1e4cd27748c661e530df4472470e2c8573dc9994a8d5'
provenance=json.loads((N/'partial-provenance.json').read_text())
candidate=json.loads((N/'candidate.json').read_text())
acks={p.name:sha(p) for p in (O/'acks').glob('*.ack.json')}
previous=(N/'collector.stdout').read_text()
assert 'MEMBER_SPOOL_FULL' in previous
assert 'original='+first+'.raw stored_sha256='+first in previous
assert previous.index('original='+first+'.raw')<previous.index('raw queued: sha256='+second)
assert sha(N/'spool'/source/(second+'.raw'))==second
started=False;helper=False;collector=None
try:
 assert HELPER not in run('containers',['docker','ps','-a','--format','{{.Names}}']).splitlines()
 assert run('source-owner',['docker','inspect',S,'--format','{{.Config.Labels.owner}}|{{.State.Status}}']).strip()=='OracleRedoKernel|exited'
 run('source-start',['docker','start',S]);started=True
 for i in range(30):
  try:
   if 'READY|1' in sql('ready-'+str(i),"alter session set container=ORKPDB;\nselect 'READY|'||count(*) from ORKLAB.T_D2_BLOCKS_20261008 where ID=0 and NOTE='online-next';"):break
  except AssertionError:
   if i==29:raise
  time.sleep(3)
 else:raise AssertionError('SOURCE_NOT_READY')
 run('helper-start',['docker','run','-d','--name',HELPER,'--label','owner=OracleRedoKernel','--label','scope=oracle-retention-finish','--user','0','--network','container:'+S,'--env-file',N/'runtime.secret.env','--mount','type=bind,source='+str(O)+',target=/evidence','--mount','type=bind,source='+str(N/'ack-test')+',target=/evidence/acks','--mount','type=bind,source='+str(N)+',target=/retention','--mount','type=bind,source='+str(raw)+',target=/members,readonly','--entrypoint','java','flink:1.18-java17','-Xmx1536m','-jar','/retention/application.jar']);helper=True
 for i in range(40):
  try:http('health-'+str(i),'/system/info');break
  except (AssertionError,ValueError):
   if i==39:raise
   time.sleep(3)
 with (N/'finish-collector.stdout').open('xb') as out,(N/'finish-collector.stderr').open('xb') as err:
  collector=subprocess.Popen(['docker','exec',HELPER,'/retention/run-collector.sh'],stdout=out,stderr=err)
  for i in range(180):
   assert collector.poll() is None
   if 'original='+second+'.raw stored_sha256='+second in (N/'finish-collector.stdout').read_text():break
   time.sleep(2)
  else:raise AssertionError('QUEUED_MEMBER_CUSTODY_TIMEOUT')
  pid=run('collector-pid',['docker','exec',HELPER,'cat','/retention/collector.pid']).strip();assert pid.isdecimal()
  assert run('collector-owner',['docker','exec',HELPER,'cat','/proc/'+pid+'/cmdline']).split('\0')[0]=='/retention/sh_agent'
  run('collector-stop',['docker','exec',HELPER,'kill','-TERM',pid]);assert collector.wait(timeout=60)==0;collector=None
 actual=sql('actual',"alter session set container=ORKPDB;\nselect 'COUNTS|'||(select count(*) from ORKTGT_CMT_20261005.RONL1_APPLIED)||'|'||(select count(*) from ORKTGT_CMT_20261005.RONL1_MEMBERS)||'|'||(select count(*) from ORKTGT_CMT_20261005.RONL1_COMMITS)||'|'||(select count(*) from ORKONLINECTL_20261010.SH_EVENT_LEDGER) from dual;\nselect 'DIAGNOSTIC|'||STATUS||'|'||TOTAL_SHA256||'|'||RECEIVED_OFFSET||'|'||TOTAL_LENGTH from ORKONLINECTL_20261010.SH_TRANSFER_SESSION where INPUT_FORMAT='ORACLE_DIAGNOSTIC';")
 assert 'COUNTS|2|2|2|2' in actual and 'DIAGNOSTIC|FINISHED|'+provenance['originalSha256'] in actual
 assert not any(p.suffix in ['.raw','.partial'] for p in (N/'spool'/source).iterdir())
 assert all(sha(raw/(d+'.raw'))==d for d in [first,second])
 assert {p.name:sha(p) for p in (N/'ack-test').glob('*.ack.json')}==acks
 assert sha(O/'raw-ready/oracle-diagnostics'/source/(provenance['originalSha256']+'.partial'))==provenance['originalSha256']
 assert run('original-partial-sha',['docker','exec',S,'sha256sum',provenance['originalSourcePath']]).split()[0]==provenance['originalSha256']
 floor=json.loads((N/'floor.stdout').read_text())['data'];assert floor['centralDeleteAllowed'] is False
 assert json.loads((N/'missing-ack.stdout').read_text())['code']!=0
 (N/'normal-proof.json').write_text(json.dumps(dict(candidate=candidate,retention=floor,sourceDml=0,newAcquisitions=0,newEvents=0,targetAndLedger=[2,2,2,2],originalAckUnchanged=True,centralRawRetained=True,oldFailureRetained=True,sourceCopiesReleased=3,capacityBytes=230686720,finalQueuedBytes=0,diagnosticSha256=provenance['originalSha256'],samePidCapacityReleaseAndNextQueue=True,firstCollectorPid=(N/'cleanup-pid.stdout').read_text().strip(),finishingCollectorPid=pid,collectorRuns=2,observationTimeout='NORMAL_CUSTODY_BACKPRESSURE_TIMEOUT',queuedMemberFinishedWithoutRecopy=True),indent=2))
finally:
 try:
  if collector is not None and collector.poll() is None:
   pid=run('cleanup-pid',['docker','exec',HELPER,'cat','/retention/collector.pid']).strip();assert pid.isdecimal()
   run('cleanup-collector',['docker','exec',HELPER,'kill','-TERM',pid]);collector.wait(timeout=60)
  if helper:run('logs',['docker','logs',HELPER]);run('helper-stop',['docker','stop','-t','20',HELPER]);run('helper-remove',['docker','rm',HELPER])
 finally:
  if started:run('source-stop',['docker','stop','-t','60',S])
