"""New owned targets; frozen source DML is never rerun. Post-commit proof refusal must preserve watermarks."""
import hashlib,json,shutil,subprocess
from pathlib import Path
R=Path(__file__).resolve().parent.parent;O=R/'lab/out/oracle-postcommit-proof-20261005';B=R/'lab/out/oracle-readonly-receipt-20261005';T=R/'lab/out/oracle-target-20261005';F=R/'lab/out/oracle-null-sides-target-20261005';E=R/'lab/out/oracle-empty-target-20261005';SOURCE='oraredo-19c-lab-20261004'
O.mkdir(exist_ok=False);sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest();proof=json.loads((B/'build-proof.json').read_text())
for n in ['adapter','kernel','spi']:
    assert sha(B/(n+'.jar'))==proof[n+'_sha256'];shutil.copyfile(B/(n+'.jar'),O/(n+'.jar'))
shutil.copyfile(B/'ojdbc8.jar',O/'ojdbc8.jar')
def run(n,args,data=None,rc=0):
    assert not (O/(n+'.rc.txt')).exists();args=list(map(str,args));(O/(n+'.command.json')).write_text(json.dumps(args))
    with (O/(n+'.stdout.txt')).open('wb') as out,(O/(n+'.stderr.txt')).open('wb') as err:p=subprocess.run(args,input=data,stdout=out,stderr=err,timeout=600)
    (O/(n+'.rc.txt')).write_text(str(p.returncode));assert p.returncode==rc,n
    return (O/(n+'.stdout.txt')).read_bytes()
def sql(n,text):
    body='whenever sqlerror exit failure rollback\nwhenever oserror exit failure rollback\nset pages 0 feedback off heading off lines 32767 trimspool on\nalter session set container=ORKPDB;\n'+text+'\nexit success\n';(O/(n+'.sql')).write_text(body)
    return run(n,['docker','exec','-i',SOURCE,'sqlplus','-s','/','as','sysdba'],body.encode())
assert run('owner',['docker','inspect',SOURCE,'--format','{{.Config.Labels.owner}}|{{.State.Status}}']).decode().strip()=='OracleRedoKernel|running'
source_sql="select 'CONFIG|'||dbid||'|'||activation#||'|'||supplemental_log_data_min||'|'||supplemental_log_data_all from v$database;\nselect 'OLD|'||ID||'|'||TXT||'|'||NOTE from ORKLAB.T_BASELINE_20261004;\n"+'\n'.join("select 'SOURCE|"+t+"|'||ID||'|'||TXT||'|'||NOTE from ORKLAB."+t+' order by ID;' for t in ['T_DML_CAPTURE_20261005','T_DML_NULL_20261005','T_DML_MIX_20261005'])
source_before=sql('source-before',source_sql);assert b'CONFIG|1276726821|1276717861|NO|NO' in source_before and b'OLD|11|blocking-closed|new-note' in source_before
configs=json.loads((R/'lab/out/oracle-registry-20261005/configs.json').read_text());prefixes=['PCNON','PCNUL','PCMIX','PCEMP']
assert b'COUNT|0' in sql('owned-table-preflight',"select 'COUNT|'||count(*) from dba_tables where owner='ORKTGT_CMT_20261005' and substr(table_name,1,instr(table_name,'_')-1) in ("+','.join("'"+p+"'" for p in prefixes)+');')
ddl=''
for c,prefix in zip(configs,prefixes):
    t='ORKTGT_CMT_20261005.'+prefix;old='ORKTGT_CMT_20261005.'+c['prefix'];c['target_prefix']=prefix
    ddl+='create table '+t+'_IDENT(ID number primary key,TARGET_ID varchar2(128) not null,SOURCE_BINDING varchar2(512) not null,SCHEMA_SHA varchar2(64) not null);\n'
    ddl+="insert into "+t+"_IDENT select ID,'oracle-dml-"+prefix+"-20261005',SOURCE_BINDING,SCHEMA_SHA from "+old+'_IDENT;\n'
    ddl+='create table '+t+'_ROWS(ID number primary key,TXT varchar2(128),NOTE varchar2(128));\ninsert into '+t+'_ROWS select ID,TXT,NOTE from '+old+'_ROWS;\n'
    ddl+='create table '+t+'_APPLIED(EVENT_ID varchar2(80) primary key,PAYLOAD_SHA varchar2(64) not null,BEFORE_IMAGE varchar2(4000),AFTER_IMAGE varchar2(4000),OPERATION varchar2(16) not null);\n'
    ddl+='create table '+t+'_MEMBERS(BATCH_SHA varchar2(64),ORDINAL number,EVENT_ID varchar2(80) references '+t+'_APPLIED(EVENT_ID),primary key(BATCH_SHA,ORDINAL),unique(BATCH_SHA,EVENT_ID));\n'
    ddl+='create table '+t+'_COMMITS(BATCH_SHA varchar2(64) primary key,TARGET_ID varchar2(128) not null,SCHEMA_SHA varchar2(64) not null,EVENTS_COUNT number not null);\n'
