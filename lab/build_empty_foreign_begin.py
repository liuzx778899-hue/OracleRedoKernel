"""Build the new foreign-begin guard and bind its private adapter dependency; retain old artifacts."""
import hashlib,json,os,shutil,subprocess,xml.etree.ElementTree as ET
from pathlib import Path
R=Path(__file__).resolve().parent.parent;O=R/'lab/out/release-empty-foreign-begin-r2-20261005';M=R/'integration/streamhouse-engine-oracle';O.mkdir(parents=True,exist_ok=False)
shutil.copytree(R/'lab/out/release-oracle-dml-20261005/m2',O/'m2')
env=dict(os.environ);env['JAVA_HOME']='D:/work/Java/jdk-17.0.7';env['PATH']=env['JAVA_HOME']+'/bin;'+env['PATH']
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
def run(n,cwd,goals):
    args=['cmd','/d','/c','D:/work/Java/apache-maven-3.9.11/bin/mvn.cmd','--batch-mode','-Dmaven.repo.local='+str(O/'m2')]+goals
    (O/(n+'.command.json')).write_text(json.dumps({'args':args,'cwd':str(cwd)}))
    with (O/(n+'.stdout.txt')).open('wb') as out,(O/(n+'.stderr.txt')).open('wb') as err:p=subprocess.run(args,cwd=cwd,env=env,stdout=out,stderr=err,timeout=300)
    (O/(n+'.rc.txt')).write_text(str(p.returncode));assert p.returncode==0,n
def reports(cwd,n):
    shutil.copytree(cwd/'target/surefire-reports',O/n)
    return {k:sum(int(ET.parse(p).getroot().get(k,0)) for p in (O/n).glob('TEST-*.xml')) for k in ['tests','failures','errors','skipped']}
run('kernel',R,['clean','verify']);k=reports(R,'kernel-tests');assert k=={'tests':254,'failures':0,'errors':0,'skipped':0}
shutil.copyfile(R/'target/oracle-redo-kernel-0.1.0-SNAPSHOT.jar',O/'kernel.jar')
version='0.1.0-bounded-foreign-begin-r2'
run('private-install',M,['org.apache.maven.plugins:maven-install-plugin:3.1.2:install-file','-Dfile='+str(O/'kernel.jar'),'-DgroupId=com.streamhouse.oracle','-DartifactId=oracle-redo-kernel','-Dversion='+version,'-Dpackaging=jar','-DgeneratePom=true'])
run('adapter',M,['-Doracle.kernel.version='+version,'clean','verify']);a=reports(M,'adapter-tests');assert a=={'tests':15,'failures':0,'errors':0,'skipped':0}
shutil.copyfile(M/'target/streamhouse-engine-oracle-0.1.0-SNAPSHOT.jar',O/'adapter.jar');shutil.copyfile(R/'lab/out/release-oracle-dml-20261005/spi.jar',O/'spi.jar')
(O/'proof.json').write_text(json.dumps({'kernel_version':version,'kernel_tests':k,'adapter_tests':a,'kernel_sha256':sha(O/'kernel.jar'),'adapter_sha256':sha(O/'adapter.jar'),'spi_sha256':sha(O/'spi.jar'),'new_functionality':'exact independently bound foreign begin exclusion only','full_product_accepted':False},indent=2));print((O/'proof.json').read_text())
