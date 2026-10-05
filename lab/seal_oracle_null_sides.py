"""Seal gate reproduction, adapter and new target verification with two streaming reads."""
import hashlib,json,subprocess
from pathlib import Path
R=Path(__file__).resolve().parent.parent
roots=[R/'lab/out'/n for n in ['oracle-product-gate-20261005','release-oracle-null-sides-20261005','oracle-null-sides-target-20261005']]
def sha(p):
    h=hashlib.sha256()
    with p.open('rb') as f:
        for b in iter(lambda:f.read(1024*1024),b''):h.update(b)
    return h.hexdigest()
O=roots[-1];assert (O/'sides-proof.json').exists()
gate=json.loads((roots[0]/'proof.json').read_text())
assert sha(roots[0]/'classes/com/streamhouse/pipeline/service/PublishGateService.class')==gate['compiled_actual_gate_sha256']
cp=';'.join([str(roots[0]/'classes')]+[str(roots[1]/(n+'.jar')) for n in ['adapter','kernel','spi']]+[str(p) for p in sorted((roots[0]/'product-libs').glob('*.jar'))])
for c in json.loads((O/'config.json').read_text()):
    n=c['prefix']+'-new-adapter-product-gate'
    args=['D:/work/Java/jdk-17.0.7/bin/java.exe','-cp',cp,'OracleProductGateProbe',str(O/c['prefix']/'baseline/snapshot.stdout.txt'),c['baseline_sha256'],c['leaf']]+c['captures']
    assert not (O/(n+'.rc.txt')).exists()
    (O/(n+'.command.json')).write_text(json.dumps(args))
    p=subprocess.run(args,capture_output=True,timeout=240)
    for ext,v in [('stdout',p.stdout),('stderr',p.stderr)]:(O/(n+'.'+ext+'.txt')).write_bytes(v)
    (O/(n+'.rc.txt')).write_text(str(p.returncode))
    assert p.returncode==1 and not p.stdout and all(s in p.stderr for s in [b'NullPointerException',b'EVENT_ID_INVALID',b'SOURCE_INCOMPLETE'])
files=[];commands=[]
for root in roots:
    for p in sorted(root.rglob('*')):
        if not p.is_file() or any(n in p.relative_to(root).parts for n in ['m2','product-libs']):continue
        assert 'secret' not in p.name.lower()
        a=sha(p);assert a==sha(p)
        files.append({'path':p.relative_to(R).as_posix(),'bytes':p.stat().st_size,'sha256':a})
    for p in sorted(root.glob('*.rc.txt')):
        n=p.name[:-7];commands.append({'path':p.relative_to(R).as_posix(),'rc':int(p.read_text()),'stdout_bytes':(root/(n+'.stdout.txt')).stat().st_size,'stderr_bytes':(root/(n+'.stderr.txt')).stat().st_size})
sources=['integration/streamhouse-engine-oracle/src/'+n for n in ['main/java/com/streamhouse/engines/oracle/OracleBatchProjection.java','main/java/com/streamhouse/engines/oracle/OracleTargetBatchWriter.java','test/java/com/streamhouse/engines/oracle/OracleBatchProjectionTest.java']]+['lab/'+n for n in ['OracleProjectionProbe.java','OracleEnvelopeSidesProbe.java','OracleProductGateProbe.java','build_oracle_null_sides.py','reproduce_oracle_product_gate.py','verify_oracle_null_sides.py','seal_oracle_null_sides.py']]
manifest={'prior_commit':subprocess.check_output(['git','rev-parse','HEAD'],cwd=R).decode().strip(),'gate_reproduction':json.loads((roots[0]/'proof.json').read_text()),'release':json.loads((roots[1]/'proof.json').read_text()),'sides':json.loads((O/'sides-proof.json').read_text()),'target':json.loads((O/'target-proof.json').read_text()),'independent':json.loads((O/'independent-proof.json').read_text()),'sources':[{'path':n,'actual_source_sha256':sha(R/n)} for n in sources],'files':files,'commands':commands,'sha256_checked_twice':True,'full_product_site_release_accepted':False}
(R/'docs/workorders/evidence/DELIVERY-oracle-null-sides-20261005.json').write_text(json.dumps(manifest,indent=2)+'\n',encoding='utf-8')
print(json.dumps({'files':len(files),'commands':len(commands),'double_sha_equal':True}))
