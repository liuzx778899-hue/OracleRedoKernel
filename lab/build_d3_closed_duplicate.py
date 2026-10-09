"""Change-driven rebuild after the real closed-header duplicate; no source actions."""
from pathlib import Path
import os,json,hashlib,shutil,subprocess,zipfile
R=Path(__file__).resolve().parent.parent;N=R/'lab/out/d3-online-product-20261010';P=R/'lab/out/streamhouse-oracle-gate-worktree'
assert (N/'kernel-r3.rc').read_text().strip()=='0';old=json.loads((N/'candidate.json').read_text())
env=dict(os.environ,JAVA_HOME='D:/work/Java/jdk-17.0.7');env['PATH']=env['JAVA_HOME']+'/bin;'+env['PATH']
mvn=['cmd','/d','/c','D:/work/Java/apache-maven-3.9.11/bin/mvn.cmd','-o','-B']
def run(name,args,cwd):
 assert not (N/(name+'.rc')).exists();p=subprocess.run(list(map(str,args)),cwd=cwd,env=env,capture_output=True,timeout=900)
 (N/(name+'.log')).write_bytes(p.stdout+p.stderr);(N/(name+'.rc')).write_text(str(p.returncode));assert p.returncode==0,(name,p.returncode)
kernel=R/'target/oracle-redo-kernel-0.1.0-SNAPSHOT.jar';sha=hashlib.sha256(kernel.read_bytes()).hexdigest();version='0.1.0-online-'+sha[:12];shutil.copyfile(kernel,N/'kernel-final.jar')
run('install-kernel-final',mvn+['org.apache.maven.plugins:maven-install-plugin:3.1.2:install-file','-Dfile='+str(kernel),'-DgroupId=com.streamhouse.oracle','-DartifactId=oracle-redo-kernel','-Dversion='+version,'-Dpackaging=jar','-DgeneratePom=true'],R)
pom=P/'server/streamhouse-app/pom.xml';text=pom.read_text();assert text.count(old['kernelVersion'])==1;pom.write_text(text.replace(old['kernelVersion'],version))
run('product-tests-final',mvn+['-pl','streamhouse-app','-am','-DfailIfNoTests=false','-Dsurefire.failIfNoSpecifiedTests=false','-Dtest=OracleRawPlanServiceTest','test'],P/'server')
run('product-package-final',mvn+['-pl','streamhouse-app','-am','-DskipTests','package'],P/'server')
app=P/'server/streamhouse-app/target/streamhouse-app-0.1.0-SNAPSHOT.jar';shutil.copyfile(app,N/'application-final.jar');libs=N/'libs-final';libs.mkdir()
with zipfile.ZipFile(app) as jar:
 for name in jar.namelist():
  if name.startswith('BOOT-INF/lib/') and name.endswith('.jar'):(libs/Path(name).name).write_bytes(jar.read(name))
assert hashlib.sha256((libs/('oracle-redo-kernel-'+version+'.jar')).read_bytes()).hexdigest()==sha
(N/'candidate-final.json').write_text(json.dumps(dict(kernelSha256=sha,kernelVersion=version,applicationSha256=hashlib.sha256(app.read_bytes()).hexdigest()),indent=2))
