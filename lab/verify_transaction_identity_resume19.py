"""Separate JVM halt/resume against existing frozen source journals; no source actions."""
import hashlib,json,os,shutil,subprocess
from pathlib import Path
R=Path(__file__).resolve().parent.parent;O=R/'lab/out/cross-window-active-20261007';N=R/'lab/out/cross-window-identities-20261007'
def sha(path):
    with path.open('rb') as f:return hashlib.file_digest(f,'sha256').hexdigest()
def run(name,args,expected=0):
    args=list(map(str,args));assert not (N/(name+'.command.json')).exists()
    (N/(name+'.command.json')).write_text(json.dumps(args))
    env=dict(os.environ,JAVA_HOME='D:/work/Java/jdk-17.0.7');env['PATH']=env['JAVA_HOME']+'/bin;'+env['PATH']
    p=subprocess.run(args,capture_output=True,timeout=240,env=env,cwd=R)
    for suffix,value in [('stdout.txt',p.stdout),('stderr.txt',p.stderr),('rc.txt',str(p.returncode).encode())]:(N/(name+'.'+suffix)).write_bytes(value)
    assert p.returncode==expected,(name,p.returncode)
    return p
b=json.loads((O/'baseline-reference.json').read_text());prefix=['D:/work/Java/jdk-17.0.7/bin/java.exe','-cp',str(N/'classes')+';'+str(N/'kernel.jar'),'com.streamhouse.oracle.TransactionIdentityResumeProbe']
state=N/'pending-identities.state'
if not (N/'separate-jvm-resume.rc.txt').exists():
    run('full-build',['D:/work/Java/apache-maven-3.9.11/bin/mvn.cmd','-B','package'])
    shutil.copyfile(R/'target/oracle-redo-kernel-0.1.0-SNAPSHOT.jar',N/'kernel.jar');(N/'classes').mkdir()
    run('compile-probe',['D:/work/Java/jdk-17.0.7/bin/javac.exe','-cp',N/'kernel.jar','-d',N/'classes',R/'lab/TransactionIdentityResumeProbe.java'])
    run('prefix-halt',prefix+['prefix',state,b['path'],b['sha256'],O/'capture/sequence-73.oracap',sha(O/'capture/sequence-73.oracap')],23)
    resume=list(map(str,prefix+['resume',state,b['path'],b['sha256'],O/'capture/sequence-74.oracap',sha(O/'capture/sequence-74.oracap'),sha(state)]))
    run('separate-jvm-resume',resume)
resume=json.loads((N/'separate-jvm-resume.command.json').read_text());trusted=resume[-1]
assert int((N/'prefix-halt.rc.txt').read_text())==23 and int((N/'separate-jvm-resume.rc.txt').read_text())==0
q=(N/'prefix-halt.stdout.txt').read_text().strip().split('|');assert q==['DURABLE_IDENTITY_CHECKPOINT',trusted,'0']
out=(N/'separate-jvm-resume.stdout.txt').read_text();assert 'Xid[usn=10, slot=14, sequence=2869]' in out and 'beginRecordScn=2743312, endRecordScn=2743344, rolledBack=false' in out
assert sha(state)==trusted
bad=N/'digest-poison.state';data=bytearray(state.read_bytes());data[-2]^=1;bad.write_bytes(data)
args=list(resume);args[args.index(str(state))]=str(bad)
p=run('reject-poison',args,1);assert not p.stdout and b'TRANSACTION_CHECKPOINT_DIGEST_MISMATCH' in p.stderr
cut=N/'truncated.state';cut.write_bytes(state.read_bytes()[:-1]);args=list(resume);args[args.index(str(state))]=str(cut);args[-1]=sha(cut)
p=run('reject-truncated-valid-digest',args,1);assert not p.stdout and b'TRANSACTION_CHECKPOINT_NONCANONICAL' in p.stderr
for path in ['src/main/java/com/streamhouse/oracle/TransactionLifecycle.java','src/test/java/com/streamhouse/oracle/TransactionLifecycleCheckpointTest.java','lab/TransactionIdentityResumeProbe.java','lab/verify_transaction_identity_resume19.py']:shutil.copyfile(R/path,N/Path(path).name)
(N/'proof.json').write_text(json.dumps(dict(kernel_sha256=sha(N/'kernel.jar'),pending_sha256=trusted,prefix_process_rc=23,separate_jvm_resume_rc=0,xid='10.14.2869',begin_record_scn=2743312,end_record_scn=2743344,negative_cases=2,source_actions=0,target_actions=0,events=0,identity_state_only=True,full_cross_publication_recovery='NOT_VERIFIED'),indent=2))
print('SEPARATE_JVM_IDENTITY_CHECKPOINT_RESUME_VERIFIED')
