"""One-shot new target DML/checkpoint plus four real client kills. Source unchanged."""
import base64,hashlib,json,shutil,subprocess,time,zipfile
from pathlib import Path
R=Path(__file__).resolve().parent.parent;O=R/'lab/out/oracle-checkpoint-crash-20261005';T=R/'lab/out/oracle-target-20261005';B=R/'lab/out/release-oracle-checkpoint-20261005';SOURCE='oraredo-19c-lab-20261004';IMAGE='flink:1.18-java17'
O.mkdir(parents=True,exist_ok=False)
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
def run(n,args,data=None,rc=0):
    args=list(map(str,args));(O/(n+'.command.json')).write_text(json.dumps(args))
    p=subprocess.run(args,input=data,capture_output=True,timeout=180)
    for ext,v in [('stdout',p.stdout),('stderr',p.stderr)]:(O/(n+'.'+ext+'.txt')).write_bytes(v)
    (O/(n+'.rc.txt')).write_text(str(p.returncode));assert p.returncode==rc,n
    return p
assert run('owner',['docker','inspect',SOURCE,'--format','{{.Config.Labels.owner}}|{{.State.Status}}']).stdout.decode().strip()=='OracleRedoKernel|running'
assert not run('no-old-helper',['docker','ps','--filter','label=scope=oracle-checkpoint-crash','--format','{{.Names}}']).stdout.strip()
proof=json.loads((B/'proof.json').read_text())
for n in ['adapter','kernel','spi']:
    assert sha(B/(n+'.jar'))==proof[n+'_sha256'];shutil.copyfile(B/(n+'.jar'),O/(n+'.jar'))
manifest=json.loads((R/'docs/workorders/evidence/DELIVERY-oracle-target-20261005.json').read_text(encoding='utf-8'));pins={f['path']:f['sha256'] for f in manifest['files']}
assert sha(T/'ojdbc8.jar')==pins[(T/'ojdbc8.jar').relative_to(R).as_posix()]
for seq in [40,41,42]:p=T/('sequence-'+str(seq)+'.oracap');assert sha(p)==pins[p.relative_to(R).as_posix()]
classes=O/'classes';classes.mkdir();cp=';'.join(str(O/(n+'.jar')) for n in ['adapter','kernel','spi'])+';'+str(T/'ojdbc8.jar')
run('compile-lab',['D:/work/Java/jdk-17.0.7/bin/javac.exe','--add-exports','java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED','-encoding','UTF-8','-cp',cp,'-d',classes]+[R/'lab'/n for n in ['OracleCheckpointCrashWorker.java','OracleCheckpointAgent.java','OracleCheckpointPause.java']])
(O/'agent.mf').write_text('Manifest-Version: 1.0\nPremain-Class: OracleCheckpointAgent\n\n')
run('package-agent',['D:/work/Java/jdk-17.0.7/bin/jar.exe','--create','--file',O/'lab-agent.jar','--manifest',O/'agent.mf','-C',classes,'.'])
with zipfile.ZipFile(O/'adapter.jar') as z:original_class=hashlib.sha256(z.read('com/streamhouse/engines/oracle/OracleConsumerCheckpoint.class')).hexdigest()
def sql(n,text):
    body='whenever sqlerror exit failure rollback\nset pages 0 lines 32767 feedback off heading off echo off\n'+text+'\nexit success\n';(O/(n+'.sql')).write_text(body)
    return run(n,['docker','exec','-i',SOURCE,'sqlplus','-s','/','as','sysdba'],body.encode()).stdout
