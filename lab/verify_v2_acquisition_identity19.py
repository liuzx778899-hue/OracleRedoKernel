"""Owned control-ledger identity faults with guaranteed restoration; never source replay or task resume."""
import copy,hashlib,json,subprocess
from pathlib import Path
R=Path(__file__).resolve().parent.parent;H=R/'lab/out/v2-historical-product-20261006';O=R/'lab/out/v2-identity-20261006';S='oraredo-19c-lab-20261004';N='ork-oracle-v2-history-20261006';U='ORKV2HIST_20261006'
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
assert subprocess.check_output(['docker','inspect',N,'--format','{{.Config.Labels.owner}}|{{.Config.Labels.scope}}|{{.State.Status}}']).decode().strip()=='OracleRedoKernel|oracle-acquired-dml|exited'
O.mkdir(exist_ok=False);(O/'executed-driver.py').write_bytes(Path(__file__).read_bytes())
b=json.loads((H/'baseline-reference.json').read_text());assert sha(Path(b['path']))==b['sha256']
(O/'execution-pin.json').write_text(json.dumps(dict(driver_sha256=sha(Path(__file__)),application_sha256=sha(H/'application.jar'),baseline=b),indent=2))
def run(name,args,body=None):
    assert not (O/(name+'.command.json')).exists();(O/(name+'.command.json')).write_text(json.dumps(list(map(str,args))))
    p=subprocess.run(list(map(str,args)),input=body,capture_output=True,timeout=60)
    (O/(name+'.stdout.txt')).write_bytes(p.stdout);(O/(name+'.stderr.txt')).write_bytes(p.stderr);(O/(name+'.rc.txt')).write_text(str(p.returncode));assert p.returncode==0 and b'SP2-' not in p.stdout,(name,p.stderr[-500:]);return p.stdout
def sql(name,text):
    body='whenever sqlerror exit failure rollback\nwhenever oserror exit failure rollback\nset pages 0 feedback off heading off lines 32767 trimspool on\nalter session set container=ORKPDB;\n'+text+'\nexit success\n'
    (O/(name+'.sql')).write_text(body);return run(name,['docker','exec','-i',S,'sqlplus','-s','/','as','sysdba'],body.encode())
def get(name):
    remote="""import json,time,urllib.request
base='http://127.0.0.1:37223/api/v1'
for n in range(60):
 try:
  login=json.loads(urllib.request.urlopen(urllib.request.Request(base+'/auth/login',json.dumps(LOGIN).encode(),{'Content-Type':'application/json'}),timeout=2).read())
  if login['code']==0:break
 except Exception:pass
 time.sleep(.5)
else:raise RuntimeError('readiness timeout; inspect same helper')
request=urllib.request.Request(base+'/oracle/acquisitions/'+ID,headers={'Authorization':'Bearer '+login['data']['token']})
print(urllib.request.urlopen(request,timeout=5).read().decode())
""".replace('LOGIN',repr(json.loads((H/'admin.secret.json').read_text()))).replace('ID',repr(b['acquisition_id']))
    return json.loads(run(name,['docker','exec','-i',S,'python3','-'],remote.encode()))
audit="select 'TARGET|ROWS|'||count(*) from ORKTGT_CMT_20261005.V2HIST_ROWS;\n"
for table in ['APPLIED','MEMBERS','COMMITS']:audit+="select 'TARGET|"+table+"|'||count(*) from ORKTGT_CMT_20261005.V2HIST_"+table+';\n'
audit+="select 'ROW|'||ID||'|'||TXT||'|'||NOTE from ORKTGT_CMT_20261005.V2HIST_ROWS;\nselect 'LEDGER|'||count(*) from "+U+".SH_EVENT_LEDGER;\nselect 'CP|'||nvl(cast(CHECKPOINT_JSON as varchar2(4000)),'<NULL>') from "+U+".SH_TASK;\nselect 'TASK|'||STATE from "+U+".SH_TASK;\nselect 'WINDOW|'||STATUS from "+U+".SH_CAP_WINDOW;\nselect 'LOGGING|'||supplemental_log_data_min||'|'||supplemental_log_data_all from v$database;\nselect 'SOURCE|'||ID||'|'||TXT||'|'||NOTE from ORKLAB.T_V2_HIST_20261006;\nselect 'OLD|'||ID||'|'||TXT||'|'||NOTE from ORKLAB.T_BASELINE_20261004;"
before=sql('before-identity-audit',audit);run('identity-helper-start',['docker','start',N]);original=get('original-persisted-get');assert original['code']==0;row=original['data'];metadata=json.loads(row['metadataJson']);cases=[]
def update(name,authority,scn,text):
    expr='||\n'.join("to_clob('"+text[i:i+1600].replace("'","''")+"')" for i in range(0,len(text),1600))
    sql(name,"update "+U+".SH_ORACLE_ACQUISITION set AUTHORITY='"+authority+"',SNAPSHOT_SCN="+str(scn)+",METADATA_JSON="+expr+" where ID="+b['acquisition_id']+";\ncommit;")
try:
    for name in ['authority','scn','source','role','anchor']:
        changed=copy.deepcopy(metadata);authority=row['authority'];scn=row['snapshotScn']
        if name=='authority':authority='wrong-owned-authority'
        if name=='scn':scn+=1
        if name=='source':changed['source']['dbid']+=1
        if name=='role':changed['dictionaryAnchors'][0]['dictionary']['logicalObject']+=1
        if name=='anchor':changed['dictionaryAnchors'][0]['key']['slot']+=1
        try:
            update(name+'-tamper',authority,scn,json.dumps(changed,separators=(',',':')))
            refusal=get(name+'-refused');assert refusal['code']!=0
            cases.append(dict(name=name,code=refusal['code'],message=refusal.get('msg')))
        finally:update(name+'-restore',row['authority'],row['snapshotScn'],row['metadataJson'])
        assert get(name+'-restored')==original and sha(Path(b['path']))==b['sha256']
    assert sql('after-identity-audit',audit)==before
    assert not list((H/'acks').glob('*.ack.json'))
    final=get('final-persisted-get');assert final==original
finally:
    run('identity-helper-stop',['docker','stop',N])
state=json.loads(run('identity-final-helper-state',['docker','inspect',N,'--format','{{json .State}}']));assert state['Status']=='exited'
(O/'proof.json').write_text(json.dumps(dict(cases=cases,restored_persisted_get_exact=True,baseline_bytes_exact=True,target_control_source_audits_exact=True,source_actions=0,task_resumes=0,ack_files=0,application_sha256=sha(H/'application.jar'),helper=state,complete_dictionary_history_verified=False),indent=2))
print('FIVE_REAL_ACQUISITION_IDENTITY_FAULTS_REFUSED_AND_RESTORED')
