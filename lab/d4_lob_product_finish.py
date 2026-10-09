"""Finish the already frozen SecureFile input. Never acquire again or execute source DML."""
from pathlib import Path
import os,sys,json,hashlib,shutil,time,zipfile
retry=sys.argv[1:]==['--resume-script'];assert retry or len(sys.argv)==1
revision='r21' if retry else 'r20'
R=Path('D:/work/vibecode/OracleRedoKernel');sys.argv=['driver','inspect','lobfinish20261010'+('r21' if retry else '')]
exec((R/'lab/d3_stream_runtime.py').read_text().split("if action=='prepare':")[0])
N=R/'lab/out/d4-lob-product-20261010';execution=N/('executed-finish-r21.py' if retry else 'executed-finish.py');assert not execution.exists();shutil.copyfile(__file__,execution)
if retry:assert (N/'runtime-r20.rc').read_text().strip()=='1'
assert (N/'runtime-r16.rc').read_text().strip()=='1' and (N/'S-source-action.started').exists()
assert (N/'r16-S-source-action.rc').read_text().strip()=='0' and (N/'r18-S-preparation.rc').read_text().strip()=='0'
assert (N/'product-package-r19.rc').read_text().strip()=='0'
props=dict(line.split('=',1) for line in (N/'runtime.secret.env').read_text().splitlines());secrets.extend(v.encode() for k,v in props.items() if 'PASSWORD' in k and v)
prior_run=run
def run(name,args,data=None,private=False):return prior_run(revision+'-'+name,args,data,private)
app=N/'application-r19.jar';candidate=json.loads((N/'candidate-r19.json').read_text());assert sha(app)==candidate['application']
libs=N/'libs-r19'
if not retry:
 libs.mkdir()
 with zipfile.ZipFile(app) as archive:
  for name in archive.namelist():
   if name.startswith('BOOT-INF/lib/') and name.endswith('.jar'):(libs/Path(name).name).write_bytes(archive.read(name))
for name,field in [('oracle-redo-kernel','kernel'),('streamhouse-engine-oracle','adapter')]:
 assert sha(libs/(name+'-0.1.0-lob-trust-'+candidate[field][:12]+'.jar'))==candidate[field]
cp=str(N)+os.pathsep+os.pathsep.join(map(str,libs.glob('*.jar')))
old=R/'lab/out/d4-lob-source-20261009/continue-r2/97.raw'
b=json.loads((N/'r13-B-acquire.stdout').read_text())['data']
reproduced=json.loads((N/'r20-B-candidate-projection.stdout').read_text()) if retry else json.loads(run('B-candidate-projection',['D:/work/Java/jdk-17.0.7/bin/java.exe','-Xmx1536m','-cp',cp,'LobProductPreparationProbe',N/'snapshots'/f"{b['id']}.snapshot",b['snapshotSha256'],N/'inputs/97.raw',N/'inputs/98.raw']))
assert reproduced==json.loads((N/'r14-B-preparation.stdout').read_text())
proof=json.loads((N/'B-normal-proof.json').read_text());proof['applicationSha256']=sha(N/'application.jar');proof['finalCandidateFrozenProjectionEqual']=True;proofs=[proof]
kind='S';U='ORKLOBSCTL_20261010';SOURCE='oracle-d4-lob-s';prefix='RLOBS1';T='ORKTGT_CMT_20261005.'+prefix;TARGET='oracle-lob-'+prefix
acquired=json.loads((N/'r16-S-acquire.stdout').read_text())['data'];ACQ=int(acquired['id']);baseline=N/'snapshots'/f'{ACQ}.snapshot'
assert sha(baseline)==acquired['snapshotSha256']
captures=[N/'inputs'/f'{seq}.raw' for seq in [97,98,99]];prepared=json.loads((N/'r18-S-preparation.stdout').read_text());e=prepared['evidence']
for file,item in zip(captures,e['captures']):assert sha(file)==item['journalSha256']
identity=':'.join(str(e['source'][k]) for k in ['dbid','activationId','compatibility','thread','resetlogsId','resetlogsScn','containerUid','containerId'])
def clob(value):
 text=json.dumps(value,separators=(',',':'))
 return '||\n'.join("to_clob('"+text[i:i+1800].replace("'","''")+"')" for i in range(0,len(text),1800))
started=False;helper=False
# Compile the extracted publication body before starting any resource; multiline SQL has zero indentation.
body=(R/'lab/d4_lob_product.py').read_text();body=body[body.index("  sql(kind+'-target-ledger'"):body.index(" (N/'normal-product-proof.json')")]
body='\n'.join(line[2:] if line.startswith('  ') else line for line in body.splitlines())
(N/('publication-body-'+revision+'.py')).write_text(body)
publication=compile(body,'publication-body-'+revision+'.py','exec')
try:
 assert HELPER not in run('containers',['docker','ps','-a','--format','{{.Names}}']).splitlines()
 assert run('source-state',['docker','inspect',S,'--format','{{.Config.Labels.owner}}|{{.State.Status}}']).strip()=='OracleRedoKernel|exited'
 run('source-start',['docker','start',S]);started=True
 for attempt in range(30):
  try:
   out=sql(f'ready-{attempt}',"alter session set container=ORKPDB;\nselect 'READY|'||count(*) from ORKLAB.T_D4_LOBS_20261009;")
   if 'READY|1' in out:break
  except AssertionError:
   if attempt==29:raise
  time.sleep(3)
 assert 'LEDGERS|0' in sql('ledger-preflight',"alter session set container=ORKPDB;\nselect 'LEDGERS|'||count(*) from dba_tables where owner='ORKTGT_CMT_20261005' and table_name='RLOBS1_APPLIED';")
 run('helper-start',['docker','run','-d','--name',HELPER,'--label','owner=OracleRedoKernel','--label','scope=oracle-lob-product','--user','0','--network','container:'+S,'--workdir','/evidence','--env-file',N/'runtime.secret.env','--mount','type=bind,source='+str(N)+',target=/evidence','--entrypoint','java','flink:1.18-java17','-Xmx1536m','-jar','/evidence/application-r19.jar']);helper=True
 for attempt in range(40):
  try:http(f'health-{attempt}','/system/info');break
  except (AssertionError,ValueError):
   if attempt==39:raise
   time.sleep(3)
 assert run('running-sha',['docker','exec',HELPER,'sha256sum','/evidence/application-r19.jar']).split()[0]==candidate['application']
 verified=http('S-verified',f'/oracle/acquisitions/{ACQ}');assert {k:v for k,v in verified.items() if k!='registeredAt'}=={k:v for k,v in acquired.items() if k!='registeredAt'}
 # Execute only target-ledger/enrollment/publication verification from the normal harness.
 exec(publication)
 proofs[-1]['applicationSha256']=candidate['application']
 (N/'S-normal-proof.json').write_text(json.dumps(proofs[-1],indent=2))
 (N/'normal-product-proof.json').write_text(json.dumps(dict(latestApplicationSha256=candidate['application'],storageProofs=proofs,sourceDml=2,newAcquisitions=2,oldEventsPublished=0,mode='authenticated-HTTP-acquisition-static-bounded',independentTargetAccepted=False),indent=2))
finally:
 try:
  if helper:
   run('failure-helper-logs',['docker','logs',HELPER]);run('failure-helper-stop',['docker','stop','-t','20',HELPER]);run('failure-helper-remove',['docker','rm',HELPER])
 finally:
  if started:run('source-stop',['docker','stop','-t','60',S])
