"""Package the committed product with the immutable observed-record kernel; no source actions."""
from pathlib import Path
import os,subprocess,json,hashlib,shutil,zipfile,sys
R=Path(__file__).resolve().parent.parent;N=R/'lab/out/read-cut-20261010';P=R/'lab/out/streamhouse-oracle-gate-worktree'
core=json.loads((N/'core-e3ac633-candidate.json').read_text())
revision=sys.argv[1] if len(sys.argv)>1 else 'r3';assert revision in ('r3','r4')
label='continuation-package' if revision=='r3' else 'sealed-reclaim-package'
assert not (N/(label+'.rc')).exists()
assert not subprocess.check_output(['git','status','--porcelain'],cwd=P).strip()
commit=subprocess.check_output(['git','rev-parse','HEAD'],cwd=P).decode().strip()
env=dict(os.environ,JAVA_HOME='D:/work/Java/jdk-17.0.7');env['PATH']=env['JAVA_HOME']+'/bin;'+env['PATH']
p=subprocess.run(['cmd','/d','/c','D:/work/Java/apache-maven-3.9.11/bin/mvn.cmd','-o','-B','-pl','streamhouse-app','-am','-DskipTests','package'],cwd=P/'server',env=env,capture_output=True,timeout=900)
(N/(label+'.log')).write_bytes(p.stdout+p.stderr);(N/(label+'.rc')).write_text(str(p.returncode));assert p.returncode==0
app=P/'server/streamhouse-app/target/streamhouse-app-0.1.0-SNAPSHOT.jar'
with zipfile.ZipFile(app) as archive:
    names=[n for n in archive.namelist() if n.startswith('BOOT-INF/lib/oracle-redo-kernel-')];assert len(names)==1
    assert hashlib.sha256(archive.read(names[0])).hexdigest()==core['sha256']
shutil.copyfile(app,N/('application-'+revision+'.jar'))
candidate=dict(productCommit=commit,applicationSha256=hashlib.sha256(app.read_bytes()).hexdigest(),kernelSha256=core['sha256'])
(N/('candidate-'+revision+'.json')).write_text(json.dumps(candidate,indent=2));print(json.dumps(candidate))
