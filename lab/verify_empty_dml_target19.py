"""Native audit plus empty-batch commit/checkpoint and zero-event receipt refusal; source is frozen."""
import base64,hashlib,json,shutil,subprocess,sys
from pathlib import Path
R=Path(__file__).resolve().parent.parent;F=R/'lab/out/empty-dml-capture-20261005';O=R/'lab/out/oracle-empty-target-20261005';B=R/'lab/out/release-empty-foreign-begin-r2-20261005';T=R/'lab/out/oracle-target-20261005';SOURCE='oraredo-19c-lab-20261004'
resume=sys.argv[1:]==['--resume-target-preflight']
if resume:
    assert (O/'target-preflight.rc.txt').read_text()=='1' and not (O/'target-5-table-setup.rc.txt').exists()
else:O.mkdir(parents=True,exist_ok=False)
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
def run(n,args,data=None,rc=0):
    args=list(map(str,args))
    if resume and (O/(n+'.rc.txt')).exists():
        assert int((O/(n+'.rc.txt')).read_text())==rc and json.loads((O/(n+'.command.json')).read_text())==args
        return subprocess.CompletedProcess(args,rc,(O/(n+'.stdout.txt')).read_bytes(),(O/(n+'.stderr.txt')).read_bytes())
    assert not (O/(n+'.rc.txt')).exists();(O/(n+'.command.json')).write_text(json.dumps(args));p=subprocess.run(args,input=data,capture_output=True,timeout=240)
    for ext,v in [('stdout',p.stdout),('stderr',p.stderr)]:(O/(n+'.'+ext+'.txt')).write_bytes(v)
    (O/(n+'.rc.txt')).write_text(str(p.returncode));assert p.returncode==rc,(n,p.stderr)
    return p
proof=json.loads((F/'proof.json').read_text());assert (F/'complete-batch-r2.rc.txt').read_text()=='0' and not any(s.startswith('EVENT|') for s in (F/'complete-batch-r2.stdout.txt').read_text().splitlines())
for n in ['kernel','adapter','spi']:shutil.copyfile(B/(n+'.jar'),O/(n+'.jar'))
pins=json.loads((B/'proof.json').read_text())
for n in ['kernel','adapter','spi']:assert sha(O/(n+'.jar'))==pins[n+'_sha256']
shutil.copyfile(T/'ojdbc8.jar',O/'ojdbc8.jar')
if not resume:shutil.copytree(F/'baseline',O/'baseline')
capture=F/'capture'/('sequence-'+str(proof['sequence'])+'.oracap');assert sha(capture)==proof['capture_sha256'];shutil.copyfile(capture,O/capture.name)
baseline=O/'baseline/snapshot.stdout.txt';digest=proof['baseline_sha256'];leaf=proof['leaf'];assert sha(baseline)==digest
classes=O/'classes'
if not resume:classes.mkdir()
cp=';'.join(str(O/(n+'.jar')) for n in ['kernel','adapter','spi','ojdbc8'])+';'+str(classes)
names=['OracleDmlCheckpointWorker.java','OracleProjectionProbe.java','CapturedRowsProbe.java','VerifiedHeapRowsProbe.java','PhysicalTransactionProbe.java']
run('compile',['D:/work/Java/jdk-17.0.7/bin/javac.exe','-encoding','UTF-8','-cp',cp,'-d',classes]+[R/'lab'/n for n in names])
java=['D:/work/Java/jdk-17.0.7/bin/java.exe','-cp',cp]
physical=run('capture-physical',java+['CapturedRowsProbe','physical',baseline,digest,leaf,O/capture.name,sha(capture)]).stdout.decode()
run('native-physical',[sys.executable,R/'lab/compare_typed_physical.py',baseline,O/'capture-physical.stdout.txt',F/'dump.trc'])
xid=next(s.split('|')[1] for s in (F/'insert-rollback-once.stdout.txt').read_text().splitlines() if s.startswith('XID|'))
assert 'TX|'+xid+'|2|2|0|true' in physical
assert run('capture-state',java+['CapturedRowsProbe','state',baseline,digest,leaf,O/capture.name,sha(capture)]).stdout==b''
args=['OracleDmlCheckpointWorker','inspect','unused',O,'EMPT',baseline,digest,leaf,'1',O/capture.name,sha(capture)]
inspect=run('inspect',java+args).stdout.decode();lines=[s.split('|') for s in inspect.splitlines()];assert len(lines)==1 and lines[0][0]=='WINDOW' and lines[0][4]=='0';w=lines[0][1:]
batch,schema=w[1:3]
def sql(n,text):
    body='whenever sqlerror exit failure rollback\nwhenever oserror exit failure rollback\nset pages 0 lines 32767 feedback off heading off\nalter session set container=ORKPDB;\n'+text+'\nexit success\n';(O/(n+'.sql')).write_text(body)
    return run(n,['docker','exec','-i',SOURCE,'sqlplus','-s','/','as','sysdba'],body.encode()).stdout.decode()
