"""Owned real HTTP acquisition recovery checks only; no publication or source DML."""
import hashlib,json,subprocess,sys
from pathlib import Path
R=Path(__file__).resolve().parent.parent;O=R/'lab/out/v2-control-runtime-20261006';S='oraredo-19c-lab-20261004';NAME='ork-oracle-v2-acquisition-20261006'
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
(O/'verify-runtime-execution-pin-r2.json').write_text(json.dumps({'script_sha256':sha(Path(__file__)),'runtime_driver_sha256':sha(R/'lab/v2_acquisition_controller_runtime.py'),'app_sha256':sha(O/'application.jar')},indent=2))
def phase(action,name):
    p=subprocess.run([sys.executable,R/'lab/v2_acquisition_controller_runtime.py',action,name],capture_output=True,timeout=120)
    (O/(name+'.host.stdout.txt')).write_bytes(p.stdout);(O/(name+'.host.stderr.txt')).write_bytes(p.stderr);assert p.returncode==0,p.stderr
    return json.loads((O/(name+'.stdout.txt')).read_text()) if action=='verify' else None
result=json.loads((O/'actual-v2-acquire.stdout.txt').read_text());assert result[0]==200 and result[1]['code']==0
row=result[1]['data'];metadata=json.loads(row['metadataJson']);assert len(metadata['dictionaryAnchors'])==6 and len(metadata['columns'])==3
id=str(row['id']);snapshot=O/'snapshots'/(id+'.snapshot');healthy=snapshot.read_bytes();assert sha(snapshot)==row['snapshotSha256']
assert healthy.startswith(b'FORMAT|ORK_HEAP_SNAPSHOT_V2\n')
persisted=json.loads((O/'initial-verify.stdout.txt').read_text())[1]['data']
assert {k:v for k,v in row.items() if k!='registeredAt'}=={k:v for k,v in persisted.items() if k!='registeredAt'}
row=persisted
(O/'healthy.snapshot.txt').write_bytes(healthy)
try:
    snapshot.write_bytes(healthy[:-1]+b'X');bad=phase('verify','corrupt-snapshot-verify');assert bad[1]['code']!=0
finally:snapshot.write_bytes(healthy)
assert phase('verify','restored-snapshot-verify')[1]['data']==row
def sql(name,body):
    text='whenever sqlerror exit failure rollback\nset pages 0 feedback off heading off lines 32767\nalter session set container=ORKPDB;\n'+body+'\nexit success\n'
    (O/(name+'.sql')).write_text(text);p=subprocess.run(['docker','exec','-i',S,'sqlplus','-s','/','as','sysdba'],input=text.encode(),capture_output=True,timeout=60)
    (O/(name+'.stdout.txt')).write_bytes(p.stdout);(O/(name+'.stderr.txt')).write_bytes(p.stderr);(O/(name+'.rc.txt')).write_text(str(p.returncode));assert p.returncode==0 and b'SP2-' not in p.stdout
original=row['metadataJson'];changed=dict(metadata);changed.pop('dictionaryAnchors')
def change(value):return "update ORKV2CTL_20261006.SH_ORACLE_ACQUISITION set METADATA_JSON='"+value.replace("'","''")+"' where ID="+id+";\ncommit;"
try:
    sql('remove-ledger-anchors',change(json.dumps(changed,separators=(',',':'))));assert phase('verify','missing-ledger-anchors-verify')[1]['code']!=0
finally:sql('restore-ledger-anchors',change(original))
assert phase('verify','restored-ledger-verify')[1]['data']==row
state=subprocess.check_output(['docker','inspect',NAME,'--format','{{.Config.Labels.owner}}|{{.Config.Labels.scope}}|{{.State.Status}}']).decode().strip();assert state=='OracleRedoKernel|oracle-v2-acquisition|running'
for action in ['stop','start']:
    p=subprocess.run(['docker',action,NAME],capture_output=True,timeout=60);(O/('restart-'+action+'.stdout.txt')).write_bytes(p.stdout);(O/('restart-'+action+'.stderr.txt')).write_bytes(p.stderr);(O/('restart-'+action+'.rc.txt')).write_text(str(p.returncode));assert p.returncode==0
remote="""import time,json,urllib.request
for n in range(50):
 try:
  r=json.loads(urllib.request.urlopen(urllib.request.Request('http://127.0.0.1:37221/api/v1/auth/login',json.dumps(LOGIN).encode(),{'Content-Type':'application/json'}),timeout=2).read())
  if r['code']==0:print('RESTART_AUTH_READY');break
 except Exception:pass
 time.sleep(.5)
else:raise RuntimeError('not ready; inspect same process')
""".replace('LOGIN',repr(json.loads((O/'admin.secret.json').read_text())))
p=subprocess.run(['docker','exec','-i',S,'python3','-'],input=remote.encode(),capture_output=True,timeout=55);(O/'restart-ready.stdout.txt').write_bytes(p.stdout);(O/'restart-ready.stderr.txt').write_bytes(p.stderr);(O/'restart-ready.rc.txt').write_text(str(p.returncode));assert p.returncode==0,p.stderr
assert phase('verify','restart-verified')[1]['data']==row and snapshot.read_bytes()==healthy
phase('audit','final-audit');audit=(O/'final-audit.stdout.txt').read_text();assert 'BINDINGS|0' in audit and 'EVENTS|0' in audit and 'LOGGING|NO|NO' in audit and 'OLD|11|blocking-closed|new-note' in audit
proof={'actual_acquisition_id':id,'snapshot_scn':row['snapshotScn'],'snapshot_sha256':row['snapshotSha256'],'dictionary_anchors':6,'corrupt_file_refused':True,'missing_persisted_anchors_refused':True,'restored_and_restart_row_exact':True,'acquired_state':row['state'],'bindings':0,'events':0,'source_DML':0,'target_actions':0,'new_raw_or_publication_verified':False}
(O/'http-proof.json').write_text(json.dumps(proof,indent=2));print(json.dumps(proof))
