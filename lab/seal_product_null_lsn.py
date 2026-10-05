"""Verify the exact reactor-tested gate class and seal product candidate evidence."""
import hashlib,json,subprocess
from pathlib import Path
R=Path(__file__).resolve().parent.parent;O=R/'lab/out/product-null-lsn-20261005';P=R/'lab/out/streamhouse-oracle-gate-worktree'
def sha(p):
    h=hashlib.sha256()
    with p.open('rb') as f:
        for b in iter(lambda:f.read(1024*1024),b''):h.update(b)
    return h.hexdigest()
proof=json.loads((O/'proof.json').read_text());assert proof['reactor_tests']==dict(tests=701,errors=0,failures=0,skipped=0)
assert subprocess.check_output(['git','rev-parse','HEAD'],cwd=P).decode().strip()==proof['product_commit']
modules=[P/'server'/n/'target/classes' for n in ['streamhouse-pipeline','streamhouse-spi','streamhouse-common']]
B=R/'lab/out/release-oracle-null-sides-20261005';OLD=R/'lab/out/oracle-product-gate-20261005'
cp=';'.join([str(p) for p in modules]+[str(O/'classes')]+[str(B/(n+'.jar')) for n in ['adapter','kernel']]+[str(p) for p in sorted((OLD/'product-libs').glob('*.jar'))])
for c in json.loads((R/'lab/out/oracle-null-sides-target-20261005/config.json').read_text()):
    n=c['prefix']+'-reactor-class-refusal';assert not (O/(n+'.rc.txt')).exists()
    args=['D:/work/Java/jdk-17.0.7/bin/java.exe','-cp',cp,'OracleProductGateProbe',str(R/'lab/out/oracle-null-sides-target-20261005'/c['prefix']/'baseline/snapshot.stdout.txt'),c['baseline_sha256'],c['leaf']]+c['captures']
    (O/(n+'.command.json')).write_text(json.dumps(args));p=subprocess.run(args,capture_output=True,timeout=240)
    for ext,v in [('stdout',p.stdout),('stderr',p.stderr)]:(O/(n+'.'+ext+'.txt')).write_bytes(v)
    (O/(n+'.rc.txt')).write_text(str(p.returncode))
    assert p.returncode==1 and not p.stdout and b'PRODUCT_GATE_REFUSED' in p.stderr and b'NullPointerException' not in p.stderr
    assert all(s in p.stderr for s in [b'EVENT_ID_INVALID',b'SOURCE_INCOMPLETE',b'LSN_MALFORMED'])
files=[]
for p in sorted(O.rglob('*')):
    if p.is_file():
        a=sha(p);assert a==sha(p);files.append({'path':p.relative_to(R).as_posix(),'bytes':p.stat().st_size,'sha256':a})
candidate=[]
for n in ['server/streamhouse-pipeline/src/main/java/com/streamhouse/pipeline/service/PublishGateService.java','server/streamhouse-pipeline/src/test/java/com/streamhouse/pipeline/service/PublishGateTest.java','server/streamhouse-pipeline/target/classes/com/streamhouse/pipeline/service/PublishGateService.class']:
    p=P/n;a=sha(p);assert a==sha(p);candidate.append({'path':n,'sha256':a})
manifest={'product':proof,'exact_reactor_gate_class_checked':True,'candidate_files':candidate,'files':files,'double_sha_equal':True,'sources':[{'path':'lab/'+n,'sha256':sha(R/'lab'/n)} for n in ['verify_product_null_lsn.py','seal_product_null_lsn.py']],'independent_review':'PENDING','public_oracle_decoder_enabled':False,'deployment_site_release_accepted':False}
(R/'docs/workorders/evidence/DELIVERY-product-null-lsn-20261005.json').write_text(json.dumps(manifest,indent=2)+'\n',encoding='utf-8');print(json.dumps({'files':len(files),'tested_commit':proof['product_commit'],'exact_candidate_checked':True}))
