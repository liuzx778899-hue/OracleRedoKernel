"""Own three-dialect new/upgrade registry matrix with real JDBC/MyBatis. Shared endpoints never selected."""
import hashlib,json,shutil,subprocess,sys,zipfile
from pathlib import Path
R=Path(__file__).resolve().parent.parent;P=R/'lab/out/streamhouse-oracle-gate-worktree';O=R/'lab/out/oracle-registry-20261005';B=R/'lab/out/release-oracle-typed-evidence-20261005'
repair=sys.argv[1:]==['--resume-migration']
assert (O/('build-proof-r2.json' if repair else 'build-proof.json')).exists()
if repair:assert (O/'matrix.rc.txt').read_text()=='1'
else:assert not (O/'matrix.rc.txt').exists()
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
def run(n,args):
    args=list(map(str,args));assert not (O/(n+'.rc.txt')).exists();(O/(n+'.command.json')).write_text(json.dumps(args))
    with (O/(n+'.stdout.txt')).open('wb') as out,(O/(n+'.stderr.txt')).open('wb') as err:p=subprocess.run(args,stdout=out,stderr=err,timeout=900)
    (O/(n+'.rc.txt')).write_text(str(p.returncode));assert p.returncode==0,n
proof=json.loads((B/'proof.json').read_text())
for n in ['kernel','adapter']:
    assert sha(B/(n+'.jar'))==proof[n+'_sha256']
    if not repair:shutil.copyfile(B/(n+'.jar'),O/(n+'.jar'))
    else:assert sha(O/(n+'.jar'))==proof[n+'_sha256']
libs=O/('libs-r2' if repair else 'libs');libs.mkdir()
boot=P/'server/streamhouse-app/target/streamhouse-app-0.1.0-SNAPSHOT.jar'
with zipfile.ZipFile(boot) as z:
    for n in z.namelist():
        if n.startswith('BOOT-INF/lib/') and n.endswith('.jar') and not Path(n).name.startswith('streamhouse-'):(libs/Path(n).name).write_bytes(z.read(n))
classes=O/('classes-r2' if repair else 'classes');classes.mkdir();modules=[d.name for d in (P/'server').iterdir() if (d/'target/classes').exists()]
cp=';'.join([str(classes)]+[str(P/'server'/d/'target/classes') for d in modules]+[str(O/(n+'.jar')) for n in ['adapter','kernel']]+[str(libs/'*')])
run('compile-matrix-r2' if repair else 'compile-matrix',['D:/work/Java/jdk-17.0.7/bin/javac.exe','-encoding','UTF-8','-cp',cp,'-d',classes,R/'lab/OracleRegistryDbProbe.java'])
configs=json.loads((R/'lab/out/oracle-null-sides-target-20261005/config.json').read_text())
for c in configs:
    c['baseline_path']='/frozen/'+c['prefix']+'/baseline/snapshot.stdout.txt'
    for i in range(0,len(c['captures']),2):c['captures'][i]='/frozen/'+c['prefix']+'/'+Path(c['captures'][i]).name
empty=json.loads((R/'lab/out/oracle-empty-target-20261005/inspect.command.json').read_text());a=empty[empty.index('OracleDmlCheckpointWorker')+1:]
configs.append(dict(prefix='EMPT',baseline_path='/empty/baseline/snapshot.stdout.txt',baseline_sha256=a[5],leaf=a[6],captures=['/empty/sequence-51.oracap',a[9]]))
(O/'configs.json').write_text(json.dumps(configs))
linuxcp=':'.join(['/evidence/'+classes.name]+['/product/'+d+'/target/classes' for d in modules]+['/evidence/adapter.jar','/evidence/kernel.jar','/evidence/'+libs.name+'/*'])
args=['docker','run','--rm','--label','owner=OracleRedoKernel','--label','scope=oracle-registry-matrix','--user','0','--network','container:oraredo-19c-lab-20261004','--mount','type=bind,source='+str(O)+',target=/evidence','--mount','type=bind,source='+str(P/'server')+',target=/product,readonly','--mount','type=bind,source='+str(R/'lab/out/oracle-null-sides-target-20261005')+',target=/frozen,readonly','--mount','type=bind,source='+str(R/'lab/out/oracle-empty-target-20261005')+',target=/empty,readonly','--entrypoint','java','flink:1.18-java17','-Xmx1536m','-cp',linuxcp,'OracleRegistryDbProbe','/evidence/control.secret.json','/evidence/configs.json','/evidence/matrix-proof.json']
if repair:args.append('--resume-migration')
run('matrix-r2' if repair else 'matrix',args)
results=json.loads((O/'matrix-proof.json').read_text());assert len(results)==24
assert all(r['guard_and_typed_binding_matched'] and r['real_refusal_persisted'] and r['ledger_written']==0 for r in results)
print('OWNED_THREE_DIALECT_REGISTRY_VERIFIED|24')
