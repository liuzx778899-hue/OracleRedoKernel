"""Build committed product reactor, pin its SPI in a private coordinate, and build adapter only."""
import hashlib,json,os,shutil,subprocess,sys,xml.etree.ElementTree as ET
from pathlib import Path
R=Path(__file__).resolve().parent.parent;P=R/'lab/out/streamhouse-oracle-gate-worktree'
O=R/'lab/out/release-oracle-typed-evidence-20261005';OLD=R/'lab/out/release-oracle-null-sides-20261005'
resume=sys.argv[1:]==['--resume-adapter']
if not resume:O.mkdir(parents=True,exist_ok=False)
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
assert not subprocess.check_output(['git','status','--porcelain'],cwd=P)
commit=subprocess.check_output(['git','rev-parse','HEAD'],cwd=P).decode().strip();assert commit.startswith('ddff6ae')
env=dict(os.environ);env['JAVA_HOME']='D:/work/Java/jdk-17.0.7';env['PATH']=env['JAVA_HOME']+'/bin;'+env['PATH']
def run(n,args,cwd):
    assert not (O/(n+'.rc.txt')).exists()
    args=list(map(str,args));(O/(n+'.command.json')).write_text(json.dumps({'args':args,'cwd':str(cwd),'product_commit':commit}))
    with (O/(n+'.stdout.txt')).open('wb') as out,(O/(n+'.stderr.txt')).open('wb') as err:
        p=subprocess.run(args,cwd=cwd,env=env,stdout=out,stderr=err,timeout=900)
    (O/(n+'.rc.txt')).write_text(str(p.returncode));assert p.returncode==0,n
def counts(folder):
    result={k:0 for k in ['tests','errors','failures','skipped']}
    for f in folder.rglob('TEST-*.xml'):
        for k in result:result[k]+=int(ET.parse(f).getroot().get(k,0))
    assert result['tests']>0 and result['errors']==result['failures']==0
    return result
mvn=['cmd','/d','/c','D:/work/Java/apache-maven-3.9.11/bin/mvn.cmd','--batch-mode']
if not resume:run('product-reactor-verify',mvn+['-s',P/'.mvn/settings.xml','-f',P/'server/pom.xml','clean','verify'],P)
else:
    assert (O/'product-reactor-verify.rc.txt').read_text()=='0'
    assert (O/'install-private-spi.rc.txt').read_text()=='0' and (O/'adapter-verify.rc.txt').read_text()=='1'
    assert json.loads((O/'product-reactor-verify.command.json').read_text())['product_commit']==commit
product_tests=counts(P/'server');assert product_tests==dict(tests=704,errors=0,failures=0,skipped=0)
if not resume:
    shutil.copyfile(P/'server/streamhouse-spi/target/streamhouse-spi-0.1.0-SNAPSHOT.jar',O/'spi.jar')
    shutil.copytree(OLD/'m2',O/'m2')
else:assert sha(P/'server/streamhouse-spi/target/streamhouse-spi-0.1.0-SNAPSHOT.jar')==sha(O/'spi.jar')
kernel=json.loads((OLD/'proof.json').read_text())
assert sha(OLD/'kernel.jar')==kernel['kernel_sha256']
if not resume:shutil.copyfile(OLD/'kernel.jar',O/'kernel.jar')
else:assert sha(O/'kernel.jar')==kernel['kernel_sha256']
version='0.1.0-oracle-evidence-ddff6ae'
if not resume:run('install-private-spi',mvn+['-Dmaven.repo.local='+str(O/'m2'),'org.apache.maven.plugins:maven-install-plugin:3.1.1:install-file','-Dfile='+str(O/'spi.jar'),'-DgroupId=com.streamhouse','-DartifactId=streamhouse-spi','-Dversion='+version,'-Dpackaging=jar','-DgeneratePom=true'],R)
M=R/'integration/streamhouse-engine-oracle'
run('adapter-verify-r2' if resume else 'adapter-verify',mvn+['-Dmaven.repo.local='+str(O/'m2'),'-Doracle.kernel.version='+kernel['kernel_version'],'clean','verify'],M)
adapter_tests=counts(M/'target/surefire-reports');assert adapter_tests==dict(tests=16,errors=0,failures=0,skipped=0)
shutil.copytree(M/'target/surefire-reports',O/'adapter-tests')
shutil.copyfile(M/'target/streamhouse-engine-oracle-0.1.0-SNAPSHOT.jar',O/'adapter.jar')
reports=[]
for f in sorted((P/'server').glob('*/target/surefire-reports/TEST-*.xml')):reports.append({'path':str(f.relative_to(P)),'sha256':sha(f)})
(O/'proof.json').write_text(json.dumps({'product_commit':commit,'product_tests':product_tests,'product_reports':reports,'adapter_tests':adapter_tests,'kernel_version':kernel['kernel_version'],'spi_version':version,'kernel_sha256':sha(O/'kernel.jar'),'spi_sha256':sha(O/'spi.jar'),'adapter_sha256':sha(O/'adapter.jar'),'kernel_tests_rerun':False,'public_oracle_decoder_enabled':False,'independent_review':'PENDING'},indent=2));print((O/'proof.json').read_text())
