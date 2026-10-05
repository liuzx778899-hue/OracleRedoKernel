"""Same candidate restart and persistent dictionary refusal, with independent zero-write checks."""
import hashlib,json,subprocess,sys
from pathlib import Path
R=Path(__file__).resolve().parent.parent;O=R/'lab/out/v2-historical-product-20261006';S='oraredo-19c-lab-20261004';N='ork-oracle-v2-history-20261006';U='ORKV2HIST_20261006'
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
def run(name,args,body=None):
    assert not (O/(name+'.command.json')).exists();(O/(name+'.command.json')).write_text(json.dumps(list(map(str,args))))
    p=subprocess.run(list(map(str,args)),input=body,capture_output=True,timeout=60)
    (O/(name+'.stdout.txt')).write_bytes(p.stdout);(O/(name+'.stderr.txt')).write_bytes(p.stderr);(O/(name+'.rc.txt')).write_text(str(p.returncode));assert p.returncode==0,(name,p.stderr[-1000:]);return p.stdout
assert subprocess.check_output(['docker','inspect',N,'--format','{{.Config.Labels.owner}}|{{.Config.Labels.scope}}|{{.State.Status}}']).decode().strip()=='OracleRedoKernel|oracle-acquired-dml|running'
b=json.loads((O/'baseline-reference.json').read_text());healthy=Path(b['path']).read_bytes();assert sha(Path(b['path']))==b['sha256']
(O/'restart-historical-pin.json').write_text(json.dumps(dict(driver_sha256=sha(Path(__file__)),application_sha256=sha(O/'application.jar'),baseline_sha256=b['sha256'])))
for action in ['stop','start']:run('restart-historical-'+action,['docker',action,N])
remote="""import json,time,urllib.request
base='http://127.0.0.1:37223/api/v1'
for n in range(60):
 try:
  l=json.loads(urllib.request.urlopen(urllib.request.Request(base+'/auth/login',json.dumps(LOGIN).encode(),{'Content-Type':'application/json'}),timeout=2).read())
  if l['code']==0:break
 except Exception:pass
 time.sleep(.5)
else:raise RuntimeError('readiness timeout; inspect same helper')
r=json.loads(urllib.request.urlopen(urllib.request.Request(base+'/oracle/acquisitions/'+ID,headers={'Authorization':'Bearer '+l['data']['token']}),timeout=5).read());assert r['code']==0;print(json.dumps(r))
""".replace('LOGIN',repr(json.loads((O/'admin.secret.json').read_text()))).replace('ID',repr(b['acquisition_id']))
row=json.loads(run('restart-historical-acquisition',['docker','exec','-i',S,'python3','-'],remote.encode()))['data']
fresh=json.loads((O/'actual-source-acquire.stdout.txt').read_text())['data'];assert {k:v for k,v in row.items() if k!='registeredAt'}=={k:v for k,v in fresh.items() if k!='registeredAt'}
assert Path(b['path']).read_bytes()==healthy
driver=R/'lab/publish_v2_historical_http19.py'
run('restart-historical-repeat-driver',[sys.executable,driver,'resume','restart-erased-history'])
terminal="""import json,time,urllib.request
base='http://127.0.0.1:37223/api/v1'
login=json.loads(urllib.request.urlopen(urllib.request.Request(base+'/auth/login',json.dumps(LOGIN).encode(),{'Content-Type':'application/json'}),timeout=3).read());assert login['code']==0
for n in range(80):
 r=json.loads(urllib.request.urlopen(urllib.request.Request(base+'/pipeline/tasks/'+ID,headers={'Authorization':'Bearer '+login['data']['token']}),timeout=3).read());assert r['code']==0
 if r['data']['state']=='FAILED':print('SAME_TASK_TERMINAL_FAILED');break
 assert r['data']['state'] not in ['DONE','CANCELLED']
 time.sleep(.25)
else:raise RuntimeError('task observation timeout; inspect same task, no resume replay')
""".replace('LOGIN',repr(json.loads((O/'admin.secret.json').read_text()))).replace('ID',repr((O/'task-id.txt').read_text()))
run('restart-historical-wait-terminal',['docker','exec','-i',S,'python3','-'],terminal.encode())
run('restart-historical-snapshot-driver',[sys.executable,driver,'snapshot','restart-erased-refused'])
run('restart-historical-reason-driver',[sys.executable,driver,'reason','restart-erased-reason'])
sql="whenever sqlerror exit failure rollback\nset pages 0 feedback off heading off lines 32767\nalter session set container=ORKPDB;\n"
for table in ['ROWS','APPLIED','MEMBERS','COMMITS']:
    sql+="select 'COUNT|"+table+"|'||count(*) from ORKTGT_CMT_20261005.V2HIST_"+table+';\n'
