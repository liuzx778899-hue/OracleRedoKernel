"""One owned empty-baseline fixture and one new INSERT/ROLLBACK, never replay source actions."""
import hashlib,json,subprocess,sys,time
from pathlib import Path
R=Path(__file__).resolve().parent.parent;O=R/'lab/out/empty-dml-capture-20261005';SOURCE='oraredo-19c-lab-20261004';B=R/'lab/out/release-oracle-dml-20261005'
assert sys.argv[1:]==['--execute'];O.mkdir(parents=True,exist_ok=False)
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
(O/'executed-driver.py').write_bytes(Path(__file__).read_bytes());(O/'driver-sha256.txt').write_text(sha(Path(__file__)))
assert sha(B/'kernel.jar')=='a084218e5ebc9c4e3313cd84beba0c2bea4ff280a82f93fcf5890d2a0493f968'
def run(n,args,data=None,rc=0):
    assert not (O/(n+'.rc.txt')).exists();args=list(map(str,args));(O/(n+'.command.json')).write_text(json.dumps(args))
    p=subprocess.run(args,input=data,capture_output=True,timeout=240)
    for ext,v in [('stdout',p.stdout),('stderr',p.stderr)]:(O/(n+'.'+ext+'.txt')).write_bytes(v)
    (O/(n+'.rc.txt')).write_text(str(p.returncode))
    if rc is not None:assert p.returncode==rc,(n,p.stderr)
    return p
def sql(n,body):
    text='whenever sqlerror exit failure rollback\nwhenever oserror exit failure rollback\nset pages 0 lines 32767 feedback off heading off\n'+body+'\nexit success\n';(O/(n+'.sql')).write_text(text)
    return run(n,['docker','exec','-i',SOURCE,'sqlplus','-s','/','as','sysdba'],text.encode()).stdout.decode()
assert run('owner',['docker','inspect',SOURCE,'--format','{{.Config.Labels.owner}}|{{.Config.Image}}|{{.State.Status}}']).stdout.decode().strip()=='OracleRedoKernel|container-registry.oracle.com/database/enterprise:19.3.0.0|running'
check=sql('preflight',"declare n number;begin select count(*) into n from v$database where dbid=1276726821 and activation#=1276717861 and log_mode='ARCHIVELOG' and supplemental_log_data_min='NO' and supplemental_log_data_all='NO';if n!=1 then raise_application_error(-20001,'source mismatch');end if;end;\n/\nalter session set container=ORKPDB;\nselect 'OBJECT_COUNT|'||count(*) from dba_objects where owner='ORKLAB' and object_name in ('T_DML_EMPTY_20261005','PK_DML_EMPTY_20261005');\nselect 'OLD|'||ID||'|'||TXT||'|'||NOTE from ORKLAB.T_BASELINE_20261004;")
assert 'OBJECT_COUNT|0' in check and 'OLD|11|blocking-closed|new-note' in check
# Allocate heap/index once before the trusted baseline, then leave no business rows.
sql('fixture-once',"alter session set container=ORKPDB;\ncreate table ORKLAB.T_DML_EMPTY_20261005(ID number not null,TXT varchar2(128),NOTE varchar2(128) invisible,constraint PK_DML_EMPTY_20261005 primary key(ID));\ninsert into ORKLAB.T_DML_EMPTY_20261005(ID,TXT,NOTE) values(0,'allocation-only','anchor');\ndelete from ORKLAB.T_DML_EMPTY_20261005 where ID=0;\ncommit;")
acquirer=O/'executed-acquire-baseline.py';acquirer.write_text((R/'lab/capture_heap_baseline19.py').read_text().replace('T_BASELINE_20261004','T_DML_EMPTY_20261005').replace('PK_BASELINE_20261004','PK_DML_EMPTY_20261005'))
run('acquire-baseline',[sys.executable,acquirer,'--execute',O/'baseline'])
snapshot=O/'baseline/snapshot.stdout.txt';digest=json.loads((O/'baseline/receipt.json').read_text())['snapshot_sha256'];assert sha(snapshot)==digest and 'ROW_COUNT|0' in snapshot.read_text()
info=sql('members',"select 'MEMBER|'||member from v$logfile order by group#;\nselect 'CURRENT|'||sequence# from v$log where status='CURRENT';")
members=[s.strip().split('|')[1] for s in info.splitlines() if s.strip().startswith('MEMBER|')];seq=int(next(s.strip().split('|')[1] for s in info.splitlines() if s.strip().startswith('CURRENT|')));assert len(members)==3
for name in ['resume_online_observation.py','follow_closed_members.py']:
    frozen=O/('executed-'+name);frozen.write_bytes((R/'lab'/name).read_bytes());run('copy-'+name,['docker','cp',frozen,SOURCE+':/tmp/'+name]);assert sha(frozen) in run('sha-'+name,['docker','exec',SOURCE,'sha256sum','/tmp/'+name]).stdout.decode()
