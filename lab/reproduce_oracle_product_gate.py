"""Compile actual product gate into isolated evidence; never modify the shared product checkout."""
import hashlib,json,subprocess,zipfile
from pathlib import Path
R=Path(__file__).resolve().parent.parent;P=Path('D:/work/vibecode/StreamHouse');O=R/'lab/out/oracle-product-gate-20261005';B=R/'lab/out/release-empty-foreign-begin-r2-20261005'
O.mkdir(parents=True,exist_ok=False);classes=O/'classes';classes.mkdir();libs=O/'product-libs';libs.mkdir();sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
boot=P/'server/streamhouse-app/target/streamhouse-app-0.1.0-SNAPSHOT.jar'
with zipfile.ZipFile(boot) as z:
    for n in z.namelist():
        if n.startswith('BOOT-INF/lib/') and n.endswith('.jar') and '/streamhouse-spi-' not in n:(libs/Path(n).name).write_bytes(z.read(n))
assert list(libs.glob('streamhouse-pipeline-*.jar'))
cp=';'.join([str(classes)]+[str(B/(n+'.jar')) for n in ['kernel','adapter','spi']]+[str(p) for p in sorted(libs.glob('*.jar'))])
def run(n,args,rc):
    args=list(map(str,args));(O/(n+'.command.json')).write_text(json.dumps(args));p=subprocess.run(args,capture_output=True,timeout=240)
    for ext,v in [('stdout',p.stdout),('stderr',p.stderr)]:(O/(n+'.'+ext+'.txt')).write_bytes(v)
    (O/(n+'.rc.txt')).write_text(str(p.returncode));assert p.returncode==rc,(n,p.stderr)
    return p
gate=P/'server/streamhouse-pipeline/src/main/java/com/streamhouse/pipeline/service/PublishGateService.java'
run('compile-actual-gate',['D:/work/Java/jdk-17.0.7/bin/javac.exe','-encoding','UTF-8','-cp',cp,'-d',classes,gate,R/'lab/OracleProductGateProbe.java'],0)
cases=[]
for name,command in [('nonnull','baseline-physical-scope-20261005/actual-r2.command.json'),('null','nullable-tail-20261005/actual.command.json'),('mixed','mixed-dml-capture-20261005/raw-complete-batch.command.json')]:
    a=json.loads((R/'lab/out'/command).read_text());i=a.index('CapturedBatchProbe')
    p=run(name+'-actual-product-gate',['D:/work/Java/jdk-17.0.7/bin/java.exe','-cp',cp,'OracleProductGateProbe']+a[i+1:],1)
    assert not p.stdout and b'NullPointerException' in p.stderr and b'EVENT_ID_INVALID' in p.stderr and b'SOURCE_INCOMPLETE' in p.stderr
    cases.append({'case':name,'rc':1,'stdout_bytes':0,'actual_gate_null_lsn_exception':True})
proof={'product_head':subprocess.check_output(['git','rev-parse','HEAD'],cwd=P).decode().strip(),'gate_actual_source_sha256':sha(gate),'compiled_actual_gate_sha256':sha(classes/'com/streamhouse/pipeline/service/PublishGateService.class'),'product_boot_dependencies_sha256':sha(boot),'cases':cases,'product_checkout_modified':False,'product_deployed':False,'new_source_or_target_actions':0,'full_product_accepted':False}
(O/'proof.json').write_text(json.dumps(proof,indent=2));print(json.dumps(proof))
