"""Build only the changed adapter against accepted private kernel; no kernel test rerun."""
import hashlib,json,os,shutil,subprocess,xml.etree.ElementTree as ET
from pathlib import Path
R=Path(__file__).resolve().parent.parent;O=R/'lab/out/release-oracle-null-sides-20261005';B=R/'lab/out/release-empty-foreign-begin-r2-20261005';M=R/'integration/streamhouse-engine-oracle'
O.mkdir(parents=True,exist_ok=False);shutil.copytree(B/'m2',O/'m2');sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
proof=json.loads((B/'proof.json').read_text())
for n in ['kernel','spi']:assert sha(B/(n+'.jar'))==proof[n+'_sha256'];shutil.copyfile(B/(n+'.jar'),O/(n+'.jar'))
env=dict(os.environ);env['JAVA_HOME']='D:/work/Java/jdk-17.0.7';env['PATH']=env['JAVA_HOME']+'/bin;'+env['PATH']
args=['cmd','/d','/c','D:/work/Java/apache-maven-3.9.11/bin/mvn.cmd','--batch-mode','-Dmaven.repo.local='+str(O/'m2'),'-Doracle.kernel.version='+proof['kernel_version'],'clean','verify']
(O/'adapter.command.json').write_text(json.dumps({'args':args,'cwd':str(M)}))
with (O/'adapter.stdout.txt').open('wb') as out,(O/'adapter.stderr.txt').open('wb') as err:p=subprocess.run(args,cwd=M,env=env,stdout=out,stderr=err,timeout=300)
(O/'adapter.rc.txt').write_text(str(p.returncode));assert p.returncode==0
shutil.copytree(M/'target/surefire-reports',O/'tests');counts={k:sum(int(ET.parse(p).getroot().get(k,0)) for p in (O/'tests').glob('TEST-*.xml')) for k in ['tests','errors','failures','skipped']};assert counts=={'tests':16,'errors':0,'failures':0,'skipped':0}
shutil.copyfile(M/'target/streamhouse-engine-oracle-0.1.0-SNAPSHOT.jar',O/'adapter.jar')
(O/'proof.json').write_text(json.dumps({'adapter_sha256':sha(O/'adapter.jar'),'kernel_sha256':sha(O/'kernel.jar'),'spi_sha256':sha(O/'spi.jar'),'kernel_version':proof['kernel_version'],'adapter_tests':counts,'kernel_tests_rerun':False,'full_product_accepted':False},indent=2));print((O/'proof.json').read_text())
