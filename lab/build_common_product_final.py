"""Build and test the committed mixed product candidate once, preserving earlier failures."""
from pathlib import Path
import hashlib,json,os,shutil,subprocess,sys,zipfile
R=Path(__file__).resolve().parent.parent;BASE=R/'lab/out/common-fields-20261010';N=BASE/'product-build-r3'
W=Path('D:/work/vibecode/StreamHouse-wt/oracle-main-merge-20261010')
assert sys.argv[1:]==['--execute'];N.mkdir(exist_ok=False);shutil.copyfile(__file__,N/'executed.py')
candidate=json.loads((BASE/'product-build-r2/candidate.json').read_text())
env=dict(os.environ,JAVA_HOME='D:/work/Java/jdk-17.0.7');env['PATH']=env['JAVA_HOME']+'/bin;'+env['PATH']
def run(label,args,cwd):
    p=subprocess.run(list(map(str,args)),cwd=cwd,env=env,capture_output=True,timeout=1200)
    for suffix,value in [('stdout',p.stdout),('stderr',p.stderr),('rc',str(p.returncode).encode())]:(N/(label+'.'+suffix)).write_bytes(value)
    assert p.returncode==0,(label,p.returncode)
    return p.stdout.decode(errors='replace')
commit=run('head',['git','rev-parse','HEAD'],W).strip();assert commit.startswith('c8382f8')
assert not run('status',['git','status','--porcelain','--untracked-files=no'],W).strip()
run('test-package',['cmd','/d','/c','D:/work/Java/apache-maven-3.9.11/bin/mvn.cmd','-o','--batch-mode','-pl','streamhouse-app','-am','package'],W/'server')
app=W/'server/streamhouse-app/target/streamhouse-app-0.1.0-SNAPSHOT.jar'
with zipfile.ZipFile(app) as archive:
    for name,digest in [('oracle-redo-kernel-'+candidate['kernelVersion']+'.jar',candidate['kernelSha256']),('streamhouse-engine-oracle-'+candidate['adapterVersion']+'.jar',candidate['adapterSha256'])]:
        assert hashlib.sha256(archive.read('BOOT-INF/lib/'+name)).hexdigest()==digest
shutil.copyfile(app,N/'application.jar')
candidate.update(productCommit=commit,applicationSha256=hashlib.sha256(app.read_bytes()).hexdigest(),reactorTestsPassed=True)
(N/'candidate.json').write_text(json.dumps(candidate,indent=2));print('COMMITTED_MIXED_PRODUCT_BUILD_PASS',flush=True)
