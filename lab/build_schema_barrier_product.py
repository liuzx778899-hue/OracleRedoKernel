"""Build clean committed product with only the new private kernel coordinate."""
import hashlib,json,os,shutil,subprocess,xml.etree.ElementTree as ET
from pathlib import Path
R=Path(__file__).resolve().parent.parent;P=R/'lab/out/streamhouse-oracle-gate-worktree';O=R/'lab/out/schema-barrier-20261005';sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
assert not subprocess.check_output(['git','status','--porcelain'],cwd=P)
assert (O/'private-install.rc.txt').read_text().strip()=='0'
assert sha(O/'fixed-kernel.jar')=='9275ab9062413ae7fa88ac3a147337fbe1cc1d9b1c7add834f374c401a9570c1'
args=['cmd','/d','/c','D:/work/Java/apache-maven-3.9.11/bin/mvn.cmd','--batch-mode','-s',P/'.mvn/settings.xml','-f',P/'server/pom.xml','clean','verify'];assert not (O/'product-reactor.command.json').exists()
commit=subprocess.check_output(['git','rev-parse','HEAD'],cwd=P).decode().strip();(O/'product-reactor.command.json').write_text(json.dumps(dict(args=list(map(str,args)),commit=commit,kernel_sha256=sha(O/'fixed-kernel.jar'))))
env=dict(os.environ);env['JAVA_HOME']='D:/work/Java/jdk-17.0.7'
with (O/'product-reactor.stdout.txt').open('wb') as out,(O/'product-reactor.stderr.txt').open('wb') as err:p=subprocess.run(list(map(str,args)),cwd=P,env=env,stdout=out,stderr=err,timeout=600)
(O/'product-reactor.rc.txt').write_text(str(p.returncode));assert p.returncode==0
reports=list((P/'server').glob('*/target/surefire-reports/TEST-*.xml'));counts={k:sum(int(ET.parse(p).getroot().get(k,0)) for p in reports) for k in ['tests','failures','errors','skipped']};assert counts==dict(tests=735,failures=0,errors=0,skipped=0)
jar=P/'server/streamhouse-app/target/streamhouse-app-0.1.0-SNAPSHOT.jar';shutil.copyfile(jar,O/'application.jar')
(O/'product-build-proof.json').write_text(json.dumps(dict(commit=commit,tests=counts,app_sha256=sha(jar),kernel_sha256=sha(O/'fixed-kernel.jar'),adapter_sha256=sha(O/'adapter.jar'),reports=[dict(path=str(p.relative_to(P)),sha256=sha(p)) for p in reports]),indent=2));print('HISTORICAL_MARKER_PRODUCT_REACTOR_VERIFIED|735')
