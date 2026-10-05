"""One new dedicated fixture and two committed source steps. Never repeats DDL/DML."""
import hashlib,json,subprocess,sys,time
from pathlib import Path
R=Path(__file__).resolve().parent.parent;O=R/'lab/out/null-dml-capture-20261005';SOURCE='oraredo-19c-lab-20261004';B=R/'lab/out/baseline-physical-scope-20261005'
assert sys.argv[1:]==['--execute'];O.mkdir(parents=True,exist_ok=False)
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
(O/'executed-driver.py').write_bytes(Path(__file__).read_bytes())
(O/'driver-sha256.txt').write_text(sha(Path(__file__)))
assert sha(B/'kernel-r2.jar')=='58da93ea85f3246d5a48ae914a29e23aa84bc6ade02de619d01e64e4c51d7932'
(O/'kernel-sha256.txt').write_text(sha(B/'kernel-r2.jar'))
def run(n,args,data=None,expected=0):
    (O/(n+'.command.json')).write_text(json.dumps(list(map(str,args))))
    proc=subprocess.run(list(map(str,args)),input=data,capture_output=True,timeout=180)
    for ext,v in [('stdout',proc.stdout),('stderr',proc.stderr)]:(O/(n+'.'+ext+'.txt')).write_bytes(v)
    (O/(n+'.rc.txt')).write_text(str(proc.returncode))
    if expected is not None:assert proc.returncode==expected,n
    return proc

def sql(n,body):
    text='whenever sqlerror exit failure rollback\nwhenever oserror exit failure rollback\nset pages 0 lines 32767 feedback off heading off\n'+body+'\nexit success\n';(O/(n+'.sql')).write_text(text)
    return run(n,['docker','exec','-i',SOURCE,'sqlplus','-s','/','as','sysdba'],text.encode()).stdout.decode()
assert run('owner',['docker','inspect',SOURCE,'--format','{{.Config.Labels.owner}}|{{.Config.Image}}|{{.State.Status}}']).stdout.decode().strip()=='OracleRedoKernel|container-registry.oracle.com/database/enterprise:19.3.0.0|running'
check=sql('preflight',"declare n number;begin select count(*) into n from v$database where dbid=1276726821 and activation#=1276717861 and log_mode='ARCHIVELOG' and supplemental_log_data_min='NO' and supplemental_log_data_all='NO';if n!=1 then raise_application_error(-20001,'source mismatch');end if;end;\n/\nalter session set container=ORKPDB;\nselect 'OBJECT_COUNT|'||count(*) from dba_objects where owner='ORKLAB' and object_name in ('T_DML_NULL_20261005','PK_DML_NULL_20261005');\nselect 'OLD_FIXTURE|'||ID||'|'||TXT||'|'||NOTE from ORKLAB.T_BASELINE_20261004;")
assert 'OBJECT_COUNT|0' in check and 'OLD_FIXTURE|11|blocking-closed|new-note' in check
sql('fixture-once',"alter session set container=ORKPDB;\ncreate table ORKLAB.T_DML_NULL_20261005(ID number not null,TXT varchar2(128),NOTE varchar2(128) invisible,constraint PK_DML_NULL_20261005 primary key(ID));\ninsert into ORKLAB.T_DML_NULL_20261005(ID,TXT,NOTE) values(0,'null-seed','anchor');\ncommit;")
acquirer=O/'acquire-dml-baseline.py';acquirer.write_text((R/'lab/capture_heap_baseline19.py').read_text().replace('T_BASELINE_20261004','T_DML_NULL_20261005').replace('PK_BASELINE_20261004','PK_DML_NULL_20261005'))
run('acquire-baseline',[sys.executable,acquirer,'--execute',O/'baseline'])
snapshot=O/'baseline/snapshot.stdout.txt';receipt=json.loads((O/'baseline/receipt.json').read_text());assert sha(snapshot)==receipt['snapshot_sha256']
classes=O/'classes';classes.mkdir()
run('compile-probe',['D:/work/Java/jdk-17.0.7/bin/javac.exe','-encoding','UTF-8','-cp',B/'kernel-r2.jar','-d',classes,R/'lab/RawDmlLeafProbe.java'])
info=sql('members',"select 'MEMBER|'||member from v$logfile order by group#;\nselect 'CURRENT|'||sequence# from v$log where status='CURRENT';")
members=[x.strip().split('|')[1] for x in info.splitlines() if x.strip().startswith('MEMBER|')];start=int(next(x.strip().split('|')[1] for x in info.splitlines() if x.strip().startswith('CURRENT|')));assert len(members)==3
for name in ['resume_online_observation.py','follow_closed_members.py']:
    actual=O/('executed-'+name);actual.write_bytes((R/'lab'/name).read_bytes());run('copy-'+name,['docker','cp',actual,SOURCE+':/tmp/'+name]);assert sha(actual) in run('sha-'+name,['docker','exec',SOURCE,'sha256sum','/tmp/'+name]).stdout.decode()