remote='/tmp/ork-empty-dml-capture-20261005';collector=['docker','exec',SOURCE,'python3','/tmp/follow_closed_members.py',remote,seq,240,.25]+members
(O/'collector.command.json').write_text(json.dumps(collector))
with (O/'collector.stdout.txt').open('wb') as out,(O/'collector.stderr.txt').open('wb') as err:
    process=subprocess.Popen(list(map(str,collector)),stdout=out,stderr=err);(O/'collector-host-pid.txt').write_text(str(process.pid))
    def wait(key,value):
        deadline=time.time()+60
        while time.time()<deadline:
            if any(json.loads(line).get(key)==value for line in (O/'collector.stdout.txt').read_text().splitlines()):return
            if process.poll() is not None:raise RuntimeError('collector terminal; preserve source actions, do not replay')
            time.sleep(.1)
        raise RuntimeError('live collector observation timeout; inspect same process handle')
    wait('ready',True)
    truth=sql('insert-rollback-once',"declare n number;begin select sequence# into n from v$log where status='CURRENT';if n!="+str(seq)+" then raise_application_error(-20002,'sequence drift');end if;end;\n/\nalter session set container=ORKPDB;\ninsert into ORKLAB.T_DML_EMPTY_20261005(ID,TXT,NOTE) values(11,'empty-undo','empty-note');\nselect 'STEP|'||ID||'|'||TXT||'|'||NOTE from ORKLAB.T_DML_EMPTY_20261005;\nselect 'XID|'||xidusn||'.'||xidslot||'.'||xidsqn from v$transaction where ses_addr=(select saddr from v$session where sid=sys_context('USERENV','SID'));\nrollback;\nselect 'TRUTH_COUNT|'||count(*) from ORKLAB.T_DML_EMPTY_20261005;\nalter session set container=CDB$ROOT;\nalter system archive log current;\nselect 'ARCHIVE|'||name from v$archived_log where thread#=1 and sequence#="+str(seq)+" and name is not null order by recid;")
    assert 'STEP|11|empty-undo|empty-note' in truth and 'TRUTH_COUNT|0' in truth
    wait('captured_sequence',seq);rc=process.wait(timeout=90);(O/'collector.rc.txt').write_text(str(rc));assert rc==0
path=next(s.strip().split('|')[1] for s in truth.splitlines() if s.strip().startswith('ARCHIVE|'))
run('copy-archive',['docker','cp',SOURCE+':'+path,O/'archive.dbf']);run('copy-capture',['docker','cp',SOURCE+':'+remote,O/'capture'])
capture=O/'capture'/('sequence-'+str(seq)+'.oracap');run('capture-archive',[sys.executable,R/'lab/compare_observation_archive.py',capture,O/'archive.dbf'])
trace=sql('independent-dump',"alter session set tracefile_identifier='ORK_EMPTY_DML';\nalter system dump logfile '"+path+"';\nselect value from v$diag_info where name='Default Trace File';")
tracepath=next(s.strip() for s in trace.splitlines() if s.strip().startswith('/opt/oracle/') and s.strip().endswith('.trc'));run('copy-dump',['docker','cp',SOURCE+':'+tracepath,O/'dump.trc'])
cp=str(B/'kernel.jar')+';'+str(R/'lab/out/mixed-dml-capture-20261005/classes')
leaf=run('raw-leaf',['D:/work/Java/jdk-17.0.7/bin/java.exe','-cp',cp,'RawDmlLeafProbe',snapshot,digest,capture,sha(capture)]).stdout.decode().strip().split('|')[1]
cp=str(B/'kernel.jar')+';'+str(R/'lab/out/captured-batch-20261005/classes')
batch=run('complete-batch',['D:/work/Java/jdk-17.0.7/bin/java.exe','-cp',cp,'CapturedBatchProbe',snapshot,digest,leaf,capture,sha(capture)],rc=None)
final=sql('final-source',"alter session set container=ORKPDB;\nselect 'FINAL_COUNT|'||count(*) from ORKLAB.T_DML_EMPTY_20261005;\nselect 'OLD|'||ID||'|'||TXT||'|'||NOTE from ORKLAB.T_BASELINE_20261004;")
assert 'FINAL_COUNT|0' in final and 'OLD|11|blocking-closed|new-note' in final
(O/'proof.json').write_text(json.dumps({'sequence':seq,'baseline_sha256':digest,'leaf':leaf,'capture_sha256':sha(capture),'batch_rc':batch.returncode,'events':sum(s.startswith(b'EVENT|') for s in batch.stdout.splitlines()),'source_rollback_once':True,'capture_archive_differences':0,'native_audit_pending':True,'target_checkpoint_pending':True},indent=2));print((O/'proof.json').read_text())
