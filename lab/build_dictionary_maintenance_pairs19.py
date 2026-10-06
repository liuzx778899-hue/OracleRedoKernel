"""Build and verify three raw maintenance pairs; never claim complete split support."""
import hashlib,json,os,shutil,subprocess,zipfile
from pathlib import Path
R=Path(__file__).resolve().parent.parent;N=R/'lab/out/index-maintenance-pairs-20261006';H=R/'lab/out/v3-product-history-20261006'
assert N.exists() and not (N/'kernel-build.command.json').exists()
(N/'executed-pair-candidate-driver.py').write_bytes(Path(__file__).read_bytes())
env=dict(os.environ,JAVA_HOME='D:/work/Java/jdk-17.0.7');env['PATH']=env['JAVA_HOME']+'/bin;'+env['PATH']
def run(name,args):
    args=list(map(str,args));assert not (N/(name+'.command.json')).exists();(N/(name+'.command.json')).write_text(json.dumps(args));p=subprocess.run(args,env=env,capture_output=True,timeout=900)
    for ext,value in [('stdout.txt',p.stdout),('stderr.txt',p.stderr),('rc.txt',str(p.returncode).encode())]:(N/(name+'.'+ext)).write_bytes(value)
    assert p.returncode==0,(name,p.returncode);print(name,p.returncode,flush=True)
run('kernel-build',['D:/work/Java/apache-maven-3.9.11/bin/mvn.cmd','clean','package'])
shutil.copyfile(R/'target/oracle-redo-kernel-0.1.0-SNAPSHOT.jar',N/'kernel.jar');shutil.copytree(R/'target/surefire-reports',N/'kernel-reports')
old=R/'lab/out/index-maintenance-candidate-20261006/kernel.jar'
with zipfile.ZipFile(old) as a,zipfile.ZipFile(N/'kernel.jar') as b:
    before={n:a.read(n) for n in a.namelist() if n.endswith('.class')};after={n:b.read(n) for n in b.namelist() if n.endswith('.class')}
    assert all(after[n]==value for n,value in before.items())
    added=sorted(set(after)-set(before));assert len(added)==2 and all('DictionaryIndexMaintenancePair' in n for n in added)
    (N/'class-compatibility.json').write_text(json.dumps(dict(previous_classes=len(before),unchanged=True,added=added)))
classes=N/'classes';classes.mkdir();cp=str(N/'kernel.jar')+';'+str(classes);j=env['JAVA_HOME']+'/bin/'
run('compile-real-pairs',[j+'javac.exe','-cp',cp,'-d',classes,R/'lab/DictionaryMaintenancePairProbe.java'])
run('real-pairs',[j+'java.exe','-cp',cp,'DictionaryMaintenancePairProbe',H/'archive-70.dbf',H/'actual-product-v3.snapshot','323fd35a8ceee43545c28a9fb32f55cedd41409a3e1b5fb63e08606380cd24a2'])
print('TYPED_PAIR_CANDIDATE_ONLY|events=0',flush=True)
