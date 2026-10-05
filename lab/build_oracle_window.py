"""Build kernel and window adapter into a new isolated Maven repository."""
import hashlib,json,os,shutil,subprocess,xml.etree.ElementTree as ET
from pathlib import Path
R=Path(__file__).resolve().parent.parent;O=R/'lab/out/release-oracle-window-20261005';O.mkdir(parents=True,exist_ok=False)
shutil.copytree(R/'lab/out/release-oracle-projection-20261005/m2',O/'m2')
env=dict(os.environ);env['JAVA_HOME']='D:/work/Java/jdk-17.0.7';env['PATH']=env['JAVA_HOME']+'/bin;'+env['PATH']
def run(name,cwd,goals):
    args=['cmd','/d','/c','D:/work/Java/apache-maven-3.9.11/bin/mvn.cmd','--batch-mode','-Dmaven.repo.local='+str(O/'m2')]+goals
    (O/(name+'.command.json')).write_text(json.dumps({'args':args,'cwd':str(cwd)}))
    with (O/(name+'.stdout.txt')).open('wb') as out,(O/(name+'.stderr.txt')).open('wb') as err:p=subprocess.run(args,cwd=cwd,env=env,stdout=out,stderr=err,timeout=300)
    (O/(name+'.rc.txt')).write_text(str(p.returncode));assert p.returncode==0,name
    reports=O/(name+'-tests');shutil.copytree(cwd/'target/surefire-reports',reports)
    return {k:sum(int(ET.parse(p).getroot().get(k,0)) for p in reports.glob('TEST-*.xml')) for k in ['tests','failures','errors','skipped']}
k=run('kernel',R,['clean','install']);assert k=={'tests':245,'failures':0,'errors':0,'skipped':0}
a=run('adapter',R/'integration/streamhouse-engine-oracle',['clean','verify']);assert a=={'tests':11,'failures':0,'errors':0,'skipped':0}
for name,p in [('kernel.jar',R/'target/oracle-redo-kernel-0.1.0-SNAPSHOT.jar'),('adapter.jar',R/'integration/streamhouse-engine-oracle/target/streamhouse-engine-oracle-0.1.0-SNAPSHOT.jar')]:shutil.copyfile(p,O/name)
shutil.copyfile(O/'m2/com/streamhouse/streamhouse-spi/0.1.0-SNAPSHOT/streamhouse-spi-0.1.0-SNAPSHOT.jar',O/'spi.jar')
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
assert sha(O/'spi.jar')=='93e451fe6c16c665cbdc5b5471a9879cd0a01ec7699a5d9666068dc14c2f8523'
commit=subprocess.check_output(['git','rev-parse','HEAD'],cwd=R).decode().strip()
subprocess.run(['git','-c','core.autocrlf=false','archive','--format=zip','--output='+str(O/'source-tree.zip'),commit],cwd=R,check=True)
proof={'runtime_commit':commit,'kernel_tests':k,'adapter_tests':a,'kernel_sha256':sha(O/'kernel.jar'),'adapter_sha256':sha(O/'adapter.jar'),'spi_sha256':sha(O/'spi.jar'),'private_maven':True,'old_repository_unchanged':True,'checkpoint_implemented':False}
(O/'proof.json').write_text(json.dumps(proof,indent=2));print(json.dumps(proof))