assert run('owner',['docker','inspect',SOURCE,'--format','{{.Config.Labels.owner}}|{{.State.Status}}']).stdout.decode().strip()=='OracleRedoKernel|running'
source_sql="select 'CONFIG|'||dbid||'|'||activation#||'|'||supplemental_log_data_min||'|'||supplemental_log_data_all from v$database;\nselect 'SOURCE_EMPTY_COUNT|'||count(*) from ORKLAB.T_DML_EMPTY_20261005;\nselect 'OLD|'||ID||'|'||TXT||'|'||NOTE from ORKLAB.T_BASELINE_20261004;"
source_before=sql('source-before-target',source_sql);assert 'SOURCE_EMPTY_COUNT|0' in source_before and 'CONFIG|1276726821|1276717861|NO|NO' in source_before and 'OLD|11|blocking-closed|new-note' in source_before
check=sql('target-preflight-r2',"select 'COUNT|'||count(*) from dba_tables where owner='ORKTGT_CMT_20261005' and substr(table_name,1,5)='EMPT_';");assert 'COUNT|0' in check
old=(T/'target-setup-password29.sql.redacted').read_text();ddl=old[old.index('create table ORKTGT_CMT_20261005.NORM_IDENT'):old.index('create table ORKTGT_CMT_20261005.PRE_IDENT')]
ddl=ddl.replace('NORM','EMPT').replace('oracle-target-EMPT-20261005','oracle-dml-EMPT-20261005').replace('b38658100a383c1a12ce0f2d560757864458261c71119102622acbdbcf07d3ce',schema).replace("insert into ORKTGT_CMT_20261005.EMPT_ROWS values(11,'blocking-closed','new-note');\n",'')
assert 'insert into ORKTGT_CMT_20261005.EMPT_ROWS' not in ddl;sql('target-5-table-setup',ddl+'commit;')
consumer=O/'consumer';consumer.mkdir()
command=['docker','run','--rm','--label','owner=OracleRedoKernel','--label','scope=oracle-empty-target','--user','0','--network','container:'+SOURCE,'--mount','type=bind,source='+str(T)+',target=/old,readonly','--mount','type=bind,source='+str(O)+',target=/evidence','--entrypoint','java','flink:1.18-java17','-cp','/evidence/kernel.jar:/evidence/adapter.jar:/evidence/spi.jar:/evidence/ojdbc8.jar:/evidence/classes','OracleDmlCheckpointWorker','consume','/old/target.secret','/evidence/consumer','EMPT','/evidence/baseline/snapshot.stdout.txt',digest,leaf,'1','/evidence/'+capture.name,sha(capture)]
expected='CONSUMED|'+w[4]+'|'+w[5]+'|'+w[6]+'|0|1\n'
assert run('consume',command).stdout.decode()==expected
checkpoint=(consumer/'checkpoint').read_bytes();ack=(consumer/(batch+'.ack')).read_bytes()
assert run('repeat',command).stdout.decode()==expected and (consumer/'checkpoint').read_bytes()==checkpoint and (consumer/(batch+'.ack')).read_bytes()==ack
query="select 'STATE|'||(select count(*) from ORKTGT_CMT_20261005.EMPT_ROWS)||'|'||(select count(*) from ORKTGT_CMT_20261005.EMPT_APPLIED)||'|'||(select count(*) from ORKTGT_CMT_20261005.EMPT_MEMBERS)||'|'||(select count(*) from ORKTGT_CMT_20261005.EMPT_COMMITS) from dual;\nselect 'RECEIPT|'||BATCH_SHA||'|'||TARGET_ID||'|'||SCHEMA_SHA||'|'||EVENTS_COUNT from ORKTGT_CMT_20261005.EMPT_COMMITS;"
snapshot=sql('healthy-state',query);assert [s.strip() for s in snapshot.splitlines() if s.strip()]==['STATE|0|0|0|1','RECEIPT|'+batch+'|oracle-dml-EMPT-20261005|'+schema+'|0']
sql('remove-owned-empty-commit',"delete from ORKTGT_CMT_20261005.EMPT_COMMITS where BATCH_SHA='"+batch+"';\ncommit;")
bad=run('ack-without-empty-commit',command,rc=1);assert not bad.stdout and b'ORACLE_TARGET_ACK_WITHOUT_COMMIT' in bad.stderr
assert 'STATE|0|0|0|0' in sql('refused-state',query) and (consumer/'checkpoint').read_bytes()==checkpoint and (consumer/(batch+'.ack')).read_bytes()==ack
sql('restore-owned-empty-commit',"insert into ORKTGT_CMT_20261005.EMPT_COMMITS values('"+batch+"','oracle-dml-EMPT-20261005','"+schema+"',0);\ncommit;")
assert run('restore-repeat',command).stdout.decode()==expected and sql('final-state',query)==snapshot
assert (consumer/'checkpoint').read_bytes()==checkpoint and (consumer/(batch+'.ack')).read_bytes()==ack
assert ack==('ORK_ORACLE_TARGET_ACK_V1|oracle-dml-EMPT-20261005|'+batch+'|'+schema+'|0\n').encode()
parts=checkpoint.decode().splitlines();assert parts[2]=='FRONTIER|'+batch and parts[-1]=='SHA256|'+hashlib.sha256(('\n'.join(parts[:-1])+'\n').encode()).hexdigest()
b64=lambda s:base64.urlsafe_b64encode(s.encode()).decode().rstrip('=')
assert parts[1]=='TARGET|'+b64('ORKTGT_CMT_20261005.EMPT|oracle-dml-EMPT-20261005')
scn=next(s.split('|')[1] for s in baseline.read_text().splitlines() if s.startswith('SNAPSHOT_SCN|'))
assert parts[3].split('|')==['BATCH',batch,schema,digest,scn,leaf,'1276726821:1276717861:318767104:1:1245693159:1920977:677737149:3',w[4],w[5],w[6],'0',str(proof['sequence'])+':'+sha(capture)]
assert not run('helpers-exited',['docker','ps','--filter','label=scope=oracle-empty-target','--format','{{.Names}}']).stdout.strip()
assert sql('source-after-target',source_sql)==source_before
(O/'proof.json').write_text(json.dumps({'sequence':proof['sequence'],'rollback_xid':xid,'raw_events':0,'native_graph_zero_differences':True,'rollback_original_compensated_live':[2,2,0],'target_rows_applied_members_commits':[0,0,0,1],'target_batch':batch,'target_schema':schema,'checkpoint_all_fields_zero_differences':True,'all_repeats_zero':True,'ack_without_actual_empty_commit_refused':True,'source_steps_replayed':False,'full_product_accepted':False},indent=2));print((O/'proof.json').read_text())
