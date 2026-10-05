"""Actual HTTP ACK-conflict experiment on owned schemas; no source DML or parser test rerun."""
import json,hashlib,subprocess,sys
from pathlib import Path
import oracle_http_mixed_commands as C
R=Path(__file__).resolve().parent.parent;O=R/'lab/out/oracle-ack-pending-20261005';S='oraredo-19c-lab-20261004';APP='ork-oracle-ack-pending-20261005';C.O=O
sql=C.sql;record=C.record
SOURCE='oracle-ack-SMIX';WINDOW='ack-SMIX';TASK_FILE=O/'task-id.txt'
def http(name,path,data=None):
 remote="""import json,urllib.request
base='http://127.0.0.1:37217/api/v1'
def request(path,data=None,token=None):
 h={'Content-Type':'application/json'}
 if token:h['Authorization']='Bearer '+token
 with urllib.request.urlopen(urllib.request.Request(base+path,data=None if data is None else json.dumps(data).encode(),headers=h),timeout=60) as r:return json.loads(r.read())
l=request('/auth/login',LOGIN);assert l['code']==0
r=request(PATH,DATA,l['data']['token']);print(json.dumps(r));assert r['code']==0
""".replace('LOGIN',repr(json.loads((O/'admin.secret.json').read_text()))).replace('PATH',repr(path)).replace('DATA',repr(data))
 return json.loads(record(name,['docker','exec','-i',S,'python3','-'],remote.encode()))
def setup():
 a=json.loads((O/'approved-binding.json').read_text());s=json.dumps(a,separators=(',',':'));expr='||\n'.join("to_clob('"+s[i:i+1800].replace("'","''")+"')" for i in range(0,len(s),1800));sha='\n'.join(Path(p).name+' '+x['journalSha256'] for p,x in zip(a['capturePaths'],a['evidence']['captures']))
 body="insert into ORKACK_20261005.SH_ORACLE_INPUT_BINDING values(7001,'"+SOURCE+"','"+WINDOW+"',1,1,'ack-consumer-SMIX','"+a['evidence']['schemaSha256']+"','APPROVED_BOUNDED',"+expr+",systimestamp,'owned-ack-fixture');\n"
 body+="insert into ORKACK_20261005.SH_CAP_WINDOW(ID,SOURCE_ID,ENGINE_TYPE,WINDOW_KEY,ISSUE_SEQ_START,ISSUE_SEQ_END,HEAD_PATH,FROZEN,FILE_SHA256,TOTAL_SIZE,DROPPED_SEGMENTS,STATUS,REGISTERED_AT) values(7002,'"+SOURCE+"','oracle','"+WINDOW+"',1,1,'"+a['capturePaths'][0]+"',1,'"+sha+"',"+str(sum(x['journalBytes'] for x in a['evidence']['captures']))+",0,'PENDING',systimestamp);\ncommit;";sql('register-ack-fixture',body)
 ackdir=O/'acks';ackdir.mkdir();name=hashlib.sha256((SOURCE+'\0'+WINDOW).encode()).hexdigest()+'.ack.json';(ackdir/name).write_bytes(b'{}');(O/'conflict-original-sha.txt').write_text(hashlib.sha256(b'{}').hexdigest())
def snapshot(name):
 text="select 'TARGET|'||(select count(*) from ORKTGT_CMT_20261005.AKMIX_APPLIED)||'|'||(select count(*) from ORKTGT_CMT_20261005.AKMIX_COMMITS) from dual;\nselect 'CONTROL|'||STATE||'|'||to_char(ID,'FM99999999999999999999') from ORKACK_20261005.SH_PIPELINE_BATCH order by ID;\nselect 'TASK|'||STATE||'|'||CHECKPOINT_JSON from ORKACK_20261005.SH_TASK;\nselect 'WINDOW|'||STATUS from ORKACK_20261005.SH_CAP_WINDOW;\nselect 'LEDGER|'||count(*) from ORKACK_20261005.SH_EVENT_LEDGER;\nselect 'AUDIT|'||ACTION||'|'||DETAIL from ORKACK_20261005.SH_AUDIT_LOG where ACTION in ('pipeline.task.done','pipeline.task.failed') order by ID;\nselect 'FAILCLOSED|'||STAGE||'|'||REASON||'|'||DETAIL from ORKACK_20261005.SH_FAILCLOSED_RECORD order by ID;"
 sql(name,text)
if __name__=='__main__':
 action=sys.argv[1]
 if action=='setup':setup()
 elif action=='submit':
  data=http('submit','/pipeline/tasks',dict(sourceId=SOURCE));TASK_FILE.write_text(str(data['data']['id']));print('ACK_TASK_ACCEPTED|'+str(data['data']['id']))
 elif action=='resume':http(sys.argv[2],'/pipeline/tasks/'+TASK_FILE.read_text()+'/resume',{})
 elif action=='snapshot':snapshot(sys.argv[2])
 elif action=='repair':
  f=next((O/'acks').glob('*.ack.json'));assert f.read_bytes()==b'{}';f.rename(O/'retained-conflicting-ack.json')
