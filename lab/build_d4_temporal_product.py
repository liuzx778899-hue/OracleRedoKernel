"""Pin verified temporal binaries and build the necessary product reactor; no repeated tests."""
from pathlib import Path
import os,json,hashlib,shutil,subprocess,zipfile
R=Path(__file__).resolve().parent.parent;N=R/'lab/out/d4-temporal-20261009/product';N.mkdir(exist_ok=False)
shutil.copyfile(__file__,N/'executed.py');P=R/'lab/out/streamhouse-oracle-gate-worktree';libs=R/'lab/out/d4-temporal-source-20261009/parser-r8/libs'
proof=json.loads((libs.parent/'normal-proof.json').read_text());sha=lambda p:hashlib.sha256(Path(p).read_bytes()).hexdigest()
env=dict(os.environ,JAVA_HOME='D:/work/Java/jdk-17.0.7');env['PATH']=env['JAVA_HOME']+'/bin;'+env['PATH']
mvn=['cmd','/d','/c','D:/work/Java/apache-maven-3.9.11/bin/mvn.cmd','-o','--batch-mode']
def run(name,args,cwd):
 p=subprocess.run(list(map(str,args)),cwd=cwd,env=env,capture_output=True,timeout=900)
 for suffix,data in [('stdout',p.stdout),('stderr',p.stderr),('rc',str(p.returncode).encode())]:(N/(name+'.'+suffix)).write_bytes(data)
 assert p.returncode==0,(name,p.returncode)
for name,group,artifact in [('kernel','com.streamhouse.oracle','oracle-redo-kernel'),('adapter','com.streamhouse','streamhouse-engine-oracle')]:
 jar=libs/(name+'.jar');assert sha(jar)==proof[name+'Sha256'];version='0.1.0-temporal-'+sha(jar)[:12]
 assert version in (P/'server/streamhouse-app/pom.xml').read_text()
 run('install-'+name,mvn+['org.apache.maven.plugins:maven-install-plugin:3.1.2:install-file','-Dfile='+str(jar),'-DgroupId='+group,'-DartifactId='+artifact,'-Dversion='+version,'-Dpackaging=jar','-DgeneratePom=true'],R)
run('package',mvn+['-pl','streamhouse-app','-am','-DskipTests','package'],P/'server')
jar=P/'server/streamhouse-app/target/streamhouse-app-0.1.0-SNAPSHOT.jar';shutil.copyfile(jar,N/'application.jar')
with zipfile.ZipFile(jar) as app:
 for name,prefix in [('kernel','oracle-redo-kernel-'),('adapter','streamhouse-engine-oracle-')]:
  members=[p for p in app.namelist() if p.startswith('BOOT-INF/lib/'+prefix)];assert len(members)==1
  assert hashlib.sha256(app.read(members[0])).hexdigest()==proof[name+'Sha256']
(N/'product-proof.json').write_text(json.dumps(dict(applicationSha256=sha(jar),kernelSha256=proof['kernelSha256'],adapterSha256=proof['adapterSha256'],sourceActions=0,targetActions=0,deployed=False),indent=2))
