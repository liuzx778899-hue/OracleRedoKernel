"""Build and exercise the actual frozen inputs; preserve every rc and failure."""
import hashlib,json,os,shutil,subprocess
from pathlib import Path
R=Path(__file__).resolve().parent.parent;O=R/'lab/out/cross-window-active-20261007';N=R/'lab/out/cross-window-range-20261007'
assert not (N/'candidate-kernel.jar').exists()
def run(name,args,required=True):
    args=list(map(str,args));assert not (N/(name+'.command.json')).exists()
    (N/(name+'.command.json')).write_text(json.dumps(args))
    env=dict(os.environ,JAVA_HOME='D:/work/Java/jdk-17.0.7');env['PATH']=env['JAVA_HOME']+'/bin;'+env['PATH']
    p=subprocess.run(args,capture_output=True,env=env,timeout=240,cwd=R)
    for suffix,value in [('stdout.txt',p.stdout),('stderr.txt',p.stderr),('rc.txt',str(p.returncode).encode())]:(N/(name+'.'+suffix)).write_bytes(value)
    if required:assert p.returncode==0,(name,p.returncode)
    return p
run('full-build',['D:/work/Java/apache-maven-3.9.11/bin/mvn.cmd','-B','package'])
shutil.copyfile(R/'target/oracle-redo-kernel-0.1.0-SNAPSHOT.jar',N/'candidate-kernel.jar')
for path in ['src/main/java/com/streamhouse/oracle/HistoricalSchemaBarrier.java','src/main/java/com/streamhouse/oracle/ArchivedDictionaryHistory.java','src/test/java/com/streamhouse/oracle/HistoricalSchemaBarrierTest.java']:
    shutil.copyfile(R/path,N/Path(path).name)
(N/'event-classes').mkdir();run('compile-events',['D:/work/Java/jdk-17.0.7/bin/javac.exe','-cp',N/'candidate-kernel.jar','-d',N/'event-classes',R/'lab/CapturedHistoricalBatchProbe.java'])
b=json.loads((O/'baseline-reference.json').read_text())
base=['D:/work/Java/jdk-17.0.7/bin/java.exe','-cp',str(N/'event-classes')+';'+str(N/'candidate-kernel.jar'),'CapturedHistoricalBatchProbe',b['path'],b['sha256'],'50342539'];inputs=[]
for seq in [73,74]:
    path=O/'capture'/('sequence-'+str(seq)+'.oracap')
    with path.open('rb') as f:digest=hashlib.file_digest(f,'sha256').hexdigest()
    inputs += [str(path),digest]
    run('candidate-'+('pending' if seq==73 else 'closed'),base+inputs,False)
(N/'executed-verifier.py').write_bytes(Path(__file__).read_bytes())
print('CANDIDATE_COMPLETE_INPUT_RUNS_RETAINED')
