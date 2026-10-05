"""Use the accepted nullable kernel under a private immutable version; no kernel rebuild."""
import hashlib,json,os,shutil,subprocess,xml.etree.ElementTree as ET
from pathlib import Path
R=Path(__file__).resolve().parent.parent;O=R/'lab/out/release-oracle-dml-20261005';O.mkdir(parents=True,exist_ok=False)
W=R/'lab/out/release-oracle-window-20261005';M=R/'integration/streamhouse-engine-oracle'
shutil.copytree(W/'m2',O/'m2')
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
k=R/'lab/out/nullable-tail-20261005/kernel.jar'
assert sha(k)=='a084218e5ebc9c4e3313cd84beba0c2bea4ff280a82f93fcf5890d2a0493f968'
shutil.copyfile(k,O/'kernel.jar');shutil.copyfile(W/'spi.jar',O/'spi.jar')
env=dict(os.environ);env['JAVA_HOME']='D:/work/Java/jdk-17.0.7';env['PATH']=env['JAVA_HOME']+'/bin;'+env['PATH']
def run(name,args):
    cmd=['cmd','/d','/c','D:/work/Java/apache-maven-3.9.11/bin/mvn.cmd','--batch-mode','-Dmaven.repo.local='+str(O/'m2')]+args
    (O/(name+'.command.json')).write_text(json.dumps({'args':cmd,'cwd':str(M)}))
    with (O/(name+'.stdout.txt')).open('wb') as out,(O/(name+'.stderr.txt')).open('wb') as err:p=subprocess.run(cmd,cwd=M,env=env,stdout=out,stderr=err,timeout=300)
    (O/(name+'.rc.txt')).write_text(str(p.returncode));assert p.returncode==0,name
version='0.1.0-bounded-4f79691'
run('private-install',['org.apache.maven.plugins:maven-install-plugin:3.1.2:install-file','-Dfile='+str(O/'kernel.jar'),'-DgroupId=com.streamhouse.oracle','-DartifactId=oracle-redo-kernel','-Dversion='+version,'-Dpackaging=jar','-DgeneratePom=true'])
run('adapter',['-Doracle.kernel.version='+version,'clean','verify'])
shutil.copytree(M/'target/surefire-reports',O/'tests')
counts={k:sum(int(ET.parse(p).getroot().get(k,0)) for p in (O/'tests').glob('TEST-*.xml')) for k in ['tests','errors','failures','skipped']}
assert counts=={'tests':15,'errors':0,'failures':0,'skipped':0}
shutil.copyfile(M/'target/streamhouse-engine-oracle-0.1.0-SNAPSHOT.jar',O/'adapter.jar')
proof={'kernel_version':version,'kernel_sha256':sha(O/'kernel.jar'),'spi_sha256':sha(O/'spi.jar'),'adapter_sha256':sha(O/'adapter.jar'),'adapter_tests':counts,'kernel_rebuilt':False,'kernel_tests_rerun':False,'old_private_repository_unchanged':True}
(O/'proof.json').write_text(json.dumps(proof,indent=2));print(json.dumps(proof))
