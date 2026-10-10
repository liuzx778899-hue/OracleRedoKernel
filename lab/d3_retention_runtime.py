"""One frozen-member capacity/custody loop and verified retention floor; no source DML."""
from pathlib import Path
import os,sys,json,shutil,subprocess,time
R=Path(__file__).resolve().parent.parent;label='d3-retention-normal-20261010'
sys.argv=[str(R/'lab/d3_stream_runtime.py'),'inspect',label]
exec((R/'lab/d3_stream_runtime.py').read_text().split("if action=='prepare':")[0])
N=R/'lab/out/d3-retention-20261010';O=R/'lab/out/d3-online-product-20261010'
assert not (N/'executed-runtime.py').exists();shutil.copyfile(__file__,N/'executed-runtime.py')
candidate=json.loads((N/'candidate.json').read_text());assert sha(N/'application.jar')==candidate['applicationSha256'] and sha(N/'sh_agent')==candidate['agentSha256']
props=dict(line.split('=',1) for line in (O/'runtime.secret.env').read_text().splitlines())
secrets.extend(v.encode() for k,v in props.items() if 'PASSWORD' in k and v)
token=(O/'machine.secret.env').read_text().strip().split('=',1)[1];secrets.append(token.encode())
props.update(SH_ORACLE_RAW_DISCOVERY_ENABLED='false',SH_PIPELINE_AUTO_CONSUME_ENABLED='false')
(N/'runtime.secret.env').write_bytes(''.join(k+'='+v+'\n' for k,v in props.items()).encode())
shutil.copytree(O/'acks',N/'ack-test')
source='oracle-online-normal';raw=O/'raw-ready/raw-members'/source
first='f2ba9de4e018da2a4e4753a047e70439d3de4e98fd444cd0d2249aa6c99b1820'
second='3112c82f72e267174eef1e4cd27748c661e530df4472470e2c8573dc9994a8d5'
for digest in [first,second]:assert sha(raw/(digest+'.raw'))==digest
acks={p.name:sha(p) for p in (O/'acks').glob('*.ack.json')};limit=230686720
(N/'spool'/source).mkdir(parents=True)
config=f'[agent]\nsource_id={source}\n[oracle]\nmember_count=2\nmember_1=/members/{first}.raw\nmember_2=/members/{second}.raw\nspool_dir=/retention/spool\npoll_interval_ms=1000\nmax_spool_bytes={limit}\n[transfer]\nbase_url=http://127.0.0.1:{PORT}\n'
(N/'collector.ini').write_bytes(config.encode())
(N/'machine.secret.env').write_bytes(('SH_AGENT_MACHINE_TOKEN='+token+'\n').encode())
(N/'run-collector.sh').write_bytes(b'#!/bin/sh\nset -eu\nset -a\n. /retention/machine.secret.env\nset +a\necho $$ > /retention/collector.pid\nexec /retention/sh_agent --collect-oracle /retention/collector.ini\n')
def machine(name,path,body,expected=0):
 code="""import json,urllib.request,urllib.error
request=urllib.request.Request(URL,data=json.dumps(BODY).encode(),headers={'Content-Type':'application/json','X-SH-Agent-Token':TOKEN})
try:
 with urllib.request.urlopen(request,timeout=180) as response:print(response.read().decode())
except urllib.error.HTTPError as ex:print(ex.read().decode())
""".replace('URL',repr(f'http://127.0.0.1:{PORT}/api/v1'+path)).replace('BODY',repr(body)).replace('TOKEN',repr(token))
 result=json.loads(run(name,['docker','exec','-i',S,'python3','-'],code.encode()));assert (result['code']==0)==(expected==0),result
 return result.get('data')
