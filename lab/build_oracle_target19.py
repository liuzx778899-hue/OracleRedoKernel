"""Build new JDBC target port using the existing private, pinned dependency repository."""
import hashlib,json,os,shutil,subprocess,xml.etree.ElementTree as ET
from pathlib import Path

ROOT=Path(__file__).resolve().parent.parent;OUT=ROOT/'lab/out/release-oracle-target-20261005'
OLD=ROOT/'lab/out/release-oracle-projection-20261005';MODULE=ROOT/'integration/streamhouse-engine-oracle'
OUT.mkdir(parents=True,exist_ok=False);env=dict(os.environ);env['JAVA_HOME']='D:/work/Java/jdk-17.0.7';env['PATH']=env['JAVA_HOME']+'/bin;'+env['PATH']
previous=json.loads((OLD/'proof.json').read_text())
for path,digest in [(OLD/'m2/com/streamhouse/streamhouse-spi/0.1.0-SNAPSHOT/streamhouse-spi-0.1.0-SNAPSHOT.jar',previous['spi_contract_jar_sha256']),
                    (OLD/'m2/com/streamhouse/oracle/oracle-redo-kernel/0.1.0-SNAPSHOT/oracle-redo-kernel-0.1.0-SNAPSHOT.jar',previous['kernel_sha256'])]:
    assert hashlib.sha256(path.read_bytes()).hexdigest()==digest
args=['cmd','/d','/c','D:/work/Java/apache-maven-3.9.11/bin/mvn.cmd','--batch-mode','-Dmaven.repo.local='+str(OLD/'m2'),'clean','verify']
(OUT/'build.command.json').write_text(json.dumps({'args':args,'cwd':str(MODULE)}))
with (OUT/'build.stdout.txt').open('wb') as stdout,(OUT/'build.stderr.txt').open('wb') as stderr:
    result=subprocess.run(args,cwd=MODULE,env=env,stdout=stdout,stderr=stderr,timeout=300)
(OUT/'build.rc.txt').write_text(str(result.returncode));assert result.returncode==0
shutil.copytree(MODULE/'target/surefire-reports',OUT/'surefire-reports');jar=OUT/'streamhouse-engine-oracle-0.1.0-SNAPSHOT.jar';shutil.copyfile(MODULE/'target'/jar.name,jar)
counts={key:sum(int(ET.parse(p).getroot().get(key,0)) for p in (OUT/'surefire-reports').glob('TEST-*.xml')) for key in ['tests','failures','errors','skipped']}
assert counts=={'tests':5,'failures':0,'errors':0,'skipped':0}
commit=subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT).decode().strip()
subprocess.run(['git','-c','core.autocrlf=false','archive','--format=zip','--output='+str(OUT/'source-tree.zip'),commit],cwd=ROOT,check=True)
proof={'adapter_runtime_commit':commit,'adapter_jar_sha256':hashlib.sha256(jar.read_bytes()).hexdigest(),'kernel_sha256':previous['kernel_sha256'],'spi_contract_jar_sha256':previous['spi_contract_jar_sha256'],'tests':counts,'kernel_tests_rerun':False,'private_maven':True}
(OUT/'proof.json').write_text(json.dumps(proof,indent=2));print(json.dumps(proof))
