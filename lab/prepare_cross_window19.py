"""One-shot new baseline for the owned cross-window experiment; source SELECT only."""
import hashlib,json,shutil,subprocess
from pathlib import Path

R=Path(__file__).resolve().parent.parent
N=R/'lab/out/cross-window-active-20261007'
S='oraredo-19c-lab-20261004'
N.mkdir(exist_ok=False)
(N/'executed-prepare.py').write_bytes(Path(__file__).read_bytes())
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()

def run(name,args,body=None):
    args=list(map(str,args))
    (N/(name+'.command.json')).write_text(json.dumps(args))
    p=subprocess.run(args,input=body,capture_output=True,timeout=180)
    for suffix,data in [('stdout.txt',p.stdout),('stderr.txt',p.stderr),('rc.txt',str(p.returncode).encode())]:
        (N/(name+'.'+suffix)).write_bytes(data)
    assert p.returncode==0,(name,p.returncode)
    return p.stdout.decode()

assert run('source-ownership',['docker','inspect',S,'--format','{{.Config.Labels.owner}}|{{.State.Running}}']).strip()=='OracleRedoKernel|true'
active=run('collector-preflight',['docker','exec','-i',S,'python3','-'],b"from pathlib import Path\nfor p in Path('/proc').glob('[0-9]*/cmdline'):\n try:\n  a=p.read_bytes().split(b'\\0')\n  if any(x.endswith((b'/follow_closed_members.py',b'/resume_online_observation.py')) for x in a):print(p.parent.name)\n except OSError:pass\n")
assert not active.strip(),'existing collector: inspect its handle before source actions'
for name in ['kernel.jar','adapter.jar','spi.jar']:
    shutil.copyfile(R/'lab/out/split-history-product-20261007'/name,N/name)
shutil.copyfile(R/'lab/out/v3-product-runtime-20261006/ojdbc8.jar',N/'ojdbc8.jar')
shutil.copyfile(R/'lab/out/oracle-snapshot-acquisition-20261005/source.secret',N/'source.secret')
classes=N/'acquisition-classes';classes.mkdir()
run('compile-acquisition',['D:/work/Java/jdk-17.0.7/bin/javac.exe','-cp',';'.join(str(N/x) for x in ['kernel.jar','adapter.jar','spi.jar']),'-d',classes,R/'lab/OracleSnapshotAcquisitionProbe.java'])
out=run('fresh-acquisition',['docker','run','--rm','--label','owner=OracleRedoKernel','--label','scope=cross-window-baseline','--network','container:'+S,'--mount','type=bind,source='+str(N)+',target=/evidence','--entrypoint','java','flink:1.18-java17','-cp','/evidence/acquisition-classes:/evidence/kernel.jar:/evidence/adapter.jar:/evidence/spi.jar:/evidence/ojdbc8.jar','OracleSnapshotAcquisitionProbe','/evidence/source.secret','cross-window','ORKLAB','T_V2_HIST_20261006','PK_V2_HIST_20261006','history-normal','/evidence/current-v3.snapshot'])
q=next(x.split('|') for x in out.splitlines() if x.startswith('ACQUIRED|'))
snapshot=N/'current-v3.snapshot'
assert len(q)==7 and q[4:]==['3','1','1'] and sha(snapshot)==q[3]
(N/'baseline-reference.json').write_text(json.dumps(dict(path=str(snapshot),scn=int(q[2]),sha256=q[3],new_logs_only=True,events=0,source_dml=0,target_actions=0),indent=2))
# Run the independent ruler in the new directory, preserving its exact executable source.
truth=(R/'lab/verify_v3_history_baseline19.py').read_text()
truth=truth.replace("O=R/'lab/out/v3-historical-raw-20261006'","O=R/'lab/out/cross-window-active-20261007'")
driver=N/'fresh-baseline-truth.py'
truth=truth.replace('R=Path(__file__).resolve().parent.parent;', 'R=Path('+repr(str(R))+');')
driver.write_text(truth)
run('independent-baseline-driver',[__import__('sys').executable,driver])
(N/'preparation-proof.json').write_text(json.dumps(dict(baseline=json.loads((N/'baseline-reference.json').read_text()),truth=json.loads((N/'baseline-truth-proof.json').read_text()),artifacts={x:sha(N/x) for x in ['kernel.jar','adapter.jar','spi.jar','ojdbc8.jar']},source_mutations=0,cross_window_recovery='NOT_VERIFIED'),indent=2))
print('NEW_CROSS_WINDOW_BASELINE_VERIFIED_NO_SOURCE_MUTATIONS')
