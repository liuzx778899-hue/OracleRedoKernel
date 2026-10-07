"""Actual prefix inventory and extended inventory in separate JVMs; no source actions."""
import hashlib,json,os,shutil,subprocess
from pathlib import Path
R=Path(__file__).resolve().parent.parent;N=R/'lab/out/cross-window-undo-20261007';O=R/'lab/out/cross-window-active-20261007'
def sha(path):
    with path.open('rb') as f:return hashlib.file_digest(f,'sha256').hexdigest()
def run(name,args,expected=0):
    args=list(map(str,args));assert not (N/(name+'.command.json')).exists();(N/(name+'.command.json')).write_text(json.dumps(args))
    env=dict(os.environ,JAVA_HOME='D:/work/Java/jdk-17.0.7');env['PATH']=env['JAVA_HOME']+'/bin;'+env['PATH']
    p=subprocess.run(args,capture_output=True,timeout=240,env=env,cwd=R)
    for suffix,value in [('stdout.txt',p.stdout),('stderr.txt',p.stderr),('rc.txt',str(p.returncode).encode())]:(N/(name+'.'+suffix)).write_bytes(value)
    assert p.returncode==expected,(name,p.returncode)
    return p
run('full-build',['D:/work/Java/apache-maven-3.9.11/bin/mvn.cmd','-B','package'])
shutil.copyfile(R/'target/oracle-redo-kernel-0.1.0-SNAPSHOT.jar',N/'kernel.jar');(N/'classes').mkdir()
run('compile-probe',['D:/work/Java/jdk-17.0.7/bin/javac.exe','-cp',N/'kernel.jar','-d',N/'classes',R/'lab/UndoGraphResumeProbe.java'])
b=json.loads((O/'baseline-reference.json').read_text());base=['D:/work/Java/jdk-17.0.7/bin/java.exe','-cp',str(N/'classes')+';'+str(N/'kernel.jar'),'com.streamhouse.oracle.UndoGraphResumeProbe']
root=N/'resume';root.mkdir()
first=[b['path'],b['sha256'],str(O/'capture/sequence-73.oracap'),sha(O/'capture/sequence-73.oracap')]
p=run('pending-halt',base+['prefix',str(root),*first],23);q=p.stdout.decode().strip().split('|')
assert q==['PENDING_GRAPH_CHECKPOINT',sha(root/'identity.state'),sha(root/'graph.state')]
resume=base+['resume',str(root),*first,str(O/'capture/sequence-74.oracap'),sha(O/'capture/sequence-74.oracap'),q[1],q[2]]
p=run('separate-jvm-resume',resume);text=p.stdout.decode()
assert 'beginRecordScn=2743312, endRecordScn=2743344, rolledBack=false' in text and text.count('ORIGINAL|')==2
assert 'archiveSequence=73, block=219164, offset=16, vector=3' in text and 'archiveSequence=74, block=19, offset=16, vector=1' in text
assert 'record=11' in text and 'rci=10' in text
bad=N/'poison';bad.mkdir();shutil.copyfile(root/'identity.state',bad/'identity.state');raw=bytearray((root/'graph.state').read_bytes());raw[-2]^=1;(bad/'graph.state').write_bytes(raw)
args=list(resume);args[args.index(str(root))]=str(bad);p=run('reject-graph-poison',args,1);assert not p.stdout and b'UNDO_CHECKPOINT_DIGEST_MISMATCH' in p.stderr
for name in ['src/main/java/com/streamhouse/oracle/UndoHistory.java','src/test/java/com/streamhouse/oracle/UndoHistoryCheckpointTest.java','lab/UndoGraphResumeProbe.java','lab/verify_undo_graph_resume19.py']:shutil.copyfile(R/name,N/Path(name).name)
(N/'proof.json').write_text(json.dumps(dict(kernel_sha256=sha(N/'kernel.jar'),identity_sha256=q[1],graph_sha256=q[2],halt_rc=23,separate_jvm_resume_rc=0,original=2,live=2,compensated=0,allocation_tail_record=11,active_head_record=11,second_original_rci=10,graph_poison_rejected=True,source_actions=0,target_actions=0,events=0,full_dictionary_publication_recovery='NOT_VERIFIED'),indent=2));print('ACTUAL_UNDO_PREFIX_CHECKPOINT_AND_EXTENDED_INVENTORY_RESUME_VERIFIED')
