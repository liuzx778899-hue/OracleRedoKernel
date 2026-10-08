"""Once-only committed-source packaging for the D2 statistics normal-path integration."""
from pathlib import Path
import hashlib, json, os, shutil, subprocess, zipfile, sys

R=Path(__file__).resolve().parent.parent
P=R/'lab/out/streamhouse-oracle-gate-worktree'
usage=sys.argv[1:]==['--usage'];normal=sys.argv[1:]==['--normal'];assert not sys.argv[1:] or usage or normal
kind='normal' if normal else 'usage' if usage else 'statistics'
N=R/('lab/out/d2-'+kind+'-candidate-20261008')
assert not N.exists(), 'inspect failed step; never restart the complete build driver'
N.mkdir();shutil.copyfile(__file__,N/'executed-build.py')
env=dict(os.environ,JAVA_HOME='D:/work/Java/jdk-17.0.7');env['PATH']=env['JAVA_HOME']+'/bin;'+env['PATH']
mvn=['cmd','/d','/c','D:/work/Java/apache-maven-3.9.11/bin/mvn.cmd','--batch-mode','-s',str(P/'.mvn/settings.xml')]
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
def run(label,args,cwd=R):
    assert not (N/(label+'.rc')).exists()
    with (N/(label+'.log')).open('xb') as log:r=subprocess.run(list(map(str,args)),cwd=cwd,env=env,stdout=log,stderr=log,timeout=900)
    (N/(label+'.rc')).write_text(str(r.returncode));assert r.returncode==0,(label,r.returncode)
    print(label,'rc0',flush=True)
def install(label,file,group,artifact,version):
    run(label,mvn+['org.apache.maven.plugins:maven-install-plugin:3.1.2:install-file','-Dfile='+str(file),'-DgroupId='+group,'-DartifactId='+artifact,'-Dversion='+version,'-Dpackaging=jar','-DgeneratePom=true'])
run('archive-root',['git','archive','--format=zip','-o',N/'root-source.zip','HEAD'])
source=N/'root-source';source.mkdir()
with zipfile.ZipFile(N/'root-source.zip') as archive:archive.extractall(source)
run('kernel-package',mvn+['-DskipTests','package'],source)
shutil.copyfile(source/'target/oracle-redo-kernel-0.1.0-SNAPSHOT.jar',N/'kernel.jar')
kv='0.1.0-'+kind+'-'+sha(N/'kernel.jar')[:12]
install('install-kernel',N/'kernel.jar','com.streamhouse.oracle','oracle-redo-kernel',kv)
old=R/'lab/out/d3-raw-enrollment-candidate-20261008/artifacts/application.jar'
assert sha(old)=='c0d2ec93af275f56f462a92135c07371a0bed61422537aba56894a3f3a9e98d8'
with zipfile.ZipFile(old) as archive:
    entries=[n for n in archive.namelist() if n.startswith('BOOT-INF/lib/streamhouse-spi-') and n.endswith('.jar')]
    assert len(entries)==1;(N/'spi.jar').write_bytes(archive.read(entries[0]))
install('install-spi',N/'spi.jar','com.streamhouse','streamhouse-spi','0.1.0-oracle-evidence-ddff6ae')
adapter=source/'integration/streamhouse-engine-oracle'
run('adapter-package',mvn+['-Doracle.kernel.version='+kv,'-DskipTests','package'],adapter)
shutil.copyfile(adapter/'target/streamhouse-engine-oracle-0.1.0-SNAPSHOT.jar',N/'adapter.jar')
av='0.1.0-'+kind+'-'+sha(N/'adapter.jar')[:12]
install('install-adapter',N/'adapter.jar','com.streamhouse','streamhouse-engine-oracle',av)
pom=P/'server/streamhouse-app/pom.xml';text=pom.read_text(encoding='utf-8')
oldAdapter='0.1.0-usage-008d2a194be3' if normal else '0.1.0-statistics-a54946ae521b' if usage else '0.1.0-split-history-fbf1d30c8072'
oldKernel='0.1.0-usage-cbbe75b013c1' if normal else '0.1.0-statistics-2cb14d11abe7' if usage else '0.1.0-d3-raw-2690e584ae63'
assert text.count(oldAdapter)==1 and text.count(oldKernel)==1
pom.write_text(text.replace(oldAdapter,av).replace(oldKernel,kv),encoding='utf-8')
(N/'dependencies.json').write_text(json.dumps(dict(kernel=kv,adapter=av,kernelSha256=sha(N/'kernel.jar'),adapterSha256=sha(N/'adapter.jar')),indent=2))
print('DEPENDENCIES_READY; commit product pom, then package its committed tree',flush=True)
