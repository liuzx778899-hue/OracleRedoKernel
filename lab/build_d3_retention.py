"""Build the changed product/agent once; reuse the trusted kernel and adapter."""
from pathlib import Path
import os,json,hashlib,subprocess,shutil,sys,zipfile
agent_resume=sys.argv[1:]==['--resume-agent'];resume=sys.argv[1:] in [['--resume-app'],['--resume-package'],['--resume-agent']];package_resume=sys.argv[1:]==['--resume-package'];assert resume or not sys.argv[1:]
R=Path(__file__).resolve().parent.parent;P=R/'lab/out/streamhouse-oracle-gate-worktree';N=R/'lab/out/d3-retention-20261010';N.mkdir(exist_ok=resume)
env=dict(os.environ,JAVA_HOME='D:/work/Java/jdk-17.0.7');env['PATH']=env['JAVA_HOME']+'/bin;'+env['PATH']
def run(name,args,cwd=R):
 if resume:name=('r4-' if agent_resume else 'r3-' if package_resume else 'r2-')+name
 assert not (N/(name+'.rc')).exists()
 p=subprocess.run(list(map(str,args)),cwd=cwd,env=env,capture_output=True,timeout=900)
 (N/(name+'.log')).write_bytes(p.stdout+p.stderr);(N/(name+'.rc')).write_text(str(p.returncode));assert p.returncode==0,(name,p.returncode)
 return p.stdout.decode('utf-8',errors='replace')
assert not run('product-clean',['git','status','--porcelain'],P).strip()
commit=run('product-head',['git','rev-parse','HEAD'],P).strip()
mvn=['cmd','/d','/c','D:/work/Java/apache-maven-3.9.11/bin/mvn.cmd','-o','-B']
if package_resume or agent_resume:assert (N/'r2-product-related-tests.rc').read_text()=='0'
else:run('product-related-tests',mvn+['-pl','streamhouse-app','-am','-DfailIfNoTests=false','-Dsurefire.failIfNoSpecifiedTests=false','-Dtest='+('OracleCommittedInputTest' if resume else 'OracleCommittedInputTest,TransferChunkServiceTest'),'test'],P/'server')
if agent_resume:assert (N/'r3-product-package.rc').read_text()=='0'
else:
 run('product-package',mvn+['-pl','streamhouse-app','-am','-DskipTests','package'],P/'server')
 app=P/'server/streamhouse-app/target/streamhouse-app-0.1.0-SNAPSHOT.jar';shutil.copyfile(app,N/'application.jar')
assert 'ork-product-runtime' not in run('containers',['docker','ps','-a','--format','{{.Names}}']).splitlines()
(N/'agent-build').mkdir(exist_ok=agent_resume)
script='''set -eu
cmake -S /source -B /build -DCMAKE_BUILD_TYPE=Release -DCMAKE_EXE_LINKER_FLAGS=-static -DCURL_LIBRARY_RELEASE=/usr/lib/libcurl.a -DCURL_INCLUDE_DIR=/usr/include "-DCMAKE_CXX_STANDARD_LIBRARIES=$(pkg-config --static --libs libcurl)"
cmake --build /build --target sh_agent test_chunk_uploader test_member_copy -j2
ctest --test-dir /build -R 'test_chunk_uploader|test_member_copy' --output-on-failure
'''
(N/'agent-build.sh').write_bytes(script.encode())
try:
 run('agent-build', ['docker','run','--name','ork-product-runtime','--label','owner=OracleRedoKernel','--mount','type=bind,source='+str(P/'agent')+',target=/source,readonly','--mount','type=bind,source='+str(N/'agent-build')+',target=/build','--mount','type=bind,source='+str(N/'agent-build.sh')+',target=/build.sh,readonly','--entrypoint','sh','ork-d3-musl-toolchain:20261008','/build.sh'])
finally:
 run('build-helper-logs',['docker','logs','ork-product-runtime']);run('build-helper-remove',['docker','rm','ork-product-runtime'])
shutil.copyfile(N/'agent-build/sh_agent',N/'sh_agent')
sha=lambda p:hashlib.sha256(Path(p).read_bytes()).hexdigest()
kernel='fff0550a36d7b19c75de2072d1660b326d4bec28834ebc3aaad1fa29577d95c1';adapter='4c619ca01918157e4172c767bdaa83b74b43168ac16527943425b358a635d0f2'
with zipfile.ZipFile(N/'application.jar') as archive:
 for prefix,digest in [('oracle-redo-kernel-',kernel),('streamhouse-engine-oracle-',adapter)]:
  names=[n for n in archive.namelist() if n.startswith('BOOT-INF/lib/'+prefix)];assert len(names)==1
  assert hashlib.sha256(archive.read(names[0])).hexdigest()==digest
product_commit=(N/'r3-product-head.log').read_text().strip() if agent_resume else commit
(N/'candidate.json').write_text(json.dumps(dict(productCommit=product_commit,agentCommit=commit,applicationSha256=sha(N/'application.jar'),agentSha256=sha(N/'sh_agent'),kernelSha256=kernel,adapterSha256=adapter),indent=2))
print((N/'candidate.json').read_text())
