"""Install new immutable adapter and verify the committed product candidate; retain every attempt."""
import hashlib,json,os,subprocess,sys,xml.etree.ElementTree as ET
from pathlib import Path
R=Path(__file__).resolve().parent.parent;P=R/'lab/out/streamhouse-oracle-gate-worktree';A=R/'lab/out/oracle-snapshot-acquisition-20261005';O=R/'lab/out/oracle-acquisition-controller-20261005'
O.mkdir(exist_ok=True);attempt=sys.argv[1] if len(sys.argv)>1 else 'build';assert not (O/(attempt+'.command.json')).exists()
env=dict(os.environ);env['JAVA_HOME']='D:/work/Java/jdk-17.0.7';env['PATH']=env['JAVA_HOME']+'/bin;'+env['PATH']
prefix=['cmd','/d','/c','D:/work/Java/apache-maven-3.9.11/bin/mvn.cmd','--batch-mode','-s',str(P/'.mvn/settings.xml')]
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
assert sha(A/'adapter.jar')=='50feffa8494a9225e48a8886752b071918e9ae50d3373c0952c6add398cb3b35'
if not (O/'install.rc.txt').exists():
 args=prefix+['org.apache.maven.plugins:maven-install-plugin:3.1.1:install-file','-Dfile='+str(A/'adapter.jar'),'-DgroupId=com.streamhouse','-DartifactId=streamhouse-engine-oracle','-Dversion=0.1.0-source-snapshot-50feffa8','-Dpackaging=jar','-DgeneratePom=true']
 with (O/'install.stdout.txt').open('wb') as out,(O/'install.stderr.txt').open('wb') as err:p=subprocess.run(args,cwd=P,env=env,stdout=out,stderr=err)
 (O/'install.rc.txt').write_text(str(p.returncode));assert p.returncode==0
assert not subprocess.check_output(['git','status','--porcelain'],cwd=P)
commit=subprocess.check_output(['git','rev-parse','HEAD'],cwd=P).decode().strip()
args=prefix+['-f',str(P/'server/pom.xml'),'clean','verify']
(O/(attempt+'.command.json')).write_text(json.dumps(dict(args=args,commit=commit,adapter_sha256=sha(A/'adapter.jar'))))
with (O/(attempt+'.stdout.txt')).open('wb') as out,(O/(attempt+'.stderr.txt')).open('wb') as err:p=subprocess.run(args,cwd=P,env=env,stdout=out,stderr=err)
(O/(attempt+'.rc.txt')).write_text(str(p.returncode));assert p.returncode==0
counts={k:0 for k in ['tests','failures','errors','skipped']};reports=[]
for f in sorted((P/'server').glob('*/target/surefire-reports/TEST-*.xml')):
 for k in counts:counts[k]+=int(ET.parse(f).getroot().get(k,0))
 reports.append(dict(path=f.relative_to(P).as_posix(),sha256=sha(f)))
assert counts['tests']>=733 and not any(counts[k] for k in ['failures','errors','skipped'])
jar=P/'server/streamhouse-app/target/streamhouse-app-0.1.0-SNAPSHOT.jar'
(O/(attempt+'-proof.json')).write_text(json.dumps(dict(commit=commit,tests=counts,app_sha256=sha(jar),adapter_sha256=sha(A/'adapter.jar'),reports=reports,kernel_tests_rerun=False),indent=2))
print('ORACLE_ACQUISITION_CONTROLLER_BUILD_VERIFIED|'+str(counts['tests']))
