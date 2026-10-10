"""Verify existing RAW/temporal HTTP acquisition wiring. No source DML or historical reapproval."""
from pathlib import Path
import sys,json,shutil,time
R=Path(__file__).resolve().parent.parent;kind=sys.argv[1];assert kind in ['raw','temporal']
label='d4-'+kind+'-http-20261010';sys.argv=[str(R/'lab/d3_stream_runtime.py'),'inspect',label]
exec((R/'lab/d3_stream_runtime.py').read_text().split("if action=='prepare':")[0])
old=R/f'lab/out/d4-{kind}-product-20261009';N=R/'lab/out'/label;N.mkdir();shutil.copyfile(__file__,N/'executed.py')
props=dict(line.split('=',1) for line in (old/'runtime.secret.env').read_text().splitlines())
secrets.extend(v.encode() for k,v in props.items() if 'PASSWORD' in k and v)
reader=(R/'lab/out/dictionary-baseline-candidate-20261006/source.secret').read_text().splitlines();secrets.append(reader[1].encode())
app=R/'lab/out/d3-online-product-20261010/application-final.jar'
assert sha(app)=='d028ca5450bfe95bab4bcac09eafc39054b023e814f2dd32e83eeffb38ee4350'
shutil.copyfile(app,N/'application.jar');(N/'snapshots').mkdir()
props.update(SERVER_PORT=str(PORT),SH_PIPELINE_AUTO_CONSUME_ENABLED='false',SH_ORACLE_RAW_DISCOVERY_ENABLED='false',SH_ORACLE_RAW_PLAN_ENABLED='false',SH_ORACLE_ACQUISITION_ENABLED='true',SH_ORACLE_ACQUISITION_USER=reader[0],SH_ORACLE_ACQUISITION_PASSWORD=reader[1],SH_ORACLE_ACQUISITION_URL=reader[2],SH_ORACLE_ACQUISITION_DIRECTORY='/evidence/snapshots')
(N/'runtime.secret.env').write_bytes(''.join(k+'='+v+'\n' for k,v in props.items()).encode())
started=False;helper=False
try:
 assert HELPER not in run('containers',['docker','ps','-a','--format','{{.Names}}']).splitlines()
 assert run('owner-state',['docker','inspect',S,'--format','{{.Config.Labels.owner}}|{{.State.Status}}']).strip()=='OracleRedoKernel|exited'
 run('start',['docker','start',S]);started=True
 for i in range(30):
  try:
   ready=sql(f'ready-{i}',"select 'LOGGING|'||supplemental_log_data_min||'|'||supplemental_log_data_all from v$database;")
   if 'LOGGING|NO|NO' in ready:break
  except AssertionError:
   if i==29:raise
  time.sleep(3)
 else:raise AssertionError('SOURCE_NOT_READY')
 run('helper-start',['docker','run','-d','--name',HELPER,'--label','owner=OracleRedoKernel','--label','scope=oracle-type-http','--user','0','--network','container:'+S,'--env-file',N/'runtime.secret.env','--mount','type=bind,source='+str(N)+',target=/evidence','--entrypoint','java','flink:1.18-java17','-Xmx1536m','-jar','/evidence/application.jar']);helper=True
 for i in range(40):
  try:http(f'health-{i}','/system/info');break
  except (AssertionError,ValueError):
   if i==39:raise
   time.sleep(3)
 U=props['SPRING_DATASOURCE_USERNAME']
 before=sql('before-counts',f"alter session set container=ORKPDB;\nselect 'COUNTS|'||(select count(*) from {U}.SH_TASK)||'|'||(select count(*) from {U}.SH_EVENT_LEDGER) from dual;")
 acquired=http('acquire','/oracle/acquisitions/historical',dict(sourceId=props['SH_ORACLE_ACQUISITION_SOURCE_ID']))
 verified=http('verified','/oracle/acquisitions/'+str(acquired['id']))
 assert {k:v for k,v in acquired.items() if k!='registeredAt'}=={k:v for k,v in verified.items() if k!='registeredAt'}
 snapshot=N/'snapshots'/(str(acquired['id'])+'.snapshot');assert sha(snapshot)==acquired['snapshotSha256']
 text=snapshot.read_text();metadata=json.loads(acquired['metadataJson']);assert int(metadata['scn'])==int(acquired['snapshotScn'])
 types=[c['type'] for c in metadata['columns']];assert types==(['NUMBER','RAW'] if kind=='raw' else ['NUMBER','DATE','TIMESTAMP'])
 scn=int(acquired['snapshotScn']);table=props['SH_ORACLE_ACQUISITION_TABLE'];U=props['SPRING_DATASOURCE_USERNAME']
 value="rawtohex(BIN)" if kind=='raw' else "to_char(D,'YYYY-MM-DD\"T\"HH24:MI:SS')||'|'||to_char(TS,'YYYY-MM-DD\"T\"HH24:MI:SS.FF9')"
 actual=sql('actual',f"alter session set container=ORKPDB;\nselect 'SOURCE|'||ID||'|'||{value} from ORKLAB.{table} as of scn {scn};\nselect 'COUNTS|'||(select count(*) from {U}.SH_TASK)||'|'||(select count(*) from {U}.SH_EVENT_LEDGER) from dual;")
 assert next(l for l in before.splitlines() if l.startswith('COUNTS|'))==next(l for l in actual.splitlines() if l.startswith('COUNTS|'))
 row=next(l.split('|') for l in text.splitlines() if l.startswith('ROW|'))
 if kind=='raw':assert row[6]=='010080FF' and 'SOURCE|1|010080FF' in actual
 else:
  from datetime import datetime
  def iso(raw):
   b=bytes.fromhex(raw);year=(b[0]-100)*100+b[1]-100
   return datetime(year,b[2],b[3],b[4]-1,b[5]-1,b[6]-1).isoformat()+(('.'+str(int.from_bytes(b[7:],'big')).zfill(9)) if len(b)==11 else '')
  assert 'SOURCE|1|'+iso(row[6])+'|'+iso(row[7]) in actual
 original=json.loads((old/'normal-product-proof.json').read_text());assert original['events']==4 and original['target']==[4,4,1] and original['mismatches']==0
 (N/'http-proof.json').write_text(json.dumps(dict(applicationSha256=sha(app),acquisition=acquired,types=types,mismatches=0,newSourceDml=0,newPublications=0,originalProductProof=str(old/'normal-product-proof.json'),lateBaselineAuthorizesOldInput=False),indent=2))
finally:
 try:
  if helper:run('logs',['docker','logs',HELPER]);run('helper-stop',['docker','stop','-t','20',HELPER]);run('helper-remove',['docker','rm',HELPER])
 finally:
  if started:run('source-stop',['docker','stop','-t','60',S])
