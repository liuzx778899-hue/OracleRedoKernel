"""Install immutable allowed binary coordinates and verify the committed product tree."""
import hashlib,json,os,subprocess,sys,xml.etree.ElementTree as ET
from pathlib import Path
R=Path(__file__).resolve().parent.parent;P=R/'lab/out/streamhouse-oracle-gate-worktree';O=R/'lab/out/oracle-bounded-runtime-20261005';B=R/'lab/out/oracle-readonly-receipt-20261005'
preflight_repair=sys.argv[1:]==['--resume-ledger-preflight']
scope_repair=sys.argv[1:]==['--resume-source-order']
repair=sys.argv[1:]==['--resume-compile'] or scope_repair or preflight_repair
if not repair:O.mkdir(parents=True,exist_ok=False)
else:assert (O/'reactor.rc.txt').read_text()=='1'
assert not subprocess.check_output(['git','status','--porcelain'],cwd=P)
commit=subprocess.check_output(['git','rev-parse','HEAD'],cwd=P).decode().strip()
env=dict(os.environ);env['JAVA_HOME']='D:/work/Java/jdk-17.0.7';env['PATH']=env['JAVA_HOME']+'/bin;'+env['PATH']
mvn=['cmd','/d','/c','D:/work/Java/apache-maven-3.9.11/bin/mvn.cmd','--batch-mode']
def run(name,args,cwd):
    args=list(map(str,args));assert not (O/(name+'.rc.txt')).exists();(O/(name+'.command.json')).write_text(json.dumps(dict(args=args,cwd=str(cwd),product_commit=commit)))
    with (O/(name+'.stdout.txt')).open('wb') as out,(O/(name+'.stderr.txt')).open('wb') as err:p=subprocess.run(args,cwd=cwd,env=env,stdout=out,stderr=err,timeout=900)
    (O/(name+'.rc.txt')).write_text(str(p.returncode));assert p.returncode==0,name
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
prior=json.loads((B/'build-proof.json').read_text());artifacts=[]
for name,group,artifact,version in [('adapter','com.streamhouse','streamhouse-engine-oracle','0.1.0-readonly-8de94aae'),('kernel','com.streamhouse.oracle','oracle-redo-kernel','0.1.0-bounded-foreign-begin-r2')]:
    jar=B/(name+'.jar');assert sha(jar)==prior[name+'_sha256']
    existing=Path(os.environ['USERPROFILE'])/'.m2/repository'/group.replace('.','/')/artifact/version/(artifact+'-'+version+'.jar')
    if existing.exists():assert sha(existing)==sha(jar)
    pom=O/(name+'-binary.pom');pom.write_text('<project xmlns="http://maven.apache.org/POM/4.0.0"><modelVersion>4.0.0</modelVersion><groupId>'+group+'</groupId><artifactId>'+artifact+'</artifactId><version>'+version+'</version></project>')
    if not repair:run('install-'+name,mvn+['org.apache.maven.plugins:maven-install-plugin:3.1.1:install-file','-Dfile='+str(jar),'-DpomFile='+str(pom)],R)
    else:assert (O/('install-'+name+'.rc.txt')).read_text()=='0' and sha(existing)==sha(jar)
    artifacts.append(dict(group=group,artifact=artifact,version=version,sha256=sha(jar)))
run('reactor-r4' if preflight_repair else 'reactor-r3' if scope_repair else 'reactor-r2' if repair else 'reactor',mvn+['-s',P/'.mvn/settings.xml','-f',P/'server/pom.xml','clean','verify'],P)
counts={k:0 for k in ['tests','errors','failures','skipped']};reports=[]
for f in sorted((P/'server').glob('*/target/surefire-reports/TEST-*.xml')):
    for k in counts:counts[k]+=int(ET.parse(f).getroot().get(k,0))
    reports.append(dict(path=str(f.relative_to(P)),sha256=sha(f)))
assert counts==dict(tests=723,errors=0,failures=0,skipped=0)
(O/'build-proof.json').write_text(json.dumps(dict(product_commit=commit,tests=counts,reports=reports,binary_artifacts=artifacts,kernel_tests_rerun=False,shared_matrix_not_run=True,real_facade_accepted=False),indent=2));print(json.dumps(dict(commit=commit,tests=counts)))
