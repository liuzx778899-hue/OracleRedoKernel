"""Build the isolated committed product tree and test actual frozen Oracle gate refusals."""
import hashlib,json,os,subprocess,xml.etree.ElementTree as ET
from pathlib import Path
R=Path(__file__).resolve().parent.parent
P=R/'lab/out/streamhouse-oracle-gate-worktree'
O=R/'lab/out/product-null-lsn-20261005'
B=R/'lab/out/release-oracle-null-sides-20261005'
OLD=R/'lab/out/oracle-product-gate-20261005'
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
O.mkdir(parents=True,exist_ok=False)
assert not subprocess.check_output(['git','status','--porcelain'],cwd=P)
commit=subprocess.check_output(['git','rev-parse','HEAD'],cwd=P).decode().strip()
env=dict(os.environ);env['JAVA_HOME']='D:/work/Java/jdk-17.0.7';env['PATH']=env['JAVA_HOME']+'/bin;'+env['PATH']
def run(n,args,cwd=R,rc=0):
    args=list(map(str,args));(O/(n+'.command.json')).write_text(json.dumps({'args':args,'cwd':str(cwd),'product_commit':commit}))
    p=subprocess.run(args,cwd=cwd,env=env,capture_output=True,timeout=900)
    for ext,v in [('stdout',p.stdout),('stderr',p.stderr)]:(O/(n+'.'+ext+'.txt')).write_bytes(v)
    (O/(n+'.rc.txt')).write_text(str(p.returncode));assert p.returncode==rc,(n,p.returncode)
    return p
run('reactor-test',['cmd','/d','/c','D:/work/Java/apache-maven-3.9.11/bin/mvn.cmd','--batch-mode','-s',P/'.mvn/settings.xml','-f',P/'server/pom.xml','test'],P)
counts={k:0 for k in ['tests','errors','failures','skipped']};reports=[]
for f in sorted((P/'server').glob('*/target/surefire-reports/TEST-*.xml')):
    root=ET.parse(f).getroot();reports.append({'path':str(f.relative_to(P)),'sha256':sha(f)})
    for k in counts:counts[k]+=int(root.get(k,0))
assert counts['tests']>0 and counts['errors']==counts['failures']==0
classes=O/'classes';classes.mkdir()
cp=';'.join([str(classes)]+[str(B/(n+'.jar')) for n in ['adapter','kernel','spi']]+[str(p) for p in sorted((OLD/'product-libs').glob('*.jar'))])
gate=P/'server/streamhouse-pipeline/src/main/java/com/streamhouse/pipeline/service/PublishGateService.java'
run('compile-actual-gate',['D:/work/Java/jdk-17.0.7/bin/javac.exe','-encoding','UTF-8','-cp',cp,'-d',classes,gate,R/'lab/OracleProductGateProbe.java'])
cases=[]
for c in json.loads((R/'lab/out/oracle-null-sides-target-20261005/config.json').read_text()):
    args=[str(R/'lab/out/oracle-null-sides-target-20261005'/c['prefix']/'baseline/snapshot.stdout.txt'),c['baseline_sha256'],c['leaf']]+c['captures']
    p=run(c['prefix']+'-controlled-gate-refusal',['D:/work/Java/jdk-17.0.7/bin/java.exe','-cp',cp,'OracleProductGateProbe']+args,rc=1)
    assert not p.stdout and b'PRODUCT_GATE_REFUSED' in p.stderr and b'NullPointerException' not in p.stderr
    assert all(reason in p.stderr for reason in [b'LSN_MALFORMED',b'EVENT_ID_INVALID',b'SOURCE_INCOMPLETE'])
    cases.append({'case':c['prefix'],'rc':1,'stdout_bytes':0,'controlled_refusal':True})
assert not subprocess.check_output(['git','status','--porcelain'],cwd=P)
(O/'proof.json').write_text(json.dumps({'product_commit':commit,'gate_source_sha256':sha(gate),'reactor_tests':counts,'test_reports':reports,'real_frozen_gate_cases':cases,'shared_checkout_changed':False,'oracle_production_gate_enabled':False,'independent_review':'PENDING','deployment_site_release_accepted':False},indent=2))
print(json.dumps({'commit':commit,'tests':counts,'cases':cases}))