remote='/tmp/ork-null-dml-capture-20261005';collector=['docker','exec',SOURCE,'python3','/tmp/follow_closed_members.py',remote,start,220,.25]+members
(O/'collector.command.json').write_text(json.dumps(collector))
with (O/'collector.stdout.txt').open('wb') as out,(O/'collector.stderr.txt').open('wb') as err:
    process=subprocess.Popen(list(map(str,collector)),stdout=out,stderr=err);(O/'collector-host-pid.txt').write_text(str(process.pid))
    def wait(key,value):
        deadline=time.time()+90
        while time.time()<deadline:
            if any(json.loads(line).get(key)==value for line in (O/'collector.stdout.txt').read_text().splitlines()):return
            if process.poll() is not None:raise RuntimeError('collector terminal; inspect same evidence, no replay')
            time.sleep(.1)
        raise RuntimeError('collector live observation timed out; inspect same handle')
    wait('ready',True);archives=[];paths=[]
    for offset,operation in enumerate(['INSERT','DELETE']):
        seq=start+offset
        statement="insert into ORKLAB.T_DML_NULL_20261005(ID,TXT,NOTE) values(11,null,null)" if operation=='INSERT' else "delete from ORKLAB.T_DML_NULL_20261005 where ID=11 and TXT is null and NOTE is null"
        body="declare s number;begin select sequence# into s from v$log where status='CURRENT';if s!="+str(seq)+" then raise_application_error(-20003,'sequence changed');end if;end;\n/\nalter session set container=ORKPDB;\nbegin "+statement+";if sql%rowcount!=1 then raise_application_error(-20004,'fixture row count');end if;end;\n/\nselect 'XID|'||xidusn||'.'||xidslot||'.'||xidsqn from v$transaction where ses_addr=(select saddr from v$session where sid=sys_context('USERENV','SID'));\ncommit;\nselect 'TRUTH|"+operation+"|'||ID||'|'||nvl(TXT,'<NULL>')||'|'||nvl(NOTE,'<NULL>') from ORKLAB.T_DML_NULL_20261005 order by ID;\nselect 'TRUTH_COUNT|"+operation+"|'||count(*) from ORKLAB.T_DML_NULL_20261005;\nalter session set container=CDB$ROOT;\nalter system archive log current;\nselect 'ARCHIVE|'||sequence#||'|'||name from v$archived_log where thread#=1 and sequence#="+str(seq)+" and name is not null order by recid;"
        truth=sql(operation+'-once',body);wait('captured_sequence',seq)
        source_paths=[line.strip().split('|')[2] for line in truth.splitlines() if line.strip().startswith('ARCHIVE|')];assert len(source_paths)==1
        archive=O/('archive-'+str(seq)+'.dbf');run('copy-archive-'+str(seq),['docker','cp',SOURCE+':'+source_paths[0],archive]);archives.append(archive);paths.append(source_paths[0])
    rc=process.wait(timeout=90);(O/'collector.rc.txt').write_text(str(rc));assert rc==0
run('copy-capture',['docker','cp',SOURCE+':'+remote,O/'capture'])
for seq,archive in zip(range(start,start+2),archives):run('capture-archive-'+str(seq),[sys.executable,R/'lab/compare_observation_archive.py',O/'capture'/('sequence-'+str(seq)+'.oracap'),archive])
trace=sql('independent-dump',"alter session set tracefile_identifier='ORK_FULL_DML';\n"+''.join("alter system dump logfile '"+p+"';\n" for p in paths)+"select value from v$diag_info where name='Default Trace File';")
traces=[line.strip() for line in trace.splitlines() if line.strip().startswith('/opt/oracle/') and line.strip().endswith('.trc')];assert len(traces)==1
run('copy-independent-dump',['docker','cp',SOURCE+':'+traces[0],O/'dump.trc'])
inputs=[]
for seq in range(start,start+2):p=O/'capture'/('sequence-'+str(seq)+'.oracap');inputs += [str(p),sha(p)]
leaf=run('raw-index-leaf',['D:/work/Java/jdk-17.0.7/bin/java.exe','-cp',str(B/'kernel-r2.jar')+';'+str(classes),'RawDmlLeafProbe',snapshot,sha(snapshot)]+inputs,expected=None)
if leaf.returncode==0:
    value=leaf.stdout.decode().strip().split('|')[1]
    cmd=['D:/work/Java/jdk-17.0.7/bin/java.exe','-cp',str(B/'kernel-r2.jar')+';'+str(R/'lab/out/captured-batch-20261005/classes'),'CapturedBatchProbe',snapshot,sha(snapshot),value]+inputs
    parsed=run('raw-complete-batch',cmd,expected=None)
    parser_rc=parsed.returncode;parser_events=sum(line.startswith(b'EVENT|') for line in parsed.stdout.splitlines())
else:parser_rc=None;parser_events=0
final=sql('final-source',"alter session set container=ORKPDB;\nselect 'FINAL|'||ID||'|'||nvl(TXT,'<NULL>')||'|'||nvl(NOTE,'<NULL>') from ORKLAB.T_DML_NULL_20261005 order by ID;\nselect 'OLD_FIXTURE|'||ID||'|'||TXT||'|'||NOTE from ORKLAB.T_BASELINE_20261004;")
assert 'FINAL|0|null-seed|anchor' in final and 'OLD_FIXTURE|11|blocking-closed|new-note' in final
result={'case':'two trailing NULL physical columns','new_owned_source_fixture':'ORKLAB.T_DML_NULL_20261005','source_business_steps_once':['INSERT','DELETE'],'captured_sequences':[start,start+1],'capture_archive_raw_differences':0,'raw_leaf_rc':leaf.returncode,'complete_batch_rc':parser_rc,'complete_batch_events':parser_events,'full_dml_accepted':False,'native_semantic_audit_pending':True,'public_decoder_enabled':False}
(O/'proof.json').write_text(json.dumps(result,indent=2));print(json.dumps(result))
