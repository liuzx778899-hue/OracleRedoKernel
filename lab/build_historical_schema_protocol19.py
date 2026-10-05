"""Build isolated binaries for baseline and ordered historical schema evidence."""
import hashlib,json,os,shutil,subprocess,xml.etree.ElementTree as ET
from pathlib import Path
R=Path(__file__).resolve().parent.parent;N=R/'lab/out/historical-schema-protocol-20261006';P=R/'lab/out/streamhouse-oracle-gate-worktree'
assert not (N/'protocol-build-proof.json').exists()
(N/'executed-protocol-build-driver.py').write_bytes(Path(__file__).read_bytes())
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
shutil.copyfile(R/'target/oracle-redo-kernel-0.1.0-SNAPSHOT.jar',N/'kernel.jar')
shutil.copyfile(P/'server/streamhouse-spi/target/streamhouse-spi-0.1.0-SNAPSHOT.jar',N/'spi.jar')
env=dict(os.environ,JAVA_HOME='D:/work/Java/jdk-17.0.7');env['PATH']=env['JAVA_HOME']+'/bin;'+env['PATH']
mvn=['cmd','/d','/c','D:/work/Java/apache-maven-3.9.11/bin/mvn.cmd','--batch-mode']
def run(name,args,cwd):
    assert not (N/(name+'.command.json')).exists();args=list(map(str,args));(N/(name+'.command.json')).write_text(json.dumps(dict(args=args,cwd=str(cwd))))
    p=subprocess.run(args,cwd=cwd,env=env,capture_output=True,timeout=900)
    for suffix,data in [('stdout.txt',p.stdout),('stderr.txt',p.stderr),('rc.txt',str(p.returncode).encode())]:(N/(name+'.'+suffix)).write_bytes(data)
    assert p.returncode==0,(name,p.stdout[-3000:],p.stderr[-1000:])
versions={name:'0.1.0-schema-protocol-'+sha(N/(name+'.jar'))[:12] for name in ['kernel','spi']}
for name,group,artifact in [('kernel','com.streamhouse.oracle','oracle-redo-kernel'),('spi','com.streamhouse','streamhouse-spi')]:
    run('install-'+name,mvn+['org.apache.maven.plugins:maven-install-plugin:3.1.2:install-file','-Dfile='+str(N/(name+'.jar')),'-DgroupId='+group,'-DartifactId='+artifact,'-Dversion='+versions[name],'-Dpackaging=jar','-DgeneratePom=true'],R)
A=R/'integration/streamhouse-engine-oracle'
run('adapter-build',mvn+['-Doracle.kernel.version='+versions['kernel'],'-Dstreamhouse.spi.version='+versions['spi'],'clean','package'],A)
shutil.copyfile(A/'target/streamhouse-engine-oracle-0.1.0-SNAPSHOT.jar',N/'adapter.jar');versions['adapter']='0.1.0-schema-protocol-'+sha(N/'adapter.jar')[:12]
run('install-adapter',mvn+['org.apache.maven.plugins:maven-install-plugin:3.1.2:install-file','-Dfile='+str(N/'adapter.jar'),'-DgroupId=com.streamhouse','-DartifactId=streamhouse-engine-oracle','-Dversion='+versions['adapter'],'-Dpackaging=jar','-DgeneratePom=true'],R)
counts={}
for name,folder in [('kernel',R/'target/surefire-reports'),('spi',P/'server/streamhouse-spi/target/surefire-reports'),('adapter',A/'target/surefire-reports')]:
    dest=N/(name+'-reports');dest.mkdir();total=[0,0,0,0]
    for p in folder.glob('TEST-*.xml'):
        shutil.copyfile(p,dest/p.name);root=ET.parse(p).getroot()
        for i,k in enumerate(['tests','failures','errors','skipped']):total[i]+=int(root.attrib[k])
    assert total[0]>0 and total[1:]==[0,0,0],(name,total);counts[name]=total
out=dict(versions=versions,sha256={name:sha(N/(name+'.jar')) for name in ['kernel','spi','adapter']},tests=counts,product_built=False,target_actions=0)
(N/'protocol-build-proof.json').write_text(json.dumps(out,indent=2));print(json.dumps(out))
