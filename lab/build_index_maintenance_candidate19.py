"""Build the typed begin candidate and probe frozen maintenance lifecycle, no source actions."""
import hashlib,json,os,shutil,subprocess
from pathlib import Path
R=Path(__file__).resolve().parent.parent;N=R/'lab/out/index-maintenance-candidate-20261006';H=R/'lab/out/v3-product-history-20261006'
assert N.exists() and not (N/'kernel-build.command.json').exists()
(N/'executed-candidate-driver.py').write_bytes(Path(__file__).read_bytes())
env=dict(os.environ,JAVA_HOME='D:/work/Java/jdk-17.0.7');env['PATH']=env['JAVA_HOME']+'/bin;'+env['PATH']
def run(name,args):
    args=list(map(str,args));assert not (N/(name+'.command.json')).exists();(N/(name+'.command.json')).write_text(json.dumps(args))
    p=subprocess.run(args,env=env,capture_output=True,timeout=900)
    for ext,value in [('stdout.txt',p.stdout),('stderr.txt',p.stderr),('rc.txt',str(p.returncode).encode())]:(N/(name+'.'+ext)).write_bytes(value)
    print(name,p.returncode,flush=True);return p.returncode
assert run('kernel-build',['D:/work/Java/apache-maven-3.9.11/bin/mvn.cmd','clean','package'])==0
shutil.copytree(R/'target/surefire-reports',N/'kernel-reports')
shutil.copyfile(R/'target/oracle-redo-kernel-0.1.0-SNAPSHOT.jar',N/'kernel.jar')
classes=N/'classes';classes.mkdir();cp=';'.join(map(str,[N/'kernel.jar',H/'adapter.jar',H/'spi.jar',classes,*list((H/'jackson').glob('*.jar'))]));j=env['JAVA_HOME']+'/bin/'
assert run('compile-lifecycle',[j+'javac.exe','-cp',cp,'-d',classes,R/'lab/SysUndoHistoryProbe.java',R/'lab/AcquiredHistoricalProjectionProbe.java'])==0
assert run('maintenance-lifecycle',[j+'java.exe','-cp',cp,'SysUndoHistoryProbe',H/'archive-70.dbf','9.9.660'])==0
ref=json.loads((H/'capture-reference.json').read_text());args=[j+'java.exe','-Xmx1536m','-cp',cp,'AcquiredHistoricalProjectionProbe',H/'actual-product-v3.snapshot',ref['baseline']['sha256'],'50342539']
for seq in ref['sequences']:
    path=H/'capture'/('sequence-'+str(seq)+'.oracap');args.extend([path,hashlib.sha256(path.read_bytes()).hexdigest()])
assert run('full-history-refusal',args)!=0
assert (N/'full-history-refusal.stdout.txt').stat().st_size==0
assert 'DICTIONARY_DISCOVERY_INDEX_COVERAGE' in (N/'full-history-refusal.stderr.txt').read_text()
print('BEGIN_LIFECYCLE_CANDIDATE_ONLY|events=0',flush=True)
