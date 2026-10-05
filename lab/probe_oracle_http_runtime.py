"""Authenticate and exercise existing-task recovery through the actual owned HTTP service."""
import hashlib,json,subprocess
from pathlib import Path
R=Path(__file__).resolve().parent.parent;O=R/'lab/out/oracle-http-20261005';A=R/'lab/out/oracle-bounded-runtime-20261005'
pin=json.loads((O/'runtime-pin.json').read_text());assert pin['owner']=='OracleRedoKernel'
assert subprocess.check_output(['docker','inspect',pin['container'],'--format','{{.State.Status}}']).decode().strip()=='running'
reference=next(x for x in json.loads((A/'runtime-proof.json').read_text()) if x['case']=='EMPT');task=reference['checkpoint']['lastBatchId']
# Resolve the actual task ID using owned schema SQL; task ID differs from batch ID.
sql="whenever sqlerror exit failure\nset pages 0 feedback off heading off\nalter session set container=ORKPDB;\nselect to_char(ID,'FM99999999999999999999') from ORKRUNTIME_20261005.SH_TASK where REF_TYPE='SOURCE' and REF_ID='oracle-runtime-EMPT';\nexit success\n"
p=subprocess.run(['docker','exec','-i',pin['source_container'],'sqlplus','-s','/','as','sysdba'],input=sql.encode(),capture_output=True);assert p.returncode==0;task=int(p.stdout.strip())
credentials=(O/'admin.secret.json').read_text()
remote="""import json,urllib.request
base='http://127.0.0.1:37215/api/v1'
def request(path,data=None,token=None):
    headers={'Content-Type':'application/json'}
    if token:headers['Authorization']='Bearer '+token
    req=urllib.request.Request(base+path,data=None if data is None else json.dumps(data).encode(),headers=headers)
    with urllib.request.urlopen(req,timeout=60) as response:return json.loads(response.read().decode())
login=request('/auth/login',LOGIN)
assert login['code']==0,login
token=login['data']['token']
before=request('/pipeline/tasks/TASK',token=token)
resume=request('/pipeline/tasks/TASK/resume',{},token)
after=request('/pipeline/tasks/TASK',token=token)
print(json.dumps({'before':before,'resume':resume,'after':after,'login_succeeded':True}))
""".replace('LOGIN',repr(json.loads(credentials))).replace('TASK',str(task))
args=['docker','exec','-i',pin['source_container'],'python3','-'];(O/'http-recovery.command.json').write_text(json.dumps(args))
with (O/'http-recovery.stdout.txt').open('wb') as out,(O/'http-recovery.stderr.txt').open('wb') as err:p=subprocess.run(args,input=remote.encode(),stdout=out,stderr=err,timeout=120)
(O/'http-recovery.rc.txt').write_text(str(p.returncode));assert p.returncode==0
proof=json.loads((O/'http-recovery.stdout.txt').read_text());assert all(proof[x]['code']==0 for x in ['before','resume','after'])
assert proof['before']['data']['state']=='DONE' and proof['after']['data']['state']=='DONE'
assert json.loads(proof['after']['data']['checkpointJson'])==reference['checkpoint']
ack=next((A/'acks/EMPT').glob('*.ack.json'));assert hashlib.sha256(ack.read_bytes()).hexdigest()==reference['ack_sha256']
(O/'http-proof.json').write_text(json.dumps(dict(authenticated_rest_recovery_accepted=True,task_id=task,initial_and_final_state='DONE',checkpoint_unchanged=True,ack_unchanged=True,pending_dml_pipeline_accepted=False),indent=2));print('ORACLE_OWNED_HTTP_RECOVERY_VERIFIED|'+str(task))
