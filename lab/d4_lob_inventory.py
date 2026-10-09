"""Offline raw-byte LOB inventory; native traces remain independent diagnostics."""
from pathlib import Path
import hashlib,json,shutil,subprocess,sys,re
R=Path(__file__).resolve().parent.parent
F=R/'lab/out/d4-lob-source-20261009/continue-r2'
rollback=sys.argv[1:2]==['--rollback'];args=sys.argv[2:] if rollback else sys.argv[1:]
assert len(args)==2 and args[0]=='--run' and re.fullmatch(('rollback' if rollback else 'inventory')+r'-r[1-9][0-9]*',args[1])
N=F/args[1];probe='LobRollbackProbe' if rollback else 'LobLayoutInventoryProbe'
proof=json.loads((F/'source-frozen-proof.json').read_text())
N.mkdir(exist_ok=False);(N/'classes').mkdir();shutil.copyfile(__file__,N/'executed.py')
def run(name,args):
 (N/(name+'.command.json')).write_text(json.dumps(list(map(str,args))))
 p=subprocess.run(list(map(str,args)),capture_output=True,timeout=300)
 (N/(name+'.stdout')).write_bytes(p.stdout);(N/(name+'.stderr')).write_bytes(p.stderr)
 (N/(name+'.rc')).write_text(str(p.returncode));assert p.returncode==0,(name,p.returncode)
 return p.stdout.decode()
sha=lambda p:hashlib.sha256(Path(p).read_bytes()).hexdigest()
run('compile',['D:/work/Java/jdk-17.0.7/bin/javac.exe','-encoding','UTF-8','-cp',R/'target/classes','-d',N/'classes',R/'lab'/('LobRollbackProbe.java' if rollback else 'LobLayoutInventoryProbe.java')])
args=['D:/work/Java/jdk-17.0.7/bin/java.exe','-Xmx2g','-cp',str(R/'target/classes')+';'+str(N/'classes'),'com.streamhouse.oracle.LobRollbackProbe' if rollback else probe,str(proof['firstScn'])]
for item in proof['archives']:
 assert sha(item['path'])==item['sha256'];args.extend([item['path'],item['sha256']])
out=run('inventory',args)
summary=next(x for x in out.splitlines() if x.startswith('SUMMARY|'))
(N/'proof.json').write_text(json.dumps(dict(raw=proof['archives'],probeSourceSha256=sha(R/'lab'/(probe+'.java')),probeClassSha256=sha(N/'classes'/('com/streamhouse/oracle/LobRollbackProbe.class' if rollback else probe+'.class')),stdoutSha256=sha(N/'inventory.stdout'),summary=summary,events=0,role='DISCOVERY_ONLY' if rollback else 'STRUCTURAL_ONLY'),indent=2))
print(summary)
