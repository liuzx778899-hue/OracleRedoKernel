"""Resume r88 after its path-only refusal; preserve provisioning and all failures."""
from pathlib import Path
import sys,json,hashlib,shutil,time,os
close_only=sys.argv[1:]==['close']
R=Path('D:/work/vibecode/OracleRedoKernel');label='producer-product-r90' if close_only else 'producer-product-r89';prefix='r90-' if close_only else 'r89-'
sys.argv=[str(R/'lab/d3_stream_runtime.py'),'inspect',label]
exec((R/'lab/d3_stream_runtime.py').read_text().split("if action=='prepare':")[0])
OLD_CTL=U;N=N/'producer-product-r88';U='ORKRECOVERYCTL_20261009'
T='ORKTGT_CMT_20261005.RPROD1';TARGET='oracle-recovery-RPROD1'
shutil.copyfile(R/'lab/producer_recovery_resume.py',N/(prefix+'executed.py'))
proof=json.loads((N/'product-proof.json').read_text());projection=json.loads((N/'projection.stdout').read_text());e=projection['evidence']
props=dict(line.split('=',1) for line in (N/'runtime.secret.env').read_text().splitlines());secrets.extend(v.encode() for k,v in props.items() if 'PASSWORD' in k and v)
inputs=[CAP/f'{i}.raw' if i<92 else R/f'lab/out/d3-stream-runtime-20261009/new-inputs/{i}.raw' for i in range(90,95)]
directory=N/'inputs';directory.mkdir(exist_ok=close_only)
for p in inputs:
 if close_only:assert os.path.samefile(p,directory/p.name)
 else:os.link(p,directory/p.name)