source_sql="select 'CONFIG|'||dbid||'|'||activation#||'|'||supplemental_log_data_min||'|'||supplemental_log_data_all from v$database;\nalter session set container=ORKPDB;\nselect 'SOURCE_ROW|'||ID||'|'||TXT||'|'||NOTE from ORKLAB.T_BASELINE_20261004;"
source_before=sql('source-before',source_sql);assert b'CONFIG|1276726821|1276717861|NO|NO' in source_before and b'SOURCE_ROW|11|blocking-closed|new-note' in source_before
prefixes=['CPNO','CPBE','CPAF','CPTM','CPAK']
check=sql('target-preflight',"alter session set container=ORKPDB;\nselect 'COUNT|'||count(*) from dba_tables where owner='ORKTGT_CMT_20261005' and substr(table_name,1,5) in ('CPNO_','CPBE_','CPAF_','CPTM_','CPAK_');")
assert b'COUNT|0' in check
old=(T/'target-setup-password29.sql.redacted').read_text();ddl=old[old.index('create table ORKTGT_CMT_20261005.NORM_IDENT'):old.index('create table ORKTGT_CMT_20261005.PRE_IDENT')]
assert '<target-secret>' not in ddl
sql('target-25-table-setup','alter session set container=ORKPDB;\n'+''.join(ddl.replace('NORM',p) for p in prefixes)+'commit;')
window=json.loads((R/'lab/out/oracle-overlap-20261005/proof.json').read_text());full=window['full_batch'];prefix=window['prefix_batch'];schema='b38658100a383c1a12ce0f2d560757864458261c71119102622acbdbcf07d3ce'
for p in prefixes:(O/p).mkdir()
def command(name,p,stage='none',current=full,detach=False):
    args=['docker','run','--name',name,'--label','owner=OracleRedoKernel','--label','scope=oracle-checkpoint-crash','--label','fixture='+p,'--user','0','--network','container:'+SOURCE,
          '--mount','type=bind,source='+str(T)+',target=/old,readonly','--mount','type=bind,source='+str(O)+',target=/evidence','--entrypoint','java']
    args+=['-d'] if detach else ['--rm']
    args += [IMAGE]
    if stage in ['checkpoint-temp','checkpoint-forced']:
        args += ['--add-exports=java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED','-javaagent:/evidence/lab-agent.jar','-DlabStage='+stage,'-DlabReady=/evidence/'+p+'/ready','-DlabTransform=/evidence/'+p+'/transform.sha256']
    args += ['-cp','/evidence/adapter.jar:/evidence/kernel.jar:/evidence/spi.jar:/old/ojdbc8.jar:/evidence/classes','OracleCheckpointCrashWorker','/old/target.secret','/evidence/'+p,p,stage,current,'/old/snapshot.txt','646ed950ea5d54be77ea3ba83f3af31eb181a26ab52dc0dbc01ec2d9ab3048bf']
    for seq in [40,41,42]:f=T/('sequence-'+str(seq)+'.oracap');args += ['/old/'+f.name,sha(f)]
    return args
query=(T/'POST-final.sql.redacted').read_text()
def state(n,p,counts):
    result=run(n,['docker','exec','-i',SOURCE,'sqlplus','-s','/','as','sysdba'],query.replace('POST',p).encode()).stdout.decode()
    assert 'STATE|'+p+'|'+'|'.join(map(str,counts)) in result,n
    return result
assert run('normal-prefix',command('ork-cp-normal-prefix','CPNO',current=prefix)).stdout==b'CONSUMED|41|7|2366749|2|1\n'
state('normal-prefix-state','CPNO',(2,2,1));prefix_ack=(O/'CPNO'/(prefix+'.ack')).read_bytes()
assert run('normal-full',command('ork-cp-normal-full','CPNO')).stdout==b'CONSUMED|42|9|2366759|1|2\n'
normal_checkpoint=(O/'CPNO/checkpoint').read_bytes();assert (O/'CPNO'/(prefix+'.ack')).read_bytes()==prefix_ack
for label,current in [('full',full),('prefix',prefix)]:
    assert run('normal-repeat-'+label,command('ork-cp-normal-repeat-'+label,'CPNO',current=current)).stdout==b'CONSUMED|42|9|2366759|0|2\n'
    assert (O/'CPNO/checkpoint').read_bytes()==normal_checkpoint
kill_results=[]
for p,stage in [('CPBE','before-target-commit'),('CPAF','after-target-commit'),('CPTM','checkpoint-temp'),('CPAK','checkpoint-forced')]:
    name='ork-cp-'+p.lower();cid=run(p+'-start',command(name,p,stage,detach=True)).stdout.decode().strip();assert cid
    deadline=time.time()+60
    while not (O/p/'ready').exists() and time.time()<deadline:time.sleep(.2)
    if not (O/p/'ready').exists():
        run(p+'-unexpected-state',['docker','inspect',cid]);run(p+'-unexpected-logs',['docker','logs',cid]);raise AssertionError(p+' did not reach expected live gate')
    assert (O/p/'ready').read_text()==stage
    assert run(p+'-live-owner',['docker','inspect',cid,'--format','{{.Config.Labels.owner}}|{{.Config.Labels.scope}}|{{.Config.Labels.fixture}}|{{.State.Status}}']).stdout.decode().strip()=='OracleRedoKernel|oracle-checkpoint-crash|'+p+'|running'
    if p=='CPBE':
        competing=run('second-process-refused',command('ork-cp-second','CPBE'),rc=1)
        assert competing.stdout==b'' and b'ORACLE_CONSUMER_ALREADY_RUNNING' in competing.stderr
    state(p+'-paused-target',p,(0,0,0) if p=='CPBE' else (3,3,1))
    folder=O/p;old_file=None
    if p in ['CPBE','CPAF']:assert not (folder/'checkpoint').exists() and not (folder/'checkpoint.tmp').exists()
    elif p=='CPTM':
        assert not (folder/'checkpoint').exists();old_file=(folder/'checkpoint.tmp').read_bytes()
    else:old_file=(folder/'checkpoint').read_bytes()
    assert not list(folder.glob('*.ack'))
    if p in ['CPTM','CPAK']:
        hashes=(folder/'transform.sha256').read_text().splitlines();assert len(hashes)==2 and hashes[0]==original_class and hashes[0]!=hashes[1]
    run(p+'-kill',['docker','kill','--signal','SIGKILL',cid]);assert run(p+'-exit',['docker','wait',cid]).stdout.decode().strip()=='137'
    assert run(p+'-killed-logs',['docker','logs',cid]).stdout==b''
    run(p+'-remove-owned-client',['docker','rm',cid])
    state(p+'-after-kill-target',p,(0,0,0) if p=='CPBE' else (3,3,1))
    expected=3 if p=='CPBE' else 0
    assert run(p+'-resume',command(name+'-resume',p)).stdout==('CONSUMED|42|9|2366759|'+str(expected)+'|1\n').encode()
    if p=='CPTM':
        saved=list(folder.glob('checkpoint.tmp.retained-*'));assert len(saved)==1 and saved[0].read_bytes()==old_file
    if old_file is not None:assert (folder/'checkpoint').read_bytes()==old_file
    assert run(p+'-repeat',command(name+'-repeat',p)).stdout==b'CONSUMED|42|9|2366759|0|1\n'
    state(p+'-final-target',p,(3,3,1));kill_results.append({'fixture':p,'gate':stage,'kill_rc':137,'resume_new_dml':expected,'repeat_new_dml':0,'agent_used':p in ['CPTM','CPAK']})
