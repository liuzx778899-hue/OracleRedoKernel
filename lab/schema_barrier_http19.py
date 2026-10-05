"""Owned HTTP rejection of the preserved invalid-schema batch; no source DDL/DML."""
import hashlib,json,secrets,subprocess,sys,time,zipfile
from pathlib import Path
R=Path(__file__).resolve().parent.parent;O=R/'lab/out/schema-barrier-20261005';OLD=R/'lab/out/acquired-dml-capture-20261005';S='oraredo-19c-lab-20261004';NAME='ork-schema-barrier-http-20261005';U='ORKSCH_20261005';SOURCE='oracle-schema-barrier';WINDOW='historical-schema-ABA';T='ORKTGT_CMT_20261005.SCBAR';sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
assert subprocess.check_output(['docker','inspect',S,'--format','{{.Config.Labels.owner}}|{{.State.Status}}']).decode().strip()=='OracleRedoKernel|running'
def run(name,args,body=None):
 assert not (O/(name+'.command.json')).exists();(O/(name+'.command.json')).write_text(json.dumps(list(map(str,args))))
 p=subprocess.run(list(map(str,args)),input=body,capture_output=True,timeout=120)
 for suffix,value in [('stdout',p.stdout),('stderr',p.stderr)]:(O/(name+'.'+suffix+'.txt')).write_bytes(value)
 (O/(name+'.rc.txt')).write_text(str(p.returncode));assert p.returncode==0 and b'SP2-' not in p.stdout,name;return p.stdout.decode()
def sql(name,text,private=False):
 body='whenever sqlerror exit failure rollback\nwhenever oserror exit failure rollback\nset pages 0 feedback off heading off lines 32767 long 200000 trimspool on\nalter session set container=ORKPDB;\n'+text+'\nexit success\n';(O/(name+('.secret' if private else '')+'.sql')).write_text(body)
 return run(name+('.secret' if private else ''),['docker','exec','-i',S,'sqlplus','-s','/','as','sysdba'],body.encode())
def http(name,path,data=None):
 body="""import json,urllib.request
base='http://127.0.0.1:37220/api/v1'
def req(path,data=None,token=None):
 h={'Content-Type':'application/json'}
 if token:h['Authorization']='Bearer '+token
 with urllib.request.urlopen(urllib.request.Request(base+path,data=None if data is None else json.dumps(data).encode(),headers=h),timeout=60) as r:return json.loads(r.read())
l=req('/auth/login',LOGIN);assert l['code']==0
r=req(PATH,DATA,l['data']['token']);print(json.dumps(r));assert r['code']==0
""".replace('LOGIN',repr(json.loads((O/'admin.secret.json').read_text()))).replace('PATH',repr(path)).replace('DATA',repr(data))
 return json.loads(run(name,['docker','exec','-i',S,'python3','-'],body.encode()))
