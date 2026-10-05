"""Build the clean committed product tree; shared matrix endpoints are never used."""
import hashlib,json,os,subprocess,sys,xml.etree.ElementTree as ET
from pathlib import Path
R=Path(__file__).resolve().parent.parent;P=R/'lab/out/streamhouse-oracle-gate-worktree';O=R/'lab/out/oracle-facade-publication-20261005'
repair=False;state_repair=False
O.mkdir(parents=True,exist_ok=False)
assert not subprocess.check_output(['git','status','--porcelain'],cwd=P)
commit=subprocess.check_output(['git','rev-parse','HEAD'],cwd=P).decode().strip()
env=dict(os.environ);env['JAVA_HOME']='D:/work/Java/jdk-17.0.7';env['PATH']=env['JAVA_HOME']+'/bin;'+env['PATH']
args=['cmd','/d','/c','D:/work/Java/apache-maven-3.9.11/bin/mvn.cmd','--batch-mode','-s',str(P/'.mvn/settings.xml'),'-f',str(P/'server/pom.xml'),'clean','verify']
n='reactor-r3' if state_repair else 'reactor-r2' if repair else 'reactor'
assert not (O/(n+'.rc.txt')).exists()
(O/(n+'.command.json')).write_text(json.dumps({'args':args,'cwd':str(P),'commit':commit}))
with (O/(n+'.stdout.txt')).open('wb') as out,(O/(n+'.stderr.txt')).open('wb') as err:p=subprocess.run(args,cwd=P,env=env,stdout=out,stderr=err,timeout=900)
(O/(n+'.rc.txt')).write_text(str(p.returncode));assert p.returncode==0
counts={k:0 for k in ['tests','errors','failures','skipped']};reports=[]
for f in sorted((P/'server').glob('*/target/surefire-reports/TEST-*.xml')):
    for k in counts:counts[k]+=int(ET.parse(f).getroot().get(k,0))
    reports.append({'path':str(f.relative_to(P)),'sha256':hashlib.sha256(f.read_bytes()).hexdigest()})
assert counts==dict(tests=715,errors=0,failures=0,skipped=0)
(O/('build-proof-r3.json' if state_repair else 'build-proof-r2.json' if repair else 'build-proof.json')).write_text(json.dumps({'product_commit':commit,'tests':counts,'reports':reports,'shared_matrix_not_run':True,'oracle_public_decoder_enabled':False},indent=2));print(json.dumps({'commit':commit,'tests':counts}))
