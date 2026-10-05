"""Verify a clean committed product candidate; reuse immutable parser/adapter binaries."""
import hashlib,json,os,subprocess,sys,xml.etree.ElementTree as ET
from pathlib import Path
R=Path(__file__).resolve().parent.parent;P=R/'lab/out/streamhouse-oracle-gate-worktree';O=R/'lab/out/oracle-ack-pending-20261005';repair=sys.argv[1:]==['--resume-compile']
if repair:assert (O/'build.rc.txt').read_text()=='1'
else:O.mkdir(exist_ok=False)
name='build-r2' if repair else 'build'
assert not subprocess.check_output(['git','status','--porcelain'],cwd=P);commit=subprocess.check_output(['git','rev-parse','HEAD'],cwd=P).decode().strip()
env=dict(os.environ);env['JAVA_HOME']='D:/work/Java/jdk-17.0.7';env['PATH']=env['JAVA_HOME']+'/bin;'+env['PATH'];args=['cmd','/d','/c','D:/work/Java/apache-maven-3.9.11/bin/mvn.cmd','--batch-mode','-s',str(P/'.mvn/settings.xml'),'-f',str(P/'server/pom.xml'),'clean','verify'];(O/(name+'.command.json')).write_text(json.dumps(dict(args=args,commit=commit)))
with (O/(name+'.stdout.txt')).open('wb') as out,(O/(name+'.stderr.txt')).open('wb') as err:p=subprocess.run(args,env=env,cwd=P,stdout=out,stderr=err)
(O/(name+'.rc.txt')).write_text(str(p.returncode));assert p.returncode==0;assert not subprocess.check_output(['git','status','--porcelain'],cwd=P)
counts={k:0 for k in ['tests','failures','errors','skipped']};reports=[]
for f in sorted((P/'server').glob('*/target/surefire-reports/TEST-*.xml')):
 for k in counts:counts[k]+=int(ET.parse(f).getroot().get(k,0))
 reports.append(dict(path=f.relative_to(P).as_posix(),sha256=hashlib.sha256(f.read_bytes()).hexdigest()))
assert counts==dict(tests=728,failures=0,errors=0,skipped=0);jar=P/'server/streamhouse-app/target/streamhouse-app-0.1.0-SNAPSHOT.jar';(O/'build-proof.json').write_text(json.dumps(dict(commit=commit,tests=counts,app_sha256=hashlib.sha256(jar.read_bytes()).hexdigest(),reports=reports,kernel_tests_rerun=False),indent=2));print('ORACLE_ACK_PENDING_BUILD_VERIFIED|728')