action=sys.argv[1]
if action=='prepare':
 proof=json.loads((O/'product-build-proof.json').read_text());assert sha(O/'application.jar')==proof['app_sha256']
 with zipfile.ZipFile(O/'application.jar') as z:
  names=[n for n in z.namelist() if n.startswith('BOOT-INF/lib/oracle-redo-kernel-')];assert len(names)==1 and hashlib.sha256(z.read(names[0])).hexdigest()==proof['kernel_sha256']
 # Preserve the faulty candidate's typed evidence exclusively as a negative input.
 classes=O/'negative-projection-classes';classes.mkdir();cp=';'.join(str(O/(n+'.jar')) for n in ['kernel','adapter','spi'])+';'+str(OLD/'parser-libs/*')+';'+str(classes)
 run('compile-negative-projection',['D:/work/Java/jdk-17.0.7/bin/javac.exe','-encoding','UTF-8','-cp',cp,'-d',classes,R/'lab/AcquiredDmlProjectionProbe.java'])
 ref=json.loads((O/'capture-reference.json').read_text());b=ref['baseline'];args=['D:/work/Java/jdk-17.0.7/bin/java.exe','-cp',cp,'AcquiredDmlProjectionProbe',b['path'],b['sha256'],(O/'raw-leaf.txt').read_text()]
 for seq in ref['sequences']:
  p=O/'capture'/('sequence-'+str(seq)+'.oracap');args += [str(p),sha(p)]
 projection=json.loads(run('faulty-typed-negative-input',args));assert len(projection['envelopes'])==2;assert projection['evidence']['baselineSha256']==b['sha256']
 check=sql('http-preflight',"select 'USER|'||count(*) from dba_users where username='"+U+"';\nselect 'TABLES|'||count(*) from dba_tables where owner='ORKTGT_CMT_20261005' and table_name like 'SCBAR\\_%' escape '\\';");assert 'USER|0' in check and 'TABLES|0' in check
 password='P'+secrets.token_hex(12)+'a1';sql('http-control-once','create user '+U+' identified by "'+password+'" default tablespace USERS quota unlimited on USERS;\ngrant create session,create table,create sequence to '+U+';',True)
 props=dict(line.split('=',1) for line in (OLD/'runtime.secret.env').read_text().splitlines());admin='H'+secrets.token_hex(12)+'a1'
 props.update(SPRING_DATASOURCE_USERNAME=U,SPRING_DATASOURCE_PASSWORD=password,SERVER_PORT='37220',SH_WORKER_ID='20',SH_ADMIN_INITIAL_PASSWORD=admin,SH_ORACLE_CONSUMER_ID='schema-consumer',SH_ORACLE_ACQUISITION_ENABLED='false',SH_ORACLE_BOUNDED_TARGET_PREFIX='SCBAR',SH_ORACLE_BOUNDED_TARGET_ID='oracle-schema-barrier-SCBAR')
 (O/'http-runtime.secret.env').write_text(''.join(k+'='+v+'\n' for k,v in props.items()));(O/'admin.secret.json').write_text(json.dumps(dict(username='admin',password=admin)))
 (O/'http-execution-pin.json').write_text(json.dumps(dict(product=proof,script_sha256=sha(Path(__file__)),negative_input_only=True),indent=2))
 run('http-start',['docker','run','-d','--name',NAME,'--label','owner=OracleRedoKernel','--label','scope=oracle-schema-barrier-http','--user','0','--network','container:'+S,'--workdir','/evidence','--env-file',O/'http-runtime.secret.env','--mount','type=bind,source='+str(O)+',target=/evidence','--entrypoint','java','flink:1.18-java17','-Xmx1536m','-jar','/evidence/application.jar'])
elif action=='enroll':
 p=json.loads((O/'faulty-typed-negative-input.stdout.txt').read_text());e=p['evidence'];source=':'.join(str(e['source'][k]) for k in ['dbid','activationId','compatibility','thread','resetlogsId','resetlogsScn','containerUid','containerId'])
 ddl='create table '+T+'_IDENT(ID number primary key,TARGET_ID varchar2(128) not null,SOURCE_BINDING varchar2(512) not null,SCHEMA_SHA varchar2(64) not null);\n'
 ddl+="insert into "+T+"_IDENT values(1,'oracle-schema-barrier-SCBAR','"+source+"','"+e['schemaSha256']+"');\n"
 ddl+='create table '+T+'_ROWS(ID number primary key,TXT varchar2(128),NOTE varchar2(128));\ninsert into '+T+"_ROWS values(0,'schema-seed','anchor');\n"
 ddl+='create table '+T+'_APPLIED(EVENT_ID varchar2(80) primary key,PAYLOAD_SHA varchar2(64) not null,BEFORE_IMAGE varchar2(4000),AFTER_IMAGE varchar2(4000),OPERATION varchar2(16) not null);\n'
 ddl+='create table '+T+'_MEMBERS(BATCH_SHA varchar2(64),ORDINAL number,EVENT_ID varchar2(80) references '+T+'_APPLIED(EVENT_ID),primary key(BATCH_SHA,ORDINAL),unique(BATCH_SHA,EVENT_ID));\n'
 ddl+='create table '+T+'_COMMITS(BATCH_SHA varchar2(64) primary key,TARGET_ID varchar2(128) not null,SCHEMA_SHA varchar2(64) not null,EVENTS_COUNT number not null);\ncommit;';sql('http-target-setup-once',ddl)
 binding=dict(baselinePath='/evidence/initial.snapshot.txt',capturePaths=['/evidence/capture/sequence-'+str(c['sequence'])+'.oracap' for c in e['captures']],evidence=e,envelopesSha256=p['envelopesSha256']);(O/'negative-approved-binding.json').write_text(json.dumps(binding,indent=2))
 text=json.dumps(binding,separators=(',',':'));expr='||\n'.join("to_clob('"+text[i:i+1800].replace("'","''")+"')" for i in range(0,len(text),1800));shas='\n'.join(Path(path).name+' '+c['journalSha256'] for path,c in zip(binding['capturePaths'],e['captures']))
 enrollment="insert into "+U+".SH_ORACLE_INPUT_BINDING values(9001,'"+SOURCE+"','"+WINDOW+"',1,1,'schema-consumer','"+e['schemaSha256']+"','APPROVED_BOUNDED',"+expr+",systimestamp,'negative-schema-counterexample-only');\n"
 enrollment+="insert into "+U+".SH_CAP_WINDOW(ID,SOURCE_ID,ENGINE_TYPE,WINDOW_KEY,ISSUE_SEQ_START,ISSUE_SEQ_END,HEAD_PATH,FROZEN,FILE_SHA256,TOTAL_SIZE,DROPPED_SEGMENTS,STATUS,REGISTERED_AT) values(9002,'"+SOURCE+"','oracle','"+WINDOW+"',1,1,'"+binding['capturePaths'][0]+"',1,'"+shas+"',"+str(sum(c['journalBytes'] for c in e['captures']))+",0,'PENDING',systimestamp);\ncommit;";sql('http-enroll-negative',enrollment)
