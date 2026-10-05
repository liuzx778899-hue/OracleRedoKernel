"""Owned actual product publication with a verified acquisition ID, never client path approval."""
import hashlib,json,subprocess,sys
from pathlib import Path
R=Path(__file__).resolve().parent.parent;O=R/'lab/out/v2-historical-product-20261006';S='oraredo-19c-lab-20261004';U='ORKV2HIST_20261006';SOURCE='oracle-v2-history';WINDOW='v2-history-ABA';T='ORKTGT_CMT_20261005.V2HIST'
def record(name,args,body=None):
 assert not (O/(name+'.rc.txt')).exists();p=subprocess.run(args,input=body,capture_output=True,timeout=90)
 (O/(name+'.command.json')).write_text(json.dumps(args));(O/(name+'.stdout.txt')).write_bytes(p.stdout);(O/(name+'.stderr.txt')).write_bytes(p.stderr);(O/(name+'.rc.txt')).write_text(str(p.returncode));assert p.returncode==0 and b'SP2-' not in p.stdout,name;return p.stdout.decode()
def sql(name,text):
 body='whenever sqlerror exit failure rollback\nset pages 0 feedback off heading off lines 32767 long 200000 longchunksize 200000 trimspool on\nalter session set container=ORKPDB;\n'+text+'\nexit success\n';(O/(name+'.sql')).write_text(body);return record(name,['docker','exec','-i',S,'sqlplus','-s','/','as','sysdba'],body.encode())
def http(name,path,data):
 remote="""import json,urllib.request
base='http://127.0.0.1:37223/api/v1'
def req(path,data=None,token=None):
 h={'Content-Type':'application/json'}
 if token:h['Authorization']='Bearer '+token
 with urllib.request.urlopen(urllib.request.Request(base+path,data=None if data is None else json.dumps(data).encode(),headers=h),timeout=60) as r:return json.loads(r.read())
l=req('/auth/login',LOGIN);assert l['code']==0
r=req(PATH,DATA,l['data']['token']);print(json.dumps(r));assert r['code']==0
""".replace('LOGIN',repr(json.loads((O/'admin.secret.json').read_text()))).replace('PATH',repr(path)).replace('DATA',repr(data))
 return json.loads(record(name,['docker','exec','-i',S,'python3','-'],remote.encode()))
action=sys.argv[1]
if action=='prepare':
 assert not (O/'publication-driver-pin.json').exists()
 (O/'publication-driver-pin.json').write_text(json.dumps(dict(driver_sha256=hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),application_sha256=hashlib.sha256((O/'application.jar').read_bytes()).hexdigest(),projection_sha256=hashlib.sha256((O/'original-negative-claimed-projection.json').read_bytes()).hexdigest())))
if action=='prepare':
 assert not (O/'actual-v2-batch.stdout.txt').read_bytes() and not (O/'erased-v2-batch.stdout.txt').read_bytes()
 assert json.loads((O/'independent-v2-historical-baseline-proof.json').read_text())['differences']==0
 p=json.loads((O/'original-negative-claimed-projection.json').read_text());e=p['evidence'];b=json.loads((O/'baseline-reference.json').read_text());assert e['baselineSha256']==b['sha256'] and e['baselineScn']==b['scn']
 assert 'TABLES|0' in sql('new-target-preflight',"select 'TABLES|'||count(*) from dba_tables where owner='ORKTGT_CMT_20261005' and table_name like 'V2HIST\\_%' escape '\\';")
 source=':'.join(str(e['source'][k]) for k in ['dbid','activationId','compatibility','thread','resetlogsId','resetlogsScn','containerUid','containerId'])
 ddl='create table '+T+'_IDENT(ID number primary key,TARGET_ID varchar2(128) not null,SOURCE_BINDING varchar2(512) not null,SCHEMA_SHA varchar2(64) not null);\n'
 ddl+="insert into "+T+"_IDENT values(1,'oracle-dml-V2HIST-20261005','"+source+"','"+e['schemaSha256']+"');\n"
 ddl+='create table '+T+'_ROWS(ID number primary key,TXT varchar2(128),NOTE varchar2(128));\ninsert into '+T+"_ROWS values(0,'dictionary-seed','anchor');\n"
 ddl+='create table '+T+'_APPLIED(EVENT_ID varchar2(80) primary key,PAYLOAD_SHA varchar2(64) not null,BEFORE_IMAGE varchar2(4000),AFTER_IMAGE varchar2(4000),OPERATION varchar2(16) not null);\n'
 ddl+='create table '+T+'_MEMBERS(BATCH_SHA varchar2(64),ORDINAL number,EVENT_ID varchar2(80) references '+T+'_APPLIED(EVENT_ID),primary key(BATCH_SHA,ORDINAL),unique(BATCH_SHA,EVENT_ID));\n'
 ddl+='create table '+T+'_COMMITS(BATCH_SHA varchar2(64) primary key,TARGET_ID varchar2(128) not null,SCHEMA_SHA varchar2(64) not null,EVENTS_COUNT number not null);\ncommit;';sql('new-target-once',ddl)
 binding=dict(baselinePath=None,capturePaths=['/evidence/capture/sequence-'+str(c['sequence'])+'.oracap' for c in e['captures']],evidence=e,envelopesSha256=p['envelopesSha256'],acquisitionId=int(b['acquisition_id']));(O/'approved-acquired-binding.json').write_text(json.dumps(binding,indent=2))
 text=json.dumps(binding,separators=(',',':'));expr='||\n'.join("to_clob('"+text[i:i+1800].replace("'","''")+"')" for i in range(0,len(text),1800));shas='\n'.join(Path(path).name+' '+c['journalSha256'] for path,c in zip(binding['capturePaths'],e['captures']))
 enrollment="insert into "+U+".SH_ORACLE_INPUT_BINDING values(8001,'"+SOURCE+"','"+WINDOW+"',1,1,'v2-history-consumer','"+e['schemaSha256']+"','APPROVED_BOUNDED',"+expr+",systimestamp,'owned-acquired-fixture');\n"
 enrollment+="insert into "+U+".SH_CAP_WINDOW(ID,SOURCE_ID,ENGINE_TYPE,WINDOW_KEY,ISSUE_SEQ_START,ISSUE_SEQ_END,HEAD_PATH,FROZEN,FILE_SHA256,TOTAL_SIZE,DROPPED_SEGMENTS,STATUS,REGISTERED_AT) values(8002,'"+SOURCE+"','oracle','"+WINDOW+"',1,1,'"+binding['capturePaths'][0]+"',1,'"+shas+"',"+str(sum(c['journalBytes'] for c in e['captures']))+",0,'PENDING',systimestamp);\ncommit;";sql('enroll-actual-acquisition',enrollment)
