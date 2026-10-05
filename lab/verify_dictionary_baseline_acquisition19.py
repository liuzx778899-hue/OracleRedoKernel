"""New-SCN V3 acquisition from the owned source; old historical redo is never authorized by it."""
import hashlib,json,shutil,subprocess,sys
from pathlib import Path
R=Path(__file__).resolve().parent.parent;O=R/'lab/out/dictionary-baseline-candidate-20261006';S='oraredo-19c-lab-20261004'
round=int(sys.argv[1]);assert not (O/f'actual-v3-r{round}.command.json').exists()
build=json.loads((O/'build-proof.json').read_text());sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
for name in ['kernel','adapter','spi']:assert sha(O/(name+'.jar'))==build[name+'_sha256']
assert subprocess.check_output(['docker','inspect',S,'--format','{{.Config.Labels.owner}}|{{.State.Status}}']).decode().strip()=='OracleRedoKernel|running'
shutil.copyfile(Path(__file__),O/f'executed-acquisition-driver-r{round}.py');shutil.copyfile(R/'lab/OracleSnapshotAcquisitionProbe.java',O/f'executed-acquisition-probe-r{round}.java')
shutil.copyfile(R/'lab/out/oracle-snapshot-acquisition-20261005/source.secret',O/'source.secret');(O/'classes').mkdir(exist_ok=True)
def run(name,args):
    args=list(map(str,args));(O/(name+'.command.json')).write_text(json.dumps(args));p=subprocess.run(args,capture_output=True,timeout=180)
    for suffix,data in [('stdout.txt',p.stdout),('stderr.txt',p.stderr),('rc.txt',str(p.returncode).encode())]:(O/(name+'.'+suffix)).write_bytes(data)
    return p
cp=';'.join(str(O/(name+'.jar')) for name in ['kernel','adapter','spi','ojdbc8'])
p=run(f'compile-acquisition-r{round}',['D:/work/Java/jdk-17.0.7/bin/javac.exe','-encoding','UTF-8','-cp',cp,'-d',O/'classes',R/'lab/OracleSnapshotAcquisitionProbe.java']);assert p.returncode==0,p.stderr.decode()
remote='/evidence/new-v3-r'+str(round)+'.snapshot';args=['docker','run','--rm','--label','owner=OracleRedoKernel','--label','scope=dictionary-baseline-acquisition','--network','container:'+S,'--mount','type=bind,source='+str(O)+',target=/evidence','--entrypoint','java','flink:1.18-java17','-cp','/evidence/classes:/evidence/kernel.jar:/evidence/adapter.jar:/evidence/spi.jar:/evidence/ojdbc8.jar','OracleSnapshotAcquisitionProbe','/evidence/source.secret','new-v3','ORKLAB','T_V2_HIST_20261006','PK_V2_HIST_20261006','history-normal',remote]
p=run(f'actual-v3-r{round}',args)
snapshot=O/f'new-v3-r{round}.snapshot'
if p.returncode:
    assert not snapshot.exists() and not p.stdout
    (O/f'failed-acquisition-r{round}-proof.json').write_text(json.dumps(dict(rc=p.returncode,events=0,snapshot_issued=False,source_actions=0,reason=p.stderr.decode().splitlines()[0]),indent=2))
    raise RuntimeError(p.stderr.decode())
q=p.stdout.decode().strip().split('|');assert q[:2]==['ACQUIRED','new-v3'] and q[4:]==['3','1','1'] and sha(snapshot)==q[3]
frames=snapshot.read_text().splitlines();assert frames[0]=='FORMAT|ORK_HEAP_SNAPSHOT_V3' and sum(f.startswith('DICT_ROW|') for f in frames)==6
proof=dict(scn=int(q[2]),sha256=q[3],snapshot=str(snapshot.relative_to(R)),source_actions=0,target_actions=0,events=0,full_sys_rows=6,indexes=3,product_rebuilt=False,history_dml_accepted=False)
(O/f'actual-acquisition-r{round}-proof.json').write_text(json.dumps(proof,indent=2));print(json.dumps(proof))
