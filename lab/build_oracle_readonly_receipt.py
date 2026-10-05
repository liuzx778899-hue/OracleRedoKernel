"""Build adapter change only; retained kernel/SPI and their accepted tests are not rerun."""
import hashlib,json,os,shutil,subprocess,xml.etree.ElementTree as ET
from pathlib import Path
R=Path(__file__).resolve().parent.parent;OLD=R/'lab/out/release-oracle-typed-evidence-20261005';O=R/'lab/out/oracle-readonly-receipt-20261005';M=R/'integration/streamhouse-engine-oracle'
O.mkdir(exist_ok=False);shutil.copytree(OLD/'m2',O/'m2');proof=json.loads((OLD/'proof.json').read_text())
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
for n in ['kernel','spi']:
    assert sha(OLD/(n+'.jar'))==proof[n+'_sha256'];shutil.copyfile(OLD/(n+'.jar'),O/(n+'.jar'))
env=dict(os.environ);env['JAVA_HOME']='D:/work/Java/jdk-17.0.7';env['PATH']=env['JAVA_HOME']+'/bin;'+env['PATH']
args=['cmd','/d','/c','D:/work/Java/apache-maven-3.9.11/bin/mvn.cmd','--batch-mode','-Dmaven.repo.local='+str(O/'m2'),'-Doracle.kernel.version='+proof['kernel_version'],'clean','verify']
(O/'build.command.json').write_text(json.dumps({'args':args,'cwd':str(M),'source_commit':subprocess.check_output(['git','rev-parse','HEAD'],cwd=R).decode().strip()}))
with (O/'build.stdout.txt').open('wb') as out,(O/'build.stderr.txt').open('wb') as err:p=subprocess.run(args,cwd=M,env=env,stdout=out,stderr=err,timeout=900)
(O/'build.rc.txt').write_text(str(p.returncode));assert p.returncode==0
counts={k:0 for k in ['tests','errors','failures','skipped']}
for f in (M/'target/surefire-reports').glob('TEST-*.xml'):
    for k in counts:counts[k]+=int(ET.parse(f).getroot().get(k,0))
assert counts==dict(tests=16,errors=0,failures=0,skipped=0)
shutil.copytree(M/'target/surefire-reports',O/'tests');shutil.copyfile(M/'target/streamhouse-engine-oracle-0.1.0-SNAPSHOT.jar',O/'adapter.jar')
(O/'build-proof.json').write_text(json.dumps({'tests':counts,'adapter_sha256':sha(O/'adapter.jar'),'kernel_sha256':sha(O/'kernel.jar'),'spi_sha256':sha(O/'spi.jar'),'kernel_tests_rerun':False},indent=2));print('READ_ONLY_RECEIPT_ADAPTER_BUILT|16')
