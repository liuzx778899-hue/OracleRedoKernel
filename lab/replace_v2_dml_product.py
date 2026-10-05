"""Replace only the owned lab helper, retaining old container and acquisition identity."""
import hashlib,json,subprocess,time
from pathlib import Path
R=Path(__file__).resolve().parent.parent;O=R/'lab/out/v2-dml-capture-20261006';S='oraredo-19c-lab-20261004';N='ork-oracle-v2-dml-20261006'
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
def run(name,args,body=None):
    assert not (O/(name+'.command.json')).exists();(O/(name+'.command.json')).write_text(json.dumps(list(map(str,args))))
    p=subprocess.run(list(map(str,args)),input=body,capture_output=True,timeout=60)
    for suffix,data in [('stdout',p.stdout),('stderr',p.stderr)]: (O/(name+'.'+suffix+'.txt')).write_bytes(data)
    (O/(name+'.rc.txt')).write_text(str(p.returncode));assert p.returncode==0,(name,p.stderr[-500:]);return p.stdout
def verify(name):
    remote="""import json,time,urllib.request
base='http://127.0.0.1:37222/api/v1'
def req(path,data=None,token=None):
 h={'Content-Type':'application/json'}
 if token:h['Authorization']='Bearer '+token
 return json.loads(urllib.request.urlopen(urllib.request.Request(base+path,data=None if data is None else json.dumps(data).encode(),headers=h),timeout=3).read())
for n in range(60):
 try:
  login=req('/auth/login',LOGIN)
  if login['code']==0:break
 except Exception:pass
 time.sleep(.5)
else:raise RuntimeError('readiness timeout; inspect same helper')
r=req('/oracle/acquisitions/'+ID,token=login['data']['token']);assert r['code']==0;print(json.dumps(r))
""".replace('LOGIN',repr(json.loads((O/'admin.secret.json').read_text()))).replace('ID',repr((O/'acquisition-id.txt').read_text()))
    return json.loads(run(name,['docker','exec','-i',S,'python3','-'],remote.encode()))['data']
proof=json.loads((O/'product-672-proof.json').read_text());assert sha(O/'application-672.jar')==proof['app_sha256']
assert subprocess.check_output(['docker','inspect',N,'--format','{{.Config.Labels.owner}}|{{.Config.Labels.scope}}|{{.State.Status}}']).decode().strip()=='OracleRedoKernel|oracle-acquired-dml|running'
b=json.loads((O/'baseline-reference.json').read_text());assert sha(Path(b['path']))==b['sha256']
(O/'replace-672-execution-pin.json').write_text(json.dumps(dict(driver_sha256=sha(Path(__file__)),application_sha256=proof['app_sha256'],old_application_sha256=sha(O/'application.jar'),baseline_sha256=b['sha256'],product_commit=proof['commit']),indent=2))
before=verify('before-672-replace-acquisition')
run('stop-old-444-helper',['docker','stop',N]);run('retain-old-444-helper',['docker','rename',N,N+'-old444'])
run('start-672-helper',['docker','run','-d','--name',N,'--label','owner=OracleRedoKernel','--label','scope=oracle-acquired-dml','--user','0','--network','container:'+S,'--workdir','/evidence','--env-file',O/'runtime.secret.env','--mount','type=bind,source='+str(O)+',target=/evidence','--entrypoint','java','flink:1.18-java17','-Xmx1536m','-jar','/evidence/application-672.jar'])
after=verify('after-672-replace-acquisition');assert after==before and sha(Path(b['path']))==b['sha256']
actual=run('running-672-app-sha',['docker','exec',N,'sha256sum','/evidence/application-672.jar']).decode().split()[0];assert actual==proof['app_sha256']
(O/'replace-672-proof.json').write_text(json.dumps(dict(product=proof['commit'],application_sha256=actual,acquisition_id=b['acquisition_id'],persisted_acquisition_exact=True,baseline_bytes_exact=True,source_actions=0,target_actions=0,old_helper_retained=N+'-old444'),indent=2))
print('PRODUCT_672_RUNNING_AND_SAME_ACQUISITION_RESTORED')