# Independent complete images use original source SQL and previously native-verified event identities.
meta=[s.split('|') for s in (R/'lab/out/captured-batch-20261005/actual.stdout.txt').read_text().splitlines() if s.startswith('EVENT|')]
def image(txt,note):
    b64=lambda s:base64.urlsafe_b64encode(s.encode()).decode().rstrip('=')
    return 'ID=NUMBER:11;TXT=VARCHAR2:'+b64(txt)+';NOTE=VARCHAR2:'+b64(note)
for p in prefixes:
    text=state(p+'-independent-read',p,(3,5,2) if p=='CPNO' else (3,3,1));rows=[s.strip().split('|') for s in text.splitlines() if s.strip()]
    expected=[];before=image('blocking-closed','new-note')
    for seq,m in zip([40,41,42],meta):
        truth=[s.strip().split('|') for s in (R/'lab/out/closed-follow-20261005'/('step-'+str(seq)+'.stdout.txt')).read_text().splitlines() if s.strip().startswith('TRUTH|')];assert len(truth)==1 and truth[0][1:3]==[str(seq),'11']
        after=image(truth[0][3],truth[0][4]);expected.append(['APPLIED',p,'oracle1:'+m[1],m[2],before,after,'UPDATE']);before=after
    assert sorted(s for s in rows if s[0]=='APPLIED')==sorted(expected)
    batches=[(prefix,2),(full,3)] if p=='CPNO' else [(full,3)]
    members=[['MEMBER',p,b,str(i),'oracle1:'+m[1]] for b,count in batches for i,m in enumerate(meta[:count])]
    receipts=[['RECEIPT',p,b,'oracle-target-'+p+'-20261005',schema,str(count)] for b,count in batches]
    assert sorted(s for s in rows if s[0]=='MEMBER')==sorted(members) and sorted(s for s in rows if s[0]=='RECEIPT')==sorted(receipts)
    assert [s for s in rows if s[0]=='ROW']==[['ROW',p,'11','blocking-closed','new-note']]
    cp_lines=(O/p/'checkpoint').read_text().splitlines();assert cp_lines[2]=='FRONTIER|'+full
    assert cp_lines[-1]=='SHA256|'+hashlib.sha256(('\n'.join(cp_lines[:-1])+'\n').encode()).hexdigest()
    assert {s.split('|')[1] for s in cp_lines if s.startswith('BATCH|')}=={b for b,count in batches}
    for b,count in batches:assert (O/p/(b+'.ack')).read_bytes()==('ORK_ORACLE_TARGET_ACK_V1|oracle-target-'+p+'-20261005|'+b+'|'+schema+'|'+str(count)+'\n').encode()
assert sql('source-after',source_sql)==source_before
assert run('lgwr',['docker','exec',SOURCE,'cat','/proc/5601/comm']).stdout.decode().strip()=='ora_lgwr_ork19'
assert not run('helpers-exited',['docker','ps','--filter','label=scope=oracle-checkpoint-crash','--format','{{.Names}}']).stdout.strip()
result={'normal_new_dml':[2,1],'normal_repeats':[0,0],'actual_client_kills':kill_results,'cross_process_lock_refused':True,'independent_real_targets':5,'complete_source_sql_images_mismatches':0,'source_dml':0,'source_config_changes':0,'new_owned_target_tables':25,'production_jars_unchanged':True,'fsync_kill_cases_lab_instrumented':2,'power_loss_accepted':False,'product_pipeline_accepted':False,'retention_accepted':False}
(O/'proof.json').write_text(json.dumps(result,indent=2));print(json.dumps(result))
