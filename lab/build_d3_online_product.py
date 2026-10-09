"""Build only the changed kernel and product; immutable dependency pin, offline tools."""
from pathlib import Path
import os,json,hashlib,shutil,subprocess,zipfile,sys
R=Path(__file__).resolve().parent.parent;N=R/'lab/out/d3-online-product-20261010';P=R/'lab/out/streamhouse-oracle-gate-worktree'
assert (N/'kernel-r2.rc').read_text().strip()=='0'
env=dict(os.environ,JAVA_HOME='D:/work/Java/jdk-17.0.7');env['PATH']=env['JAVA_HOME']+'/bin;'+env['PATH']
mvn=['cmd','/d','/c','D:/work/Java/apache-maven-3.9.11/bin/mvn.cmd','-o','-B']
def run(name,args,cwd):
 assert not (N/(name+'.rc')).exists()
 p=subprocess.run(list(map(str,args)),cwd=cwd,env=env,capture_output=True,timeout=900)
 for suffix,data in [('log',p.stdout+p.stderr),('rc',str(p.returncode).encode())]:(N/(name+'.'+suffix)).write_bytes(data)
 assert p.returncode==0,(name,p.returncode)
kernel=R/'target/oracle-redo-kernel-0.1.0-SNAPSHOT.jar';sha=hashlib.sha256(kernel.read_bytes()).hexdigest();version='0.1.0-online-'+sha[:12]
if sys.argv[1:]!=['--resume-reactor']:
 shutil.copyfile(kernel,N/'kernel.jar')
 run('install-kernel',mvn+['org.apache.maven.plugins:maven-install-plugin:3.1.2:install-file','-Dfile='+str(kernel),'-DgroupId=com.streamhouse.oracle','-DartifactId=oracle-redo-kernel','-Dversion='+version,'-Dpackaging=jar','-DgeneratePom=true'],R)
 pom=P/'server/streamhouse-app/pom.xml';text=pom.read_text();old='0.1.0-lob-trust-ca21c74e6542';assert text.count(old)==1;pom.write_text(text.replace(old,version))
run('product-tests-reactor',mvn+['-pl','streamhouse-app','-am','-DfailIfNoTests=false','-Dsurefire.failIfNoSpecifiedTests=false','-Dtest=OracleRawPlanServiceTest,OracleRawDiscoveryServiceTest,OracleRawStreamEnrollmentTest','test'],P/'server')
run('product-package',mvn+['-pl','streamhouse-app','-am','-DskipTests','package'],P/'server')
app=P/'server/streamhouse-app/target/streamhouse-app-0.1.0-SNAPSHOT.jar';shutil.copyfile(app,N/'application.jar');libs=N/'libs';libs.mkdir()
with zipfile.ZipFile(app) as jar:
 for name in jar.namelist():
  if name.startswith('BOOT-INF/lib/') and name.endswith('.jar'):(libs/Path(name).name).write_bytes(jar.read(name))
 assert hashlib.sha256((libs/('oracle-redo-kernel-'+version+'.jar')).read_bytes()).hexdigest()==sha
(N/'candidate.json').write_text(json.dumps(dict(kernelSha256=sha,kernelVersion=version,applicationSha256=hashlib.sha256(app.read_bytes()).hexdigest()),indent=2))