paths=['/evidence/inputs/'+p.name for p in inputs]
baseline=R/'lab/out/d2-blocks-runtime-20261008/snapshots/101673191742861312.snapshot'
props['SH_ORACLE_PREFIX_INPUT_DIRECTORY']='/evidence/inputs'
(N/(prefix+'runtime.secret.env')).write_text(''.join(k+'='+v+'\n' for k,v in props.items()))
prior_run,prior_sql,prior_http=run,sql,http
def run(name,*args,**kwargs):return prior_run(prefix+name,*args,**kwargs)
def sql(name,*args,**kwargs):return prior_sql(prefix+name,*args,**kwargs)
def http(name,*args,**kwargs):return prior_http(prefix+name,*args,**kwargs)
source=(R/'lab/producer_recovery_product.py').read_text()
exec('def clob(value):'+source.split('def clob(value):',1)[1].split('started=False;helper=False',1)[0])
started=False;helper=False
try:
 assert HELPER not in run('containers',['docker','ps','-a','--format','{{.Names}}']).splitlines()
 assert run('source-state',['docker','inspect',S,'--format','{{.State.Status}}']).strip()=='exited'
 run('source-start',['docker','start',S]);started=True
 for attempt in range(30):
  try:
   out=sql(f'ready-{attempt}',"select 'CURRENT|'||sequence# from v$log where status='CURRENT';\nselect 'LOGGING|'||supplemental_log_data_min||'|'||supplemental_log_data_all from v$database;\nalter session set container=ORKPDB;\nselect 'ANCHORS|'||count(*) from ORKLAB.T_D2_BLOCKS_20261008 where NOTE='anchor';")
   if 'ANCHORS|' in out:break
  except AssertionError:
   if attempt==29:raise
  time.sleep(5)
 assert 'CURRENT|95' in out and 'LOGGING|NO|NO' in out and 'ANCHORS|20' in out
 assert 'EMPTY|0' in sql('guard',f"alter session set container=ORKPDB;\nselect 'EMPTY|'||count(*) from {T}_APPLIED;")
 if close_only:
  partial=sql('partial-closure',f"alter session set container=ORKPDB;\nselect 'BINDING|'||count(*) from {U}.SH_ORACLE_INPUT_BINDING where ID=9901;\nselect 'WINDOW|'||count(*) from {U}.SH_CAP_WINDOW where ID=9902 and STATUS='PENDING';")
  assert 'BINDING|0' in partial
  if 'WINDOW|0' in partial:enroll(False)
  else:
   assert 'WINDOW|1' in partial
   binding=dict(baselinePath=None,capturePaths=paths,evidence=e,envelopesSha256=projection['envelopesSha256'],acquisitionId=101673191742861312,recovery=dict(prefixBindingId=9801),inputFormat='RAW_MEMBER')
   sql('closed-binding-only',f"alter session set container=ORKPDB;\ninsert into {U}.SH_ORACLE_INPUT_BINDING values(9901,'{SOURCE}','recovery-closed',1,5,'v3-history-consumer','{e['schemaSha256']}','APPROVED_BOUNDED',"+clob(binding)+",systimestamp,'owned-recovery-normal');\ncommit;")
 run('helper-start',['docker','run','-d','--name',HELPER,'--label','owner=OracleRedoKernel','--label','scope=oracle-product-runtime','--user','0','--network','container:'+S,'--workdir','/evidence','--env-file',N/(prefix+'runtime.secret.env'),'--mount','type=bind,source='+str(N)+',target=/evidence','--mount','type=bind,source='+str(baseline.parent)+',target=/evidence/snapshots,readonly','--entrypoint','java','flink:1.18-java17','-Xmx1536m','-jar','/evidence/application.jar']);helper=True
 for attempt in range(40):
  try:http(f'health-{attempt}','/system/info');break
  except (AssertionError,ValueError):
   if attempt==39:raise
   time.sleep(3)
 assert run('running-sha',['docker','exec',HELPER,'sha256sum','/evidence/application.jar']).split()[0]==proof['applicationSha256']
 if not close_only:
  binding=dict(acquisitionId=101673191742861312,leaf=50359491,captures=[dict(path=paths[i],sha256=sha(p),inputFormat='RAW_MEMBER') for i,p in enumerate(inputs[:4])],checkpoint=None)
  sql('repair-scope',f"alter session set container=ORKPDB;\nbegin update {U}.SH_ORACLE_INPUT_BINDING set BINDING_JSON="+clob(binding)+f" where ID=9801 and STATE='APPROVED_PREFIX';if sql%rowcount!=1 then raise_application_error(-20001,'prefix state changed');end if;update {U}.SH_CAP_WINDOW set HEAD_PATH='{paths[0]}' where ID=9802 and STATUS='PENDING';if sql%rowcount!=1 then raise_application_error(-20002,'window state changed');end if;end;\n/\ncommit;")
  receipt=http('checkpoint','/oracle/prefixes/9801/checkpoint',{})
 else:receipt=json.loads((N/'r89-r89-checkpoint.stdout').read_text())['data']
 state=N/'prefix-state'/Path(receipt['checkpoint']['path']).name
 assert state.read_bytes().startswith(b'ORK_HISTORICAL_RECOVERY_V2\n') and sha(state)==receipt['checkpoint']['sha256']
 if not close_only:enroll(False)
 for attempt in range(60):
  status=sql(f'closed-{attempt}',f"alter session set container=ORKPDB;\nselect 'CLOSED|'||STATE||'|'||RC||'|'||EVENT_COUNT from {U}.SH_PIPELINE_BATCH where WINDOW_KEY='recovery-closed';")
  if 'CLOSED|PUBLISHED|0|46' in status:break
  if '|FAILED|' in status:raise AssertionError(status)
  time.sleep(2)
 else:raise AssertionError('normal publication timeout')
 # Reuse the unchanged itemized target/checkpoint/ACK checks from the initial execution package.
 exec("actual=sql('actual',"+source.split(" actual=sql('actual',",1)[1].split('\nfinally:',1)[0].replace('\n ', '\n'))
finally:
 if helper:
  run('helper-logs',['docker','logs',HELPER]);run('helper-stop',['docker','stop','-t','20',HELPER]);run('helper-remove',['docker','rm',HELPER])
 if started:run('source-stop',['docker','stop','-t','60',S])
