"""Freeze the private V3 acquisition candidate and full reactor results per attempt."""
import hashlib,json,os,shutil,subprocess,sys,zipfile,xml.etree.ElementTree as ET
from pathlib import Path
R=Path(__file__).resolve().parent.parent;P=R/'lab/out/streamhouse-oracle-gate-worktree';N=R/'lab/out/v3-product-runtime-20261006'
N.mkdir(exist_ok=True);attempt=sys.argv[1] if len(sys.argv)>1 else 'build'
assert not (N/(attempt+'.command.json')).exists()
sha=lambda path:hashlib.sha256(path.read_bytes()).hexdigest()
shutil.copyfile(Path(__file__),N/('executed-'+attempt+'-driver.py'))
for path in [P/'server/streamhouse-app/src/main/java/com/streamhouse/app/baseline/OracleAcquisitionService.java',
    P/'server/streamhouse-app/src/main/java/com/streamhouse/app/web/OracleAcquisitionController.java',
    P/'server/streamhouse-app/src/test/java/com/streamhouse/app/baseline/OracleAcquisitionServiceTest.java']:
    shutil.copyfile(path,N/(attempt+'-'+path.name))
env=dict(os.environ,JAVA_HOME='D:/work/Java/jdk-17.0.7');env['PATH']=env['JAVA_HOME']+'/bin;'+env['PATH']
args=['cmd','/d','/c','D:/work/Java/apache-maven-3.9.11/bin/mvn.cmd','--batch-mode','-s',str(P/'.mvn/settings.xml'),'clean','package']
(N/(attempt+'.command.json')).write_text(json.dumps(dict(args=args,cwd=str(P/'server'),parent_commit=subprocess.check_output(['git','rev-parse','HEAD'],cwd=P).decode().strip())))
with (N/(attempt+'.stdout.txt')).open('wb') as out,(N/(attempt+'.stderr.txt')).open('wb') as err:
    result=subprocess.run(args,cwd=P/'server',env=env,stdout=out,stderr=err,timeout=900)
(N/(attempt+'.rc.txt')).write_text(str(result.returncode));assert result.returncode==0,attempt
shutil.copyfile(P/'server/streamhouse-app/target/streamhouse-app-0.1.0-SNAPSHOT.jar',N/'application.jar')
reports=N/(attempt+'-reports');reports.mkdir();total=[0,0,0,0]
for path in (P/'server').glob('*/target/surefire-reports/TEST-*.xml'):
    shutil.copyfile(path,reports/(path.parents[2].name+'-'+path.name));a=ET.parse(path).getroot().attrib
    for i,key in enumerate(['tests','failures','errors','skipped']):total[i]+=int(a[key])
assert total[0]>=750 and total[1:]==[0,0,0],total
libs={}
with zipfile.ZipFile(N/'application.jar') as jar:
    for artifact in ['oracle-redo-kernel','streamhouse-engine-oracle','streamhouse-spi']:
        paths=[p for p in jar.namelist() if p.startswith('BOOT-INF/lib/'+artifact+'-') and p.endswith('.jar')];assert len(paths)==1
        path=N/(artifact+'.jar');path.write_bytes(jar.read(paths[0]));libs[artifact]=dict(path=paths[0],sha256=sha(path))
assert libs['oracle-redo-kernel']['sha256']=='240c2250f00256209bf02ccb9b17918e90797c920e113ac552db2881c678c25c'
assert libs['streamhouse-engine-oracle']['sha256']=='65e0fcbad44f9df769db48fdf86ea66f13ab232d6c7872c25ee64c90a1d40dbe'
proof=dict(tests=total,product_sha256=sha(N/'application.jar'),embedded=libs,source_actions=0,target_actions=0,http_verified=False)
(N/(attempt+'-proof.json')).write_text(json.dumps(proof,indent=2));print(json.dumps(proof))
