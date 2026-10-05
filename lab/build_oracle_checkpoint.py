"""Build checkpoint adapter only; preserve accepted kernel and SPI artifacts."""
import hashlib,json,os,shutil,subprocess,xml.etree.ElementTree as ET
from pathlib import Path
R=Path(__file__).resolve().parent.parent;O=R/'lab/out/release-oracle-checkpoint-20261005';W=R/'lab/out/release-oracle-window-20261005';M=R/'integration/streamhouse-engine-oracle';O.mkdir(parents=True,exist_ok=False)
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
old=json.loads((W/'proof.json').read_text())
for name in ['kernel','spi']:
    assert sha(W/(name+'.jar'))==old[name+'_sha256'];shutil.copyfile(W/(name+'.jar'),O/(name+'.jar'))
env=dict(os.environ);env['JAVA_HOME']='D:/work/Java/jdk-17.0.7';env['PATH']=env['JAVA_HOME']+'/bin;'+env['PATH']
args=['cmd','/d','/c','D:/work/Java/apache-maven-3.9.11/bin/mvn.cmd','--batch-mode','-Dmaven.repo.local='+str(W/'m2'),'clean','verify'];(O/'build.command.json').write_text(json.dumps({'args':args,'cwd':str(M)}))
with (O/'build.stdout.txt').open('wb') as out,(O/'build.stderr.txt').open('wb') as err:p=subprocess.run(args,cwd=M,env=env,stdout=out,stderr=err,timeout=300)
(O/'build.rc.txt').write_text(str(p.returncode));assert p.returncode==0
shutil.copytree(M/'target/surefire-reports',O/'tests')
counts={k:sum(int(ET.parse(p).getroot().get(k,0)) for p in (O/'tests').glob('TEST-*.xml')) for k in ['tests','failures','errors','skipped']};assert counts=={'tests':15,'failures':0,'errors':0,'skipped':0}
shutil.copyfile(M/'target/streamhouse-engine-oracle-0.1.0-SNAPSHOT.jar',O/'adapter.jar')
commit=subprocess.check_output(['git','rev-parse','HEAD'],cwd=R).decode().strip()
subprocess.run(['git','-c','core.autocrlf=false','archive','--format=zip','--output='+str(O/'source-tree.zip'),commit],cwd=R,check=True)
proof={'runtime_commit':commit,'kernel_sha256':old['kernel_sha256'],'spi_sha256':old['spi_sha256'],'adapter_sha256':sha(O/'adapter.jar'),'adapter_tests':counts,'kernel_tests_rerun':False,'target_driver_changed':False,'checkpoint_sigkill_accepted':False}
(O/'proof.json').write_text(json.dumps(proof,indent=2));print(json.dumps(proof))
