"""Build committed isolated product and verify its actual embedded private binaries."""
import hashlib,json,os,shutil,subprocess,zipfile,xml.etree.ElementTree as E
from pathlib import Path
R=Path(__file__).resolve().parent.parent;P=R/'lab/out/streamhouse-oracle-gate-worktree';O=R/'lab/out/v2-dml-capture-20261006'
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
assert not subprocess.check_output(['git','status','--porcelain'],cwd=P)
kernel=O/'implicit-tail-libs/kernel.jar';assert sha(kernel)=='672f01bc0c4a78856ce40af4de778bdca3fe881d3c46eb5de3fb069ff433302e'
installed=Path('C:/Users/Administrator/.m2/repository/com/streamhouse/oracle/oracle-redo-kernel/0.1.0-inverse-tail-672f01bc/oracle-redo-kernel-0.1.0-inverse-tail-672f01bc.jar');assert sha(installed)==sha(kernel)
assert (O/'private-672-install-r2.rc.txt').read_text().strip()=='0'
args=['cmd','/d','/c','D:/work/Java/apache-maven-3.9.11/bin/mvn.cmd','-B','-s',str(P/'.mvn/settings.xml'),'-f',str(P/'server/pom.xml'),'clean','verify']
commit=subprocess.check_output(['git','rev-parse','HEAD'],cwd=P).decode().strip();assert not (O/'product-672-reactor.command.json').exists()
(O/'product-672-reactor.command.json').write_text(json.dumps(dict(args=args,commit=commit,driver_sha256=sha(Path(__file__)),kernel_sha256=sha(kernel)),indent=2))
env=dict(os.environ);env['JAVA_HOME']='D:/work/Java/jdk-17.0.7'
with (O/'product-672-reactor.stdout.txt').open('wb') as out,(O/'product-672-reactor.stderr.txt').open('wb') as err:
    p=subprocess.run(args,cwd=P,env=env,stdout=out,stderr=err,timeout=1200)
(O/'product-672-reactor.rc.txt').write_text(str(p.returncode));assert p.returncode==0
reports=list((P/'server').glob('*/target/surefire-reports/TEST-*.xml'));counts={k:sum(int(E.parse(p).getroot().get(k,0)) for p in reports) for k in ['tests','failures','errors','skipped']}
assert counts==dict(tests=737,failures=0,errors=0,skipped=0),counts
jar=P/'server/streamhouse-app/target/streamhouse-app-0.1.0-SNAPSHOT.jar';copy=O/'application-672.jar';assert not copy.exists();shutil.copyfile(jar,copy)
with zipfile.ZipFile(copy) as z:
    for name,expected in [('oracle-redo-kernel-0.1.0-inverse-tail-672f01bc.jar',sha(kernel)),('streamhouse-engine-oracle-0.1.0-dictionary-snapshot-9516d4f9.jar',sha(O/'implicit-tail-libs/adapter.jar'))]:
        assert hashlib.sha256(z.read('BOOT-INF/lib/'+name)).hexdigest()==expected
proof=dict(commit=commit,tests=counts,app_sha256=sha(copy),kernel_sha256=sha(kernel),adapter_sha256=sha(O/'implicit-tail-libs/adapter.jar'),reports={str(p.relative_to(P)):sha(p) for p in reports},http_runtime_verified=False)
(O/'product-672-proof.json').write_text(json.dumps(proof,indent=2));print(json.dumps({k:v for k,v in proof.items() if k!='reports'}))