sql('create-owned-targets',ddl+'commit;');classes=O/'classes';classes.mkdir();cp=';'.join(str(O/(n+'.jar')) for n in ['adapter','kernel','spi','ojdbc8'])
run('compile',['D:/work/Java/jdk-17.0.7/bin/javac.exe','-encoding','UTF-8','-cp',cp,'-d',classes,R/'lab/OraclePostCommitProofWorker.java'])
def snapshot(n,prefix):
    t='ORKTGT_CMT_20261005.'+prefix+'_';body="select 'STATE|'||(select count(*) from "+t+"APPLIED)||'|'||(select count(*) from "+t+"MEMBERS)||'|'||(select count(*) from "+t+'COMMITS) from dual;\n'
    for table,fields,order in [('IDENT','ID,TARGET_ID,SOURCE_BINDING,SCHEMA_SHA','ID'),('APPLIED','EVENT_ID,PAYLOAD_SHA,BEFORE_IMAGE,AFTER_IMAGE,OPERATION','EVENT_ID'),('MEMBERS','BATCH_SHA,ORDINAL,EVENT_ID','BATCH_SHA,ORDINAL'),('COMMITS','BATCH_SHA,TARGET_ID,SCHEMA_SHA,EVENTS_COUNT','BATCH_SHA'),('ROWS','ID,TXT,NOTE','ID')]:
        body+="select '"+table+"|'||"+"||'|'||".join(fields.split(','))+' from '+t+table+' order by '+order+';\n'
    return sql(n,body)
def command(c,mode):
    a=['docker','run','--rm','--label','owner=OracleRedoKernel','--label','scope=oracle-postcommit-proof','--user','0','--network','container:'+SOURCE]
    for path,target in [(O,'/evidence'),(T,'/old'),(F,'/frozen'),(E,'/empty')]:a+=['--mount','type=bind,source='+str(path)+',target='+target+(',readonly' if target!='/evidence' else '')]
    return a+['--entrypoint','java','flink:1.18-java17','-Xmx1536m','-cp','/evidence/adapter.jar:/evidence/kernel.jar:/evidence/spi.jar:/evidence/ojdbc8.jar:/evidence/classes','OraclePostCommitProofWorker','/old/target.secret',c['target_prefix'],'/evidence/'+c['target_prefix'],mode,c['baseline_path'],c['baseline_sha256'],str(c['leaf'])]+c['captures']
results=[]
for c,events in zip(configs,[2,2,3,0]):
    p=c['target_prefix'];directory=O/p;directory.mkdir();before=snapshot(p+'-before',p);assert b'STATE|0|0|0' in before
    assert not run(p+'-proof-unavailable',command(c,'fail'),rc=1)
    assert b'ORACLE_POSTCOMMIT_PROOF_UNAVAILABLE' in (O/(p+'-proof-unavailable.stderr.txt')).read_bytes()
    assert not (directory/'checkpoint').exists() and not list(directory.glob('*.ack*')) and not (directory/'checkpoint.tmp').exists()
    committed=snapshot(p+'-committed-unacked',p);assert ('STATE|'+str(events)+'|'+str(events)+'|1').encode() in committed
    assert run(p+'-resume',command(c,'resume')).decode().strip()=='POST_COMMIT_RECOVERED|'+p+'|0|1'
    checkpoint=(directory/'checkpoint').read_bytes();ack=list(directory.glob('*.ack'));assert len(ack)==1;ack_bytes=ack[0].read_bytes()
    assert run(p+'-repeat',command(c,'resume')).decode().strip()=='POST_COMMIT_RECOVERED|'+p+'|0|1'
    assert (directory/'checkpoint').read_bytes()==checkpoint and ack[0].read_bytes()==ack_bytes
    assert snapshot(p+'-final',p)==committed
    results.append({'case':c['prefix'],'target_prefix':p,'committed_events':events,'postcommit_failure_no_checkpoint_or_ack':True,'recovery_and_repeat_new_applications':0,'target_unchanged_on_recovery':True})
assert sql('source-after',source_sql)==source_before
assert not run('helpers-exited',['docker','ps','--filter','label=scope=oracle-postcommit-proof','--format','{{.Names}}']).strip()
(O/'config.json').write_text(json.dumps(configs,indent=2));(O/'proof.json').write_text(json.dumps({'cases':results,'source_business_dml':0,'source_config_changed':False,'new_owned_target_tables':20,'production_fault_injection':False,'independent_comparison':'PENDING','product_facade_not_executed':True},indent=2));print('POST_COMMIT_PROOF_BOUNDARY_VERIFIED|4')
