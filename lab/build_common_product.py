"""Build the mixed-field candidate in the retained isolated main checkout, preserving primary main."""
from pathlib import Path
import hashlib,json,os,shutil,subprocess,sys
R=Path(__file__).resolve().parent.parent;BASE=R/'lab/out/common-fields-20261010';C=BASE/'candidate-r15';N=BASE/'product-build-r2'
W=Path('D:/work/vibecode/StreamHouse-wt/oracle-main-merge-20261010')
finalize=sys.argv[1:]==['--finalize-successful-r2']
assert (finalize or sys.argv[1:]==['--execute']) and (BASE/'target-r1/proof.json').exists()
assert (BASE/'product-build-r1/head.rc').read_text().strip()=='0'
assert not (BASE/'product-build-r1/install-adapter.rc').exists()
if finalize:
    assert (N/'package.rc').read_text().strip()=='0' and not (N/'candidate.json').exists()
    assert not (N/'executed-finalize.py').exists();shutil.copyfile(__file__,N/'executed-finalize.py')
else:
    N.mkdir(exist_ok=False);shutil.copyfile(__file__,N/'executed.py')
candidate=json.loads((C/'development-candidate.json').read_text());sha=lambda p:hashlib.sha256(Path(p).read_bytes()).hexdigest()
assert sha(C/'kernel.jar')==candidate['kernelSha256'] and sha(C/'adapter.jar')==candidate['adapterSha256']
env=dict(os.environ,JAVA_HOME='D:/work/Java/jdk-17.0.7');env['PATH']=env['JAVA_HOME']+'/bin;'+env['PATH']
mvn=['cmd','/d','/c','D:/work/Java/apache-maven-3.9.11/bin/mvn.cmd','-o','--batch-mode']
def run(label,args,cwd):
    p=subprocess.run(list(map(str,args)),cwd=cwd,env=env,capture_output=True,timeout=900)
    for suffix,value in [('stdout',p.stdout),('stderr',p.stderr),('rc',str(p.returncode).encode())]:(N/(label+'.'+suffix)).write_bytes(value)
    assert p.returncode==0,(label,p.returncode)
    return p.stdout.decode(errors='replace')
if not finalize:
    assert run('head',['git','rev-parse','HEAD'],W).strip()=='29f36a5cf0093c423ac42f3a9e7f9f145cceeff6'
    status=run('status',['git','status','--porcelain','--untracked-files=no'],W);assert not status.strip(),status
kernel=candidate['kernelVersion'];adapter='0.1.0-common-'+candidate['kernelSha256'][:12]+'-'+candidate['adapterSha256'][:12]
pom=(R/'integration/streamhouse-engine-oracle/pom.xml').read_text().replace('<version>0.1.0-SNAPSHOT</version>','<version>'+adapter+'</version>',1).replace('<oracle.kernel.version>0.1.0-SNAPSHOT</oracle.kernel.version>','<oracle.kernel.version>'+kernel+'</oracle.kernel.version>')
path=W/'server/streamhouse-app/pom.xml';old=(N/'main-app-original.pom').read_bytes() if finalize else path.read_bytes()
assert old.count(b'0.1.0-lob-trust-4c619ca01918')==1 and old.count(b'0.1.0-readcut-53fa9a98275d')==1
expected=old.replace(b'0.1.0-lob-trust-4c619ca01918',adapter.encode()).replace(b'0.1.0-readcut-53fa9a98275d',kernel.encode())
if not finalize:
    (N/'adapter.pom').write_text(pom)
    run('install-adapter',mvn+['org.apache.maven.plugins:maven-install-plugin:3.1.2:install-file','-Dfile='+str(C/'adapter.jar'),'-DpomFile='+str(N/'adapter.pom')],R)
    (N/'main-app-original.pom').write_bytes(old);path.write_bytes(expected)
    run('package',mvn+['-pl','streamhouse-app','-am','-DskipTests','package'],W/'server')
else:
    assert run('finalize-head',['git','rev-parse','HEAD'],W).strip()=='29f36a5cf0093c423ac42f3a9e7f9f145cceeff6'
    assert path.read_bytes()==expected and (N/'adapter.pom').read_text()==pom
shutil.copyfile(W/'server/streamhouse-app/target/streamhouse-app-0.1.0-SNAPSHOT.jar',N/'application.jar')
(N/'candidate.json').write_text(json.dumps(dict(baseMain='29f36a5cf0093c423ac42f3a9e7f9f145cceeff6',kernelVersion=kernel,adapterVersion=adapter,kernelSha256=candidate['kernelSha256'],adapterSha256=candidate['adapterSha256'],applicationSha256=sha(N/'application.jar'),primaryMainModified=False),indent=2))
print('ISOLATED_MIXED_PRODUCT_CANDIDATE',flush=True)