elif action=='submit':
 r=http('http-submit','/pipeline/tasks',dict(sourceId=SOURCE));(O/'http-task-id.txt').write_text(str(r['data']['id']))
elif action=='task':http(sys.argv[2],'/pipeline/tasks/'+(O/'http-task-id.txt').read_text())
elif action=='reason':sql('http-refusal-reasons',"select 'REFUSAL|'||RC||'|'||RC_REASON from "+U+".SH_PIPELINE_BATCH order by ID;")
elif action=='reason-exact':sql('http-refusal-reasons-exact',"column reason_hex format a1000\nselect 'REASON_HEX|'||RC||'|'||rawtohex(utl_raw.cast_to_raw(RC_REASON)) reason_hex from "+U+".SH_PIPELINE_BATCH order by ID;")
elif action=='resume':http('http-repeat-refusal','/pipeline/tasks/'+(O/'http-task-id.txt').read_text()+'/resume',{})
elif action=='snapshot':
 text="select 'TARGET_ROW|'||ID||'|'||TXT||'|'||NOTE from "+T+"_ROWS order by ID;\n"
 for suffix in ['APPLIED','MEMBERS','COMMITS']:text+="select 'TARGET_"+suffix+"|'||count(*) from "+T+'_'+suffix+';\n'
 text+="select 'BATCH|'||STATE||'|'||EVENT_COUNT from "+U+".SH_PIPELINE_BATCH order by ID;\nselect 'TASK|'||STATE from "+U+".SH_TASK;\nselect 'CP|'||nvl(cast(CHECKPOINT_JSON as varchar2(4000)),'<NULL>') from "+U+".SH_TASK;\nselect 'WINDOW|'||STATUS from "+U+".SH_CAP_WINDOW;\nselect 'LEDGER|'||count(*) from "+U+".SH_EVENT_LEDGER;\nselect 'FAIL|'||STAGE||'|'||REASON||'|'||DETAIL from "+U+".SH_FAILCLOSED_RECORD;\nselect 'LOGGING|'||supplemental_log_data_min||'|'||supplemental_log_data_all from v$database;\nselect 'OLD|'||ID||'|'||TXT||'|'||NOTE from ORKLAB.T_BASELINE_20261004;";sql(sys.argv[2],text)
elif action=='stop':
 assert subprocess.check_output(['docker','inspect',NAME,'--format','{{.Config.Labels.owner}}|{{.Config.Labels.scope}}']).decode().strip()=='OracleRedoKernel|oracle-schema-barrier-http'
 run('http-stop',['docker','stop','-t','20',NAME]);run('http-final-state',['docker','inspect',NAME,'--format','{{.State.Status}}|{{.State.ExitCode}}']);run('http-complete-logs',['docker','logs',NAME])
else:raise ValueError(action)
print('SCHEMA_BARRIER_HTTP_PHASE|'+action)