started=False;helper=False;collector=None
try:
 assert HELPER not in run('containers',['docker','ps','-a','--format','{{.Names}}']).splitlines()
 assert run('source-owner',['docker','inspect',S,'--format','{{.Config.Labels.owner}}|{{.State.Status}}']).strip()=='OracleRedoKernel|exited'
 run('source-start',['docker','start',S]);started=True
 for i in range(30):
  try:
   if 'READY|1' in sql(f'ready-{i}',"alter session set container=ORKPDB;\nselect 'READY|'||count(*) from ORKLAB.T_D2_BLOCKS_20261008 where ID=0 and NOTE='online-next';"):break
  except AssertionError:
   if i==29:raise
  time.sleep(3)
 else:raise AssertionError('SOURCE_NOT_READY')
 # Preserve the old failure bytes in place; only a new copied source queue is released.
 paths=run('old-partials',['docker','exec',S,'find','/tmp/ork-online-product-20261010/spool/'+source,'-maxdepth','1','-type','f','-name','*.partial']).splitlines()
 assert paths,'reuse the actual old copy failure, do not manufacture a new source failure'
 original=sorted(paths)[0];partial=N/'spool'/source/Path(original).name
 run('copy-failed-partial',['docker','cp',S+':'+original,partial]);partial_sha=sha(partial);partial_bytes=partial.stat().st_size;assert partial_bytes>0
 assert run('original-partial-sha',['docker','exec',S,'sha256sum',original]).split()[0]==partial_sha
 (N/'partial-provenance.json').write_text(json.dumps(dict(originalSourcePath=original,originalSha256=partial_sha,copiedName=partial.name,bytes=partial.stat().st_size),indent=2))
 run('helper-start',['docker','run','-d','--name',HELPER,'--label','owner=OracleRedoKernel','--label','scope=oracle-retention-normal','--user','0','--network','container:'+S,'--env-file',N/'runtime.secret.env','--mount','type=bind,source='+str(O)+',target=/evidence','--mount','type=bind,source='+str(N/'ack-test')+',target=/evidence/acks','--mount','type=bind,source='+str(N)+',target=/retention','--mount','type=bind,source='+str(raw)+',target=/members,readonly','--entrypoint','java','flink:1.18-java17','-Xmx1536m','-jar','/retention/application.jar']);helper=True
 for i in range(40):
  try:http(f'health-{i}','/system/info');break
  except (AssertionError,ValueError):
   if i==39:raise
   time.sleep(3)
 before=sql('before',"alter session set container=ORKPDB;\nselect 'COUNTS|'||(select count(*) from ORKTGT_CMT_20261005.RONL1_APPLIED)||'|'||(select count(*) from ORKTGT_CMT_20261005.RONL1_MEMBERS)||'|'||(select count(*) from ORKTGT_CMT_20261005.RONL1_COMMITS)||'|'||(select count(*) from ORKONLINECTL_20261010.SH_EVENT_LEDGER) from dual;")
 assert 'COUNTS|2|2|2|2' in before
 floor=machine('floor','/transfer/oracle/retention',{});assert (int(floor['requiredRawFirstSequence']),int(floor['decodedThroughSequence']),floor['centralDeleteAllowed'])==(100,101,False)
 # A missing copied ACK must not yield a retention receipt; restore the same bytes.
 last=json.loads((O/'normal-online-proof.json').read_text())['proofs'][-1]['checkpoint']['lastWindowKey']
 import hashlib
 ack=N/'ack-test'/(hashlib.sha256((source+'\0'+last).encode()).hexdigest()+'.ack.json');held=ack.with_suffix('.held')
 ack.rename(held)
 try:machine('missing-ack','/transfer/oracle/retention',{},expected=1)
 finally:held.rename(ack)
 run('collector-permissions',['docker','exec',HELPER,'chmod','700','/retention/sh_agent','/retention/run-collector.sh','/retention/machine.secret.env'])
 with (N/'collector.stdout').open('xb') as out,(N/'collector.stderr').open('xb') as err:
  collector=subprocess.Popen(['docker','exec',HELPER,'/retention/run-collector.sh'],stdout=out,stderr=err)
  samples=[]
  for i in range(180):
   assert collector.poll() is None,'same collector must remain live through backpressure and release'
   queued=0;inodes=set()
   for p in (N/'spool'/source).iterdir():
    if p.suffix not in ['.raw','.partial']:continue
    try:stat=p.stat()
    except FileNotFoundError:continue
    identity=(stat.st_dev,stat.st_ino)
    if identity not in inodes:queued+=stat.st_size;inodes.add(identity)
   samples.append(queued)
   text=(N/'collector.stdout').read_text()
   released=[('original='+first+'.raw stored_sha256='+first),('original='+second+'.raw stored_sha256='+second),
             ('original='+partial.name+' stored_sha256='+partial_sha)]
   if all(value in text for value in released) and 'MEMBER_SPOOL_FULL' in text:break
   time.sleep(2)
  else:raise AssertionError('NORMAL_CUSTODY_BACKPRESSURE_TIMEOUT')
  pid=run('collector-pid',['docker','exec',HELPER,'cat','/retention/collector.pid']).strip();assert pid.isdecimal()
  assert run('collector-owner',['docker','exec',HELPER,'cat','/proc/'+pid+'/cmdline']).split('\0')[0]=='/retention/sh_agent'
  run('collector-stop',['docker','exec',HELPER,'kill','-TERM',pid]);assert collector.wait(timeout=60)==0;collector=None
 # The diagnostic cannot become a RAW_MEMBER discovery input or window.
 actual=sql('actual',"alter session set container=ORKPDB;\nselect 'COUNTS|'||(select count(*) from ORKTGT_CMT_20261005.RONL1_APPLIED)||'|'||(select count(*) from ORKTGT_CMT_20261005.RONL1_MEMBERS)||'|'||(select count(*) from ORKTGT_CMT_20261005.RONL1_COMMITS)||'|'||(select count(*) from ORKONLINECTL_20261010.SH_EVENT_LEDGER) from dual;\nselect 'DIAGNOSTIC|'||STATUS||'|'||TOTAL_SHA256||'|'||RECEIVED_OFFSET||'|'||TOTAL_LENGTH from ORKONLINECTL_20261010.SH_TRANSFER_SESSION where INPUT_FORMAT='ORACLE_DIAGNOSTIC';")
 assert 'COUNTS|2|2|2|2' in actual and 'DIAGNOSTIC|FINISHED|'+partial_sha in actual
 assert not any(p.suffix in ['.raw','.partial'] for p in (N/'spool'/source).iterdir())
 assert all(sha(raw/(d+'.raw'))==d for d in [first,second])
 assert {p.name:sha(p) for p in (O/'acks').glob('*.ack.json')}==acks and {p.name:sha(p) for p in (N/'ack-test').glob('*.ack.json')}==acks
 assert sha(O/'raw-ready/oracle-diagnostics'/source/(partial_sha+'.partial'))==partial_sha
 assert run('original-partial-final-sha',['docker','exec',S,'sha256sum',original]).split()[0]==partial_sha
 (N/'normal-proof.json').write_text(json.dumps(dict(candidate=candidate,retention=floor,sourceDml=0,newAcquisitions=0,newEvents=0,originalTarget=[2,2,2],originalAckUnchanged=True,centralRawRetained=True,oldFailureRetained=True,sourceCopiesReleased=3,sameCollectorPid=pid,maxQueuedSample=max(samples),capacityBytes=limit,finalQueuedBytes=0,diagnosticSha256=partial_sha),indent=2))
finally:
 try:
  if collector is not None and collector.poll() is None:
   pid=run('cleanup-pid',['docker','exec',HELPER,'cat','/retention/collector.pid']).strip();assert pid.isdecimal()
   run('cleanup-collector',['docker','exec',HELPER,'kill','-TERM',pid]);collector.wait(timeout=60)
  if helper:run('logs',['docker','logs',HELPER]);run('helper-stop',['docker','stop','-t','20',HELPER]);run('helper-remove',['docker','rm',HELPER])
 finally:
  if started:run('source-stop',['docker','stop','-t','60',S])
