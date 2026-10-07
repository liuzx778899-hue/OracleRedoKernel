"""Frozen prefix passes the same physical gates while retaining explicitly pending state."""
import hashlib,json,os,shutil,subprocess
from pathlib import Path
R=Path(__file__).resolve().parent.parent;N=R/'lab/out/physical-prefix-20261007';O=R/'lab/out/cross-window-active-20261007'
assert N.exists() and (N/'full-build.rc.txt').read_text()=='1' and not (N/'kernel.jar').exists()
def sha(p):
    with p.open('rb') as f:return hashlib.file_digest(f,'sha256').hexdigest()
def run(name,args,expected=0):
    args=list(map(str,args));assert not (N/(name+'.command.json')).exists();(N/(name+'.command.json')).write_text(json.dumps(args))
    env=dict(os.environ,JAVA_HOME='D:/work/Java/jdk-17.0.7');env['PATH']=env['JAVA_HOME']+'/bin;'+env['PATH']
    p=subprocess.run(args,capture_output=True,timeout=300,cwd=R,env=env)
    for suffix,value in [('stdout.txt',p.stdout),('stderr.txt',p.stderr),('rc.txt',str(p.returncode).encode())]:(N/(name+'.'+suffix)).write_bytes(value)
    assert p.returncode==expected,(name,p.returncode);return p
run('r2-full-build',['D:/work/Java/apache-maven-3.9.11/bin/mvn.cmd','-B','package'])
shutil.copyfile(R/'target/oracle-redo-kernel-0.1.0-SNAPSHOT.jar',N/'kernel.jar');(N/'classes').mkdir()
j='D:/work/Java/jdk-17.0.7/bin/';run('compile',[j+'javac.exe','-cp',N/'kernel.jar','-d',N/'classes',R/'lab/PhysicalPrefixProbe.java',R/'lab/CapturedHistoricalBatchProbe.java'])
b=json.loads((O/'baseline-reference.json').read_text());first=O/'capture/sequence-73.oracap';second=O/'capture/sequence-74.oracap'
base=[j+'java.exe','-Xmx1536m','-cp',str(N/'classes')+';'+str(N/'kernel.jar')];inputs=[b['path'],b['sha256'],'50342539',first,sha(first)]
p=run('actual-73-prefix',base+['com.streamhouse.oracle.PhysicalPrefixProbe',*inputs]);assert b'PREFIX|0|1' in p.stdout and b'PENDING|Xid[usn=10, slot=14, sequence=2869]|' in p.stdout and b'|1|1|0|0' in p.stdout
assert b'archiveSequence=73, block=219164, offset=16, vector=3' in p.stdout
p=run('default-73-still-rejects',base+['CapturedHistoricalBatchProbe',*inputs],1);assert not p.stdout and b'INCOMPLETE_TRANSACTION_IDENTITIES:1' in p.stderr
p=run('actual-73-74-closed',base+['com.streamhouse.oracle.PhysicalPrefixProbe',*inputs,second,sha(second)]);assert b'PREFIX|1|0' in p.stdout and b'beginRecordScn=2743312, endRecordScn=2743344, rolledBack=false' in p.stdout
p=run('complete-events-regression',base+['CapturedHistoricalBatchProbe',*inputs,second,sha(second)]);assert p.stdout==(R/'lab/out/cross-window-range-20261007/candidate-closed.stdout.txt').read_bytes()
(N/'proof.json').write_text(json.dumps(dict(kernel_sha256=sha(N/'kernel.jar'),prefix_transactions=0,pending=1,pending_originals=1,pending_heap_pairs=1,pending_index_pairs=0,default_prefix_refused=True,closed_transactions=1,complete_events=2,complete_stdout_byte_equal=True,source_actions=0,target_actions=0,product_actions=0,unified_resume_verified=False),indent=2))
print('PHYSICAL_PENDING_PREFIX_AND_DEFAULT_REFUSAL_VERIFIED')
