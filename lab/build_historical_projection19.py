"""Build a separate adapter candidate against the frozen historical-event kernel."""
import hashlib,json,os,subprocess,shutil,xml.etree.ElementTree as ET
from pathlib import Path
R=Path(__file__).resolve().parent.parent;N=R/'lab/out/historical-product-candidate-20261006'
assert not (N/'projection-build-proof.json').exists()
N.mkdir(exist_ok=True);(N/'executed-projection-build-driver.py').write_bytes(Path(__file__).read_bytes())
env=dict(os.environ,JAVA_HOME='D:/work/Java/jdk-17.0.7');env['PATH']=env['JAVA_HOME']+'/bin;'+env['PATH']
mvn=['cmd','/d','/c','D:/work/Java/apache-maven-3.9.11/bin/mvn.cmd','--batch-mode']
def run(name,args,cwd):
    assert not (N/(name+'.command.json')).exists()
    (N/(name+'.command.json')).write_text(json.dumps(dict(args=list(map(str,args)),cwd=str(cwd))))
    p=subprocess.run(list(map(str,args)),cwd=cwd,env=env,capture_output=True,timeout=600)
    for suffix,data in [('stdout.txt',p.stdout),('stderr.txt',p.stderr),('rc.txt',str(p.returncode).encode())]:(N/(name+'.'+suffix)).write_bytes(data)
    assert p.returncode==0,(name,p.stdout[-2000:],p.stderr[-1000:])
for name,group,artifact,version,path in [('kernel','com.streamhouse.oracle','oracle-redo-kernel','0.1.0-historical-3ccca289e717',R/'lab/out/historical-row-schema-candidate-20261006/kernel.jar'),
    ('spi','com.streamhouse','streamhouse-spi','0.1.0-oracle-evidence-fb9c443b',R/'lab/out/dictionary-baseline-candidate-20261006/spi.jar')]:
    run('install-'+name+'-r2',mvn+['org.apache.maven.plugins:maven-install-plugin:3.1.2:install-file','-Dfile='+str(path),'-DgroupId='+group,'-DartifactId='+artifact,'-Dversion='+version,'-Dpackaging=jar','-DgeneratePom=true'],R)
adapter=R/'integration/streamhouse-engine-oracle'
run('adapter-full-build',mvn+['-Doracle.kernel.version=0.1.0-historical-3ccca289e717','-Dstreamhouse.spi.version=0.1.0-oracle-evidence-fb9c443b','clean','package'],adapter)
shutil.copyfile(adapter/'target/streamhouse-engine-oracle-0.1.0-SNAPSHOT.jar',N/'adapter.jar')
reports=N/'adapter-reports';reports.mkdir();counts=[0,0,0,0]
for p in (adapter/'target/surefire-reports').glob('TEST-*.xml'):
    shutil.copyfile(p,reports/p.name);root=ET.parse(p).getroot()
    for i,k in enumerate(['tests','failures','errors','skipped']):counts[i]+=int(root.attrib[k])
assert counts[0]>=24 and counts[1:]==[0,0,0],counts
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
(N/'projection-build-proof.json').write_text(json.dumps(dict(adapter_sha256=sha(N/'adapter.jar'),tests=counts,kernel_sha256=sha(R/'lab/out/historical-row-schema-candidate-20261006/kernel.jar'),product_rebuilt=False,target_actions=0),indent=2))
print(json.dumps(dict(adapter_sha256=sha(N/'adapter.jar'),tests=counts)))
