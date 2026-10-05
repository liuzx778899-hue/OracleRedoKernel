"""Isolated V3 kernel/adapter candidate build, no product installation or source mutation."""
import hashlib,json,os,shutil,subprocess,xml.etree.ElementTree as ET
from pathlib import Path
R=Path(__file__).resolve().parent.parent;O=R/'lab/out/dictionary-baseline-candidate-20261006';Q=R/'lab/out/sys-full-baseline-research-20261006';M=R/'integration/streamhouse-engine-oracle'
assert (Q/'kernel-full-r1.rc.txt').read_text().strip()=='0';O.mkdir(exist_ok=False)
commit=subprocess.check_output(['git','rev-parse','HEAD'],cwd=R).decode().strip();assert commit.startswith('6b68786')
shutil.copyfile(Path(__file__),O/'executed-build-driver.py')
for name,p in [('kernel',R/'target/oracle-redo-kernel-0.1.0-SNAPSHOT.jar'),('spi',R/'lab/out/dictionary-anchor-real-20261005/spi.jar'),('ojdbc8',R/'lab/out/oracle-snapshot-acquisition-20261005/ojdbc8.jar')]:shutil.copyfile(p,O/(name+'.jar'))
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
assert sha(O/'spi.jar')=='fb9c443ba88e49b656d97a4bd881c31a3c9fef305bf9d5a503c13364ed940b95'
env=os.environ.copy();env['JAVA_HOME']='D:/work/Java/jdk-17.0.7';env['PATH']=env['JAVA_HOME']+'/bin;'+env['PATH']
mvn=['cmd','/d','/c','D:/work/Java/apache-maven-3.9.11/bin/mvn.cmd','--batch-mode','-Dmaven.repo.local='+str(O/'m2')]
def run(name,args,cwd=R):
    args=list(map(str,args));(O/(name+'.command.json')).write_text(json.dumps(dict(args=args,cwd=str(cwd))))
    p=subprocess.run(args,cwd=cwd,env=env,capture_output=True,timeout=600)
    for suffix,data in [('stdout.txt',p.stdout),('stderr.txt',p.stderr),('rc.txt',str(p.returncode).encode())]:(O/(name+'.'+suffix)).write_bytes(data)
    assert p.returncode==0,name
def reports(folder,label):
    counts=[0,0,0,0];dest=O/label;dest.mkdir()
    for p in folder.glob('TEST-*.xml'):
        t=ET.parse(p).getroot();shutil.copyfile(p,dest/p.name)
        for i,k in enumerate(['tests','failures','errors','skipped']):counts[i]+=int(t.attrib[k])
    assert counts[0]>0 and counts[1:]==[0,0,0];return counts
kernel_tests=reports(R/'target/surefire-reports','kernel-reports');assert kernel_tests==[290,0,0,0],kernel_tests
version='0.1.0-dictionary-baseline-'+sha(O/'kernel.jar')[:12]
for name,group,artifact,v in [('kernel','com.streamhouse.oracle','oracle-redo-kernel',version),('spi','com.streamhouse','streamhouse-spi','0.1.0-oracle-evidence-fb9c443b')]:
    run('install-'+name,mvn+['org.apache.maven.plugins:maven-install-plugin:3.1.2:install-file','-Dfile='+str(O/(name+'.jar')),'-DgroupId='+group,'-DartifactId='+artifact,'-Dversion='+v,'-Dpackaging=jar','-DgeneratePom=true'])
run('adapter-full-build',mvn+['-Doracle.kernel.version='+version,'-Dstreamhouse.spi.version=0.1.0-oracle-evidence-fb9c443b','clean','package'],M)
adapter_tests=reports(M/'target/surefire-reports','adapter-reports');shutil.copyfile(M/'target/streamhouse-engine-oracle-0.1.0-SNAPSHOT.jar',O/'adapter.jar')
(O/'build-proof.json').write_text(json.dumps(dict(source_commit=commit,kernel_tests=kernel_tests,adapter_tests=adapter_tests,kernel_version=version,kernel_sha256=sha(O/'kernel.jar'),adapter_sha256=sha(O/'adapter.jar'),spi_sha256=sha(O/'spi.jar'),product_rebuilt=False,source_actions=0,target_actions=0),indent=2))
print((O/'build-proof.json').read_text())