sql+="select 'LEDGER|'||count(*) from "+U+".SH_EVENT_LEDGER;\nselect 'SOURCE|'||ID||'|'||TXT||'|'||NOTE from ORKLAB.T_V2_HIST_20261006;\nexit success\n"
(O/'final-zero-write-audit.sql').write_text(sql);audit=run('final-zero-write-audit',['docker','exec','-i',S,'sqlplus','-s','/','as','sysdba'],sql.encode()).decode()
for line in ['COUNT|ROWS|1','COUNT|APPLIED|0','COUNT|MEMBERS|0','COUNT|COMMITS|0','LEDGER|0','SOURCE|0|dictionary-restored|anchor']:assert line in audit
reason='ORACLE_APPROVED_INPUT_REFUSED: TARGET_DICTIONARY_WRITE_REQUIRES_HISTORY:scn=2522903:object=75185'
def verify(text,reasontext,count):
    lines=[l.strip().split('|') for l in text.splitlines() if l.strip()]
    select=lambda key:[l for l in lines if l[0]==key]
    assert select('ROW')==[['ROW','0','dictionary-seed','anchor']]
    assert not any(select(k) for k in ['APPLIED','MEMBER','COMMIT','LEDGER'])
    control=select('CONTROL');assert len(control)==count and all(c[2]=='REFUSED' and c[-1]=='0' for c in control)
    assert select('TASK')==[['TASK',(O/'task-id.txt').read_text(),'FAILED']]
    assert select('CHECKPOINT')==[['CHECKPOINT','']] and select('WINDOW')==[['WINDOW','PENDING']]
    reasons=[l.strip().split('|') for l in reasontext.splitlines() if l.strip()];assert len(reasons)==count
    assert all(l[:2]==['REASON_HEX','2'] and bytes.fromhex(l[2]).decode()==reason for l in reasons)
    assert not list((O/'acks').glob('*.ack.json'))
for name,reasonfile,count in [('original-refused','original-refusal-reason',1),('erased-refused','erased-refusal-reason',2),('restart-erased-refused','restart-erased-reason',3)]:
    verify((O/(name+'.stdout.txt')).read_text(),(O/(reasonfile+'.stdout.txt')).read_text(),count)
text=(O/'restart-erased-refused.stdout.txt').read_text();reasons=(O/'restart-erased-reason.stdout.txt').read_text();negative=[]
for name,bad,badreason in [('event-count',text.replace('|1|1|0','|1|1|1',1),reasons),('target-row',text.replace('dictionary-seed','wrong-seed',1),reasons),('wrong-reason',text,reasons.replace('REASON_HEX|2|','REASON_HEX|0|',1))]:
    try:verify(bad,badreason,3)
    except AssertionError:negative.append(name)
    else:raise AssertionError('COMPARATOR_ACCEPTED_'+name)
run('stop-historical-accepted-helper',['docker','stop',N])
state=json.loads(run('final-historical-helper-state',['docker','inspect',N,'--format','{{json .State}}']));assert state['Status']=='exited'
run('final-historical-helper-logs',['docker','logs',N])
(O/'historical-product-proof.json').write_text(json.dumps(dict(product_commit=json.loads((O/'runtime-pin.json').read_text())['commit'],application_sha256=sha(O/'application.jar'),baseline=b,original_erased_restart_refusals=3,refusal_reason=reason,target_events=0,ledger=0,checkpoint_empty=True,acks=0,native_dictionary_rows=6,linked_new_col_rows=4,comparator_negatives=negative,source_actions_replayed=False,helper=state,historical_dictionary_accepted=False,site_release_accepted=False),indent=2))
print('V2_HISTORICAL_ORIGINAL_ERASED_RESTART_PRODUCT_REFUSED_BEFORE_TARGET')
