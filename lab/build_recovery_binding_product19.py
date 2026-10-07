"""Consume unchanged adapter/SPI and immutable unified-recovery kernel in private product."""
import hashlib,json,os,shutil,subprocess,sys,zipfile
import xml.etree.ElementTree as ET
from pathlib import Path
R=Path(__file__).resolve().parent.parent;P=R/'lab/out/streamhouse-oracle-gate-worktree';N=R/'lab/out/product-recovery-binding-20261007';OLD=R/'lab/out/split-history-product-20261007'
version='0.1.0-unified-recovery-593e730b67a2'
def sha(p):
    with p.open('rb') as f:return hashlib.file_digest(f,'sha256').hexdigest()
env=dict(os.environ,JAVA_HOME='D:/work/Java/jdk-17.0.7');env['PATH']=env['JAVA_HOME']+'/bin;'+env['PATH']
mvn=['cmd','/d','/c','D:/work/Java/apache-maven-3.9.11/bin/mvn.cmd','--batch-mode']
def run(name,args,cwd):
    assert not (N/(name+'.command.json')).exists();(N/(name+'.command.json')).write_text(json.dumps(dict(args=args,cwd=str(cwd),commit=subprocess.check_output(['git','rev-parse','HEAD'],cwd=cwd).decode().strip())))
    with (N/(name+'.stdout.txt')).open('wb') as out,(N/(name+'.stderr.txt')).open('wb') as err:p=subprocess.run(args,cwd=cwd,env=env,stdout=out,stderr=err,timeout=900)
    (N/(name+'.rc.txt')).write_text(str(p.returncode));assert p.returncode==0,(name,p.returncode)
if sys.argv[1]=='prepare':
    assert not N.exists();N.mkdir();shutil.copyfile(R/'lab/out/unified-recovery-20261007/kernel.jar',N/'kernel.jar')
    assert sha(N/'kernel.jar')=='593e730b67a2622ac9084eeae837eb64477249efe1946f4c5e4856059fe39ea9'
    for name in ['adapter.jar','spi.jar']:shutil.copyfile(OLD/name,N/name)
    assert sha(N/'adapter.jar')=='fbf1d30c807200faa30034ba31e184bb7467788dc216e79121b5ccc3d586816b'
    run('install-kernel',mvn+['org.apache.maven.plugins:maven-install-plugin:3.1.2:install-file','-Dfile='+str(N/'kernel.jar'),'-DgroupId=com.streamhouse.oracle','-DartifactId=oracle-redo-kernel','-Dversion='+version,'-Dpackaging=jar','-DgeneratePom=true'],R)
    path=P/'server/streamhouse-app/pom.xml';raw=path.read_bytes();old=b'0.1.0-split-history-a84db1a3ff8f';assert raw.count(old)==1;path.write_bytes(raw.replace(old,version.encode()))
    shutil.copyfile(__file__,N/'executed-build-driver.py');print('IMMUTABLE_RECOVERY_KERNEL_PREPARED_ADAPTER_UNCHANGED')
elif sys.argv[1]=='product':
    assert not subprocess.check_output(['git','status','--porcelain'],cwd=P).strip(),'private tree dirty'
    run('product-build',mvn+['-s',str(P/'.mvn/settings.xml'),'clean','package'],P/'server')
    dest=N/'product-reports';dest.mkdir();counts=[0,0,0,0]
    for path in (P/'server').rglob('target/surefire-reports/TEST-*.xml'):
        shutil.copyfile(path,dest/(path.parents[2].name+'-'+path.name));attrs=ET.parse(path).getroot().attrib
        for i,name in enumerate(['tests','failures','errors','skipped']):counts[i]+=int(attrs[name])
    assert counts[0]>=754 and counts[1:]==[0,0,0],counts
    shutil.copyfile(P/'server/streamhouse-app/target/streamhouse-app-0.1.0-SNAPSHOT.jar',N/'application.jar');embedded={}
    with zipfile.ZipFile(N/'application.jar') as app:
        for name,expected in [('oracle-redo-kernel','kernel'),('streamhouse-engine-oracle','adapter'),('streamhouse-spi','spi')]:
            members=[p for p in app.namelist() if p.startswith('BOOT-INF/lib/'+name+'-') and p.endswith('.jar')];assert len(members)==1
            path=N/('embedded-'+name+'.jar');path.write_bytes(app.read(members[0]))
            if expected!='spi':assert sha(path)==sha(N/(expected+'.jar'))
            else:
                with zipfile.ZipFile(path) as actual,zipfile.ZipFile(N/'spi.jar') as reference:
                    for member in reference.namelist():
                        if member.endswith('.class'):assert actual.read(member)==reference.read(member)
            embedded[name]=dict(path=members[0],sha256=sha(path))
    proof=dict(tests=counts,product_sha256=sha(N/'application.jar'),kernel_version=version,embedded=embedded,spi_all_classes_unchanged=True,adapter_unchanged=True,http_recovery_verified=False,target_recovery_verified=False,source_actions=0,target_actions=0)
    (N/'product-proof.json').write_text(json.dumps(proof,indent=2));print(json.dumps(proof))
else:raise ValueError(sys.argv[1])