elif action=='erased':
 p=json.loads((O/'erased-negative-claimed-projection.json').read_text());e=p['evidence'];b=json.loads((O/'baseline-reference.json').read_text())
 binding=dict(baselinePath=None,capturePaths=['/evidence/erased-'+str(c['sequence'])+'.oracap' for c in e['captures']],evidence=e,envelopesSha256=p['envelopesSha256'],acquisitionId=int(b['acquisition_id']))
 (O/'approved-erased-negative-binding.json').write_text(json.dumps(binding,indent=2))
 text=json.dumps(binding,separators=(',',':'));expr='||\n'.join("to_clob('"+text[i:i+1800].replace("'","''")+"')" for i in range(0,len(text),1800));shas='\n'.join(Path(path).name+' '+c['journalSha256'] for path,c in zip(binding['capturePaths'],e['captures']))
 sql('enroll-erased-negative',"update "+U+".SH_ORACLE_INPUT_BINDING set BINDING_JSON="+expr+" where ID=8001;\nupdate "+U+".SH_CAP_WINDOW set HEAD_PATH='"+binding['capturePaths'][0]+"', FILE_SHA256='"+shas+"',TOTAL_SIZE="+str(sum(c['journalBytes'] for c in e['captures']))+" where ID=8002;\ncommit;")
elif action in ['tamper','restore']:
 authority="'wrong-authority'" if action=='tamper' else "'owned-v2-hist-20261006'";sql('acquisition-'+action,"update "+U+".SH_ORACLE_ACQUISITION set AUTHORITY="+authority+" where ID="+(O/'acquisition-id.txt').read_text()+";\ncommit;")
elif action=='submit':
 r=http('submit-acquired-DML','/pipeline/tasks',dict(sourceId=SOURCE));(O/'task-id.txt').write_text(str(r['data']['id']))
elif action=='resume':http(sys.argv[2],'/pipeline/tasks/'+(O/'task-id.txt').read_text()+'/resume',{})
elif action=='reason':
 sql(sys.argv[2],"column reason_hex format a1600\nselect 'REASON_HEX|'||RC||'|'||rawtohex(utl_raw.cast_to_raw(RC_REASON)) reason_hex from "+U+".SH_PIPELINE_BATCH order by ID;")
elif action=='snapshot':
 text="select 'IDENT|'||ID||'|'||TARGET_ID||'|'||SOURCE_BINDING||'|'||SCHEMA_SHA from "+T+"_IDENT;\nselect 'ROW|'||ID||'|'||nvl(TXT,'<NULL>')||'|'||nvl(NOTE,'<NULL>') from "+T+"_ROWS order by ID;\nselect 'APPLIED|'||EVENT_ID||'|'||PAYLOAD_SHA||'|'||OPERATION||'|'||BEFORE_IMAGE||'|'||AFTER_IMAGE from "+T+"_APPLIED order by EVENT_ID;\nselect 'MEMBER|'||BATCH_SHA||'|'||ORDINAL||'|'||EVENT_ID from "+T+"_MEMBERS order by ORDINAL;\nselect 'COMMIT|'||BATCH_SHA||'|'||TARGET_ID||'|'||SCHEMA_SHA||'|'||EVENTS_COUNT from "+T+"_COMMITS;\n"
 text+="select 'CONTROL|'||ID||'|'||STATE||'|'||INPUT_SHA256||'|'||ISSUE_SEQ_START||'|'||ISSUE_SEQ_END||'|'||EVENT_COUNT from "+U+".SH_PIPELINE_BATCH order by ID;\nselect 'TASK|'||ID||'|'||STATE from "+U+".SH_TASK;\nselect 'CHECKPOINT|'||cast(CHECKPOINT_JSON as varchar2(4000)) from "+U+".SH_TASK;\nselect 'WINDOW|'||STATUS from "+U+".SH_CAP_WINDOW;\nselect 'LEDGER|'||EVENT_ID from "+U+".SH_EVENT_LEDGER order by EVENT_ID;\nselect 'ACQUIRED|'||ID||'|'||STATE||'|'||SNAPSHOT_SHA256||'|'||SNAPSHOT_SCN from "+U+".SH_ORACLE_ACQUISITION;\nselect 'FAIL|'||STAGE||'|'||REASON||'|'||DETAIL from "+U+".SH_FAILCLOSED_RECORD;\nselect 'LOGGING|'||supplemental_log_data_min||'|'||supplemental_log_data_all from v$database;\nselect 'OLD|'||ID||'|'||TXT||'|'||NOTE from ORKLAB.T_BASELINE_20261004;";sql(sys.argv[2],text)
else:raise ValueError(action)
print('ACQUIRED_DML_PUBLICATION_PHASE|'+action)
