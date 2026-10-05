"""Freeze accepted inputs and inspect new adapter before any owned target write."""
import hashlib,json,shutil,subprocess
from pathlib import Path
R=Path(__file__).resolve().parent.parent;O=R/'lab/out/oracle-dml-target-20261005';B=R/'lab/out/release-oracle-dml-20261005'
O.mkdir(parents=True,exist_ok=False)
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
def run(n,args,rc=0):
    args=list(map(str,args));(O/(n+'.command.json')).write_text(json.dumps(args))
    p=subprocess.run(args,capture_output=True,timeout=180)
    for ext,v in [('stdout',p.stdout),('stderr',p.stderr)]:(O/(n+'.'+ext+'.txt')).write_bytes(v)
    (O/(n+'.rc.txt')).write_text(str(p.returncode));assert p.returncode==rc,n
    return p.stdout
proof=json.loads((B/'proof.json').read_text())
for n in ['kernel','spi','adapter']:
    assert sha(B/(n+'.jar'))==proof[n+'_sha256'];shutil.copyfile(B/(n+'.jar'),O/(n+'.jar'))
T=R/'lab/out/oracle-target-20261005';shutil.copyfile(T/'ojdbc8.jar',O/'ojdbc8.jar')
pins={f['path']:f['sha256'] for f in json.loads((R/'docs/workorders/evidence/DELIVERY-oracle-target-20261005.json').read_text())['files']}
assert sha(O/'ojdbc8.jar')==pins[(T/'ojdbc8.jar').relative_to(R).as_posix()]
classes=O/'classes';classes.mkdir();cp=';'.join(str(O/(n+'.jar')) for n in ['adapter','kernel','spi','ojdbc8'])
run('compile',['D:/work/Java/jdk-17.0.7/bin/javac.exe','-encoding','UTF-8','-cp',cp,'-d',classes,R/'lab/OracleDmlCheckpointWorker.java',R/'lab/OracleProjectionProbe.java'])
groups=[('DNON','baseline-physical-scope-20261005/actual-r2.command.json','baseline-physical-scope-20261005/actual-r2.stdout.txt','dml-seed'),('DNUL','nullable-tail-20261005/actual.command.json','nullable-tail-20261005/actual.stdout.txt','null-seed'),('DMIX','mixed-dml-capture-20261005/raw-complete-batch.command.json','mixed-dml-capture-20261005/raw-complete-batch.stdout.txt','mixed-seed')]
configs=[]
for prefix,command,expected,seed in groups:
    cmd=json.loads((R/'lab/out'/command).read_text());i=cmd.index('CapturedBatchProbe');a=cmd[i+1:]
    d=O/prefix;d.mkdir();shutil.copytree(Path(a[0]).parent,d/'baseline');assert sha(d/'baseline/snapshot.stdout.txt')==a[1]
    frozen=[str(d/'baseline/snapshot.stdout.txt'),a[1],a[2]]
    for f,h in zip(a[3::2],a[4::2]):
        p=Path(f);assert sha(p)==h;shutil.copyfile(p,d/p.name);frozen += [str(d/p.name),h]
    cfg={'prefix':prefix,'seed':seed,'baseline_sha256':a[1],'leaf':a[2],'captures':frozen[3:],'expected_raw_output':expected,'expected_events':3 if prefix=='DMIX' else 2}
    args=['D:/work/Java/jdk-17.0.7/bin/java.exe','-cp',cp+';'+str(classes),'OracleDmlCheckpointWorker','inspect','unused',d,prefix]+frozen[:3]+[str(len(frozen[3:])//2)]+frozen[3:]
    output=run(prefix+'-inspect',args).decode();cfg['windows']=[s.split('|')[1:] for s in output.splitlines() if s.startswith('WINDOW|')]
    assert int(cfg['windows'][-1][3])==cfg['expected_events'];configs.append(cfg)
    if prefix=='DMIX':
        empty=dict(cfg);empty['prefix']='DRBK';empty['captures']=cfg['captures'][2:];empty['expected_events']=0
        e=O/'DRBK';e.mkdir();shutil.copytree(d/'baseline',e/'baseline')
        for f in empty['captures'][::2]:shutil.copyfile(f,e/Path(f).name)
        empty['captures']=[str(e/Path(v).name) if j%2==0 else v for j,v in enumerate(empty['captures'])]
        result=run('DRBK-inspect',args[:4]+['inspect','unused',e,'DRBK',e/'baseline/snapshot.stdout.txt',a[1],a[2],'2']+empty['captures'],rc=1)
        assert not result and b'BASELINE_SCN_OUTSIDE_INPUT_COVERAGE' in (O/'DRBK-inspect.stderr.txt').read_bytes()
(O/'config.json').write_text(json.dumps(configs,indent=2));print(json.dumps({c['prefix']:c['windows'] for c in configs}))
