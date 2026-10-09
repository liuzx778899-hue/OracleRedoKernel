"""Package the temporal candidate after targeted kernel tests; no database or deployment actions."""
from pathlib import Path
import os,sys,json,hashlib,shutil,subprocess,xml.etree.ElementTree as ET
R=Path(__file__).resolve().parent.parent;E=R/'lab/out/d4-temporal-20261009';N=E;A=R/'integration/streamhouse-engine-oracle'
assert (E/'kernel.rc').read_text().strip()=='0'
resumeTest=sys.argv[1:]==['--resume-test']
if resumeTest:
 N=E/'build-r2';assert (N/'adapter.rc').read_text().strip()=='1' and not (N/'adapter-r3.rc').exists()
elif sys.argv[1:]==['--resume-dependencies']:
 assert (E/'build-driver.rc').read_text().strip()=='1' and not (E/'install-kernel.rc').exists()
 N=E/'build-r2';N.mkdir(exist_ok=False)
else:assert sys.argv[1:]==[]
executed=N/('executed-test-resume.py' if resumeTest else 'executed-build.py');assert not executed.exists()
shutil.copyfile(__file__,executed)
sha=lambda p:hashlib.sha256(Path(p).read_bytes()).hexdigest()
shutil.copyfile(R/'target/oracle-redo-kernel-0.1.0-SNAPSHOT.jar',N/'kernel.jar')
version='0.1.0-temporal-'+sha(N/'kernel.jar')[:12]
spi=R/'lab/out/d3-stream-runtime-20261009/producer-product-r109/libs/streamhouse-spi-0.1.0-SNAPSHOT.jar'
assert sha(spi)=='42f0b4aa41322b26b7c8acdd2a63b26edeeb6556a275c1b699becc323872b9e4'
env=dict(os.environ,JAVA_HOME='D:/work/Java/jdk-17.0.7');env['PATH']=env['JAVA_HOME']+'/bin;'+env['PATH']
mvn=['cmd','/d','/c','D:/work/Java/apache-maven-3.9.11/bin/mvn.cmd','-o','--batch-mode']
def run(name,args,cwd):
 assert not (N/(name+'.rc')).exists();(N/(name+'.command.json')).write_text(json.dumps(dict(args=list(map(str,args)),cwd=str(cwd))))
 p=subprocess.run(list(map(str,args)),cwd=cwd,env=env,capture_output=True,timeout=600)
 for suffix,data in [('stdout',p.stdout),('stderr',p.stderr),('rc',str(p.returncode).encode())]:(N/(name+'.'+suffix)).write_bytes(data)
 assert p.returncode==0,(name,p.returncode)
if not resumeTest:run('install-kernel',mvn+['org.apache.maven.plugins:maven-install-plugin:3.1.2:install-file','-Dfile='+str(N/'kernel.jar'),'-DgroupId=com.streamhouse.oracle','-DartifactId=oracle-redo-kernel','-Dversion='+version,'-Dpackaging=jar','-DgeneratePom=true'],R)
spiVersion='0.1.0-temporal-spi-'+sha(spi)[:12]
if not resumeTest:run('install-spi',mvn+['org.apache.maven.plugins:maven-install-plugin:3.1.2:install-file','-Dfile='+str(spi),'-DgroupId=com.streamhouse','-DartifactId=streamhouse-spi','-Dversion='+spiVersion,'-Dpackaging=jar','-DgeneratePom=true'],R)
run('adapter-r3' if resumeTest else 'adapter',mvn+['-Doracle.kernel.version='+version,'-Dstreamhouse.spi.version='+spiVersion,'-Dtest='+('OracleTemporalProjectionTest' if resumeTest else 'OracleTemporalProjectionTest,OracleSnapshotAcquirerTest,OracleBatchProjectionTest'),'test','package'],A)
shutil.copyfile(A/'target/streamhouse-engine-oracle-0.1.0-SNAPSHOT.jar',N/'adapter.jar')
counts={}
for label,folder,names in [('kernel',R/'target/surefire-reports',['OracleScalarsTest','FrozenHeapBaselineTest','DictionaryHistoryRowsTest','DictionaryBaselineTest']),('adapter',A/'target/surefire-reports',['OracleTemporalProjectionTest','OracleSnapshotAcquirerTest','OracleBatchProjectionTest'])]:
 total=[0,0,0,0];dest=N/(label+'-reports');dest.mkdir()
 for name in names:
  files=list(folder.glob('TEST-*.'+name+'.xml'));assert len(files)==1;file=files[0];shutil.copyfile(file,dest/file.name);root=ET.parse(file).getroot()
  for i,k in enumerate(['tests','failures','errors','skipped']):total[i]+=int(root.attrib[k])
 assert total[0]>0 and total[1:]==[0,0,0];counts[label]=total
(N/'candidate-proof.json').write_text(json.dumps(dict(kernelVersion=version,kernelSha256=sha(N/'kernel.jar'),adapterSha256=sha(N/'adapter.jar'),spiSha256=sha(spi),tests=counts,sourceActions=0,targetActions=0,realTemporalVerified=False,productIntegrated=False),indent=2))
