"""Targeted development candidate only; no deployment or source actions."""
from pathlib import Path
import hashlib,json,os,re,shutil,subprocess,sys

R=Path(__file__).resolve().parent.parent
N=R/'lab/out/common-fields-20261010'
if sys.argv[1:]:
    assert len(sys.argv)==2 and re.fullmatch(r'candidate-r[1-9][0-9]*',sys.argv[1]) and int(sys.argv[1].split('-r')[1])>=2
    N=N/sys.argv[1]
A=R/'integration/streamhouse-engine-oracle'
N.mkdir(exist_ok=not sys.argv[1:])
assert not (N/'executed-build.py').exists()
shutil.copyfile(__file__,N/'executed-build.py')
env=dict(os.environ,JAVA_HOME='D:/work/Java/jdk-17.0.7')
env['PATH']=env['JAVA_HOME']+'/bin;'+env['PATH']
mvn=['cmd','/d','/c','D:/work/Java/apache-maven-3.9.11/bin/mvn.cmd','-o','--batch-mode']

def run(label,args,cwd):
    assert not (N/(label+'.rc')).exists()
    p=subprocess.run(list(map(str,args)),cwd=cwd,env=env,capture_output=True,timeout=600)
    for suffix,value in [('stdout',p.stdout),('stderr',p.stderr),('rc',str(p.returncode).encode())]:
        (N/(label+'.'+suffix)).write_bytes(value)
    assert p.returncode==0,(label,p.returncode)

sha=lambda p:hashlib.sha256(Path(p).read_bytes()).hexdigest()
run('mixed-scope',mvn+['-Dtest=MixedLobScopeTest,HistoricalSchemaBarrierTest,LobBitmapUndoPairTest,HeapRowPatchTest,SecureFileLobControlUndoPairTest,TransactionMarkersTest','package'],R)
shutil.copyfile(R/'target/oracle-redo-kernel-0.1.0-SNAPSHOT.jar',N/'kernel.jar')
version='0.1.0-common-'+sha(N/'kernel.jar')[:12]
run('install-kernel',mvn+['org.apache.maven.plugins:maven-install-plugin:3.1.2:install-file','-Dfile='+str(N/'kernel.jar'),
    '-DgroupId=com.streamhouse.oracle','-DartifactId=oracle-redo-kernel','-Dversion='+version,'-Dpackaging=jar','-DgeneratePom=true'],R)
run('adapter',mvn+['-Doracle.kernel.version='+version,
    '-Dtest=OracleSnapshotAcquirerTest,OracleLobValueTest,OracleBatchProjectionTest,OracleTemporalProjectionTest','package'],A)
shutil.copyfile(A/'target/streamhouse-engine-oracle-0.1.0-SNAPSHOT.jar',N/'adapter.jar')
(N/'development-candidate.json').write_text(json.dumps(dict(kernelVersion=version,kernelSha256=sha(N/'kernel.jar'),
    adapterSha256=sha(N/'adapter.jar'),newMixedRawVerified=False,productIntegrated=False),indent=2))
print('DEVELOPMENT_CANDIDATE',flush=True)
