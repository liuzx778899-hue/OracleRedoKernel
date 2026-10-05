"""Restart the same owned candidate and independently verify persisted publication without source replay."""
import hashlib,json,subprocess
from pathlib import Path
R=Path(__file__).resolve().parent.parent;O=R/'lab/out/v2-dml-capture-20261006';S='oraredo-19c-lab-20261004';N='ork-oracle-v2-dml-20261006'
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
def run(name,args,body=None):
    assert not (O/(name+'.command.json')).exists();(O/(name+'.command.json')).write_text(json.dumps(list(map(str,args))))
    p=subprocess.run(list(map(str,args)),input=body,capture_output=True,timeout=60)
    (O/(name+'.stdout.txt')).write_bytes(p.stdout);(O/(name+'.stderr.txt')).write_bytes(p.stderr);(O/(name+'.rc.txt')).write_text(str(p.returncode));assert p.returncode==0,(name,p.stderr[-1000:]);return p.stdout
assert subprocess.check_output(['docker','inspect',N,'--format','{{.Config.Labels.owner}}|{{.Config.Labels.scope}}|{{.State.Status}}']).decode().strip()=='OracleRedoKernel|oracle-acquired-dml|running'
appsha=json.loads((O/'product-672-proof.json').read_text())['app_sha256'];assert sha(O/'application-672.jar')==appsha
ack=next((O/'acks').glob('*.ack.json'));before=ack.read_bytes();baseline=json.loads((O/'baseline-reference.json').read_text())
(O/'restart-672-execution-pin.json').write_text(json.dumps(dict(driver_sha256=sha(Path(__file__)),app_sha256=appsha,ack_sha256=sha(ack),baseline_sha256=baseline['sha256'])))
for action in ['stop','start']:run('restart-672-'+action,['docker',action,N])
remote="""import json,time,urllib.request
base='http://127.0.0.1:37222/api/v1'
for n in range(60):
 try:
  r=json.loads(urllib.request.urlopen(urllib.request.Request(base+'/auth/login',json.dumps(LOGIN).encode(),{'Content-Type':'application/json'}),timeout=2).read())
  if r['code']==0:break
 except Exception:pass
 time.sleep(.5)
else:raise RuntimeError('readiness timeout; inspect same helper')
h={'Authorization':'Bearer '+r['data']['token']}
row=json.loads(urllib.request.urlopen(urllib.request.Request(base+'/oracle/acquisitions/'+ID,headers=h),timeout=5).read());assert row['code']==0;print(json.dumps(row))
""".replace('LOGIN',repr(json.loads((O/'admin.secret.json').read_text()))).replace('ID',repr(baseline['acquisition_id']))
row=json.loads(run('restart-672-acquisition',['docker','exec','-i',S,'python3','-'],remote.encode()))
assert row==json.loads((O/'after-672-replace-acquisition.stdout.txt').read_text())
driver=R/'lab/publish_v2_dml_http.py'
run('restart-672-repeat-driver',[__import__('sys').executable,driver,'resume','restart-repeat-v2-DML'])
run('restart-672-snapshot-driver',[__import__('sys').executable,driver,'snapshot','actual-restart-repeat'])
assert (O/'actual-restart-repeat.stdout.txt').read_bytes()==(O/'actual-published.stdout.txt').read_bytes()
assert ack.read_bytes()==before and sha(Path(baseline['path']))==baseline['sha256']
actual=run('restart-672-app-sha',['docker','exec',N,'sha256sum','/evidence/application-672.jar']).decode().split()[0];assert actual==appsha
(O/'restart-repeat-proof.json').write_text(json.dumps(dict(same_candidate_application_restarted=True,acquisition_exact=True,target_control_checkpoint_ack_exact_bytes_unchanged=True,ack_sha256=sha(ack),source_actions=0),indent=2))
print('V2_PRODUCT_RESTART_AND_REPEAT_EXACT')
