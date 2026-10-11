"""Pin committed binary artifacts in isolated product checkout; build the committed candidate once."""
from pathlib import Path
import hashlib,json,os,shutil,subprocess,sys,zipfile
R=Path(__file__).resolve().parent.parent;W=Path('D:/work/vibecode/StreamHouse-wt/oracle-main-merge-20261010')
N=R/'lab/out/array-temporal-product-20261011/build-r1'
prepare=sys.argv[1:]==['--prepare'];assert prepare or sys.argv[1:]==['--build']
sha=lambda p:hashlib.sha256(Path(p).read_bytes()).hexdigest()
env=dict(os.environ,JAVA_HOME='D:/work/Java/jdk-17.0.7');env['PATH']=env['JAVA_HOME']+'/bin;'+env['PATH']
mvn=['cmd','/d','/c','D:/work/Java/apache-maven-3.9.11/bin/mvn.cmd','-o','--batch-mode']
kernel='0.1.0-array-time-acf311f32006';adapter=kernel+'-ddf291e4186a'
if prepare:N.mkdir(parents=True,exist_ok=False);shutil.copyfile(__file__,N/'executed-prepare.py')
else:assert N.is_dir();shutil.copyfile(__file__,N/'executed-build.py')
def run(label,args,cwd):
    assert not (N/(label+'.rc')).exists()
    result=subprocess.run(list(map(str,args)),cwd=cwd,env=env,capture_output=True,timeout=1200)
    for suffix,value in [('stdout',result.stdout),('stderr',result.stderr),('rc',str(result.returncode).encode())]:(N/(label+'.'+suffix)).write_bytes(value)
    assert result.returncode==0,(label,result.returncode)
    return result.stdout.decode(errors='replace')
if prepare:
    assert run('kernel-head',['git','rev-parse','HEAD'],R).strip()=='aa28d23c72350a86bb8252e0861f6f9ef10da014'
    assert not run('kernel-status',['git','status','--porcelain','--untracked-files=no'],R).strip()
    assert run('product-base',['git','rev-parse','HEAD'],W).strip()=='dc593c603a6f9c6336a1d1cb17ff026205eb37f3'
    assert not run('product-status',['git','status','--porcelain','--untracked-files=no'],W).strip()
    shutil.copyfile(R/'target/oracle-redo-kernel-0.1.0-SNAPSHOT.jar',N/'kernel.jar')
    shutil.copyfile(R/'integration/streamhouse-engine-oracle/target/streamhouse-engine-oracle-0.1.0-SNAPSHOT.jar',N/'adapter.jar')
    assert sha(N/'kernel.jar')=='acf311f32006846d854846817eaec8ddb574ac7579f557634188ae1d7c211841'
    assert sha(N/'adapter.jar')=='ddf291e4186a00f3ab14e474d74b6125443cf6c1e69423b84a33cd1fe7722900'
    run('install-kernel',mvn+['org.apache.maven.plugins:maven-install-plugin:3.1.2:install-file','-Dfile='+str(N/'kernel.jar'),'-DgroupId=com.streamhouse.oracle','-DartifactId=oracle-redo-kernel','-Dversion='+kernel,'-Dpackaging=jar','-DgeneratePom=true'],R)
    pom=(R/'integration/streamhouse-engine-oracle/pom.xml').read_text(encoding='utf-8').replace('<version>0.1.0-SNAPSHOT</version>','<version>'+adapter+'</version>',1).replace('<oracle.kernel.version>0.1.0-SNAPSHOT</oracle.kernel.version>','<oracle.kernel.version>'+kernel+'</oracle.kernel.version>')
    (N/'adapter.pom').write_text(pom,encoding='utf-8')
    run('install-adapter',mvn+['org.apache.maven.plugins:maven-install-plugin:3.1.2:install-file','-Dfile='+str(N/'adapter.jar'),'-DpomFile='+str(N/'adapter.pom')],R)
    path=W/'server/streamhouse-app/pom.xml';old=path.read_bytes();(N/'product-original.pom').write_bytes(old)
    assert old.count(b'0.1.0-common-3a9ccc651b60-87eb1e9064de')==1 and old.count(b'0.1.0-common-3a9ccc651b60</version>')==1
    path.write_bytes(old.replace(b'0.1.0-common-3a9ccc651b60-87eb1e9064de',adapter.encode()).replace(b'0.1.0-common-3a9ccc651b60</version>',(kernel+'</version>').encode()))
else:
    assert not run('build-status',['git','status','--porcelain','--untracked-files=no'],W).strip()
    commit=run('build-head',['git','rev-parse','HEAD'],W).strip()
    text=(W/'server/streamhouse-app/pom.xml').read_text(encoding='utf-8');assert adapter in text and kernel+'</version>' in text
    run('reactor',mvn+['package'],W/'server')
    shutil.copyfile(W/'server/streamhouse-app/target/streamhouse-app-0.1.0-SNAPSHOT.jar',N/'application.jar')
    with zipfile.ZipFile(N/'application.jar') as archive:
        for name,version in [('kernel',kernel),('adapter',adapter)]:
            artifact='oracle-redo-kernel' if name=='kernel' else 'streamhouse-engine-oracle'
            assert hashlib.sha256(archive.read('BOOT-INF/lib/'+artifact+'-'+version+'.jar')).hexdigest()==sha(N/(name+'.jar'))
    (N/'candidate.json').write_text(json.dumps(dict(productCommit=commit,kernelVersion=kernel,adapterVersion=adapter,kernelSha256=sha(N/'kernel.jar'),adapterSha256=sha(N/'adapter.jar'),applicationSha256=sha(N/'application.jar'),productRuntimeVerified=False),indent=2),encoding='utf-8')
print('PREPARED' if prepare else 'COMMITTED_PRODUCT_BUILT',flush=True)
