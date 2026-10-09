"""Offline raw-byte LOB inventory; native traces remain independent diagnostics."""
from pathlib import Path
import hashlib,json,shutil,subprocess,sys,re
R=Path(__file__).resolve().parent.parent
F=R/'lab/out/d4-lob-source-20261009/continue-r2'
assert len(sys.argv)==3 and sys.argv[1]=='--run' and re.fullmatch(r'inventory-r[1-9][0-9]*',sys.argv[2])
N=F/sys.argv[2]
proof=json.loads((F/'source-frozen-proof.json').read_text())
N.mkdir(exist_ok=False);(N/'classes').mkdir();shutil.copyfile(__file__,N/'executed.py')
def run(name,args):
 (N/(name+'.command.json')).write_text(json.dumps(list(map(str,args))))
 p=subprocess.run(list(map(str,args)),capture_output=True,timeout=300)
 (N/(name+'.stdout')).write_bytes(p.stdout);(N/(name+'.stderr')).write_bytes(p.stderr)
 (N/(name+'.rc')).write_text(str(p.returncode));assert p.returncode==0,(name,p.returncode)
 return p.stdout.decode()
sha=lambda p:hashlib.sha256(Path(p).read_bytes()).hexdigest()
run('compile',['D:/work/Java/jdk-17.0.7/bin/javac.exe','-encoding','UTF-8','-cp',R/'target/classes','-d',N/'classes',R/'lab/LobLayoutInventoryProbe.java'])
args=['D:/work/Java/jdk-17.0.7/bin/java.exe','-Xmx2g','-cp',str(R/'target/classes')+';'+str(N/'classes'),'LobLayoutInventoryProbe',str(proof['firstScn'])]
for item in proof['archives']:
 assert sha(item['path'])==item['sha256'];args.extend([item['path'],item['sha256']])
out=run('inventory',args)
summary=next(x for x in out.splitlines() if x.startswith('SUMMARY|'))
(N/'proof.json').write_text(json.dumps(dict(raw=proof['archives'],probeSourceSha256=sha(R/'lab/LobLayoutInventoryProbe.java'),probeClassSha256=sha(N/'classes/LobLayoutInventoryProbe.class'),stdoutSha256=sha(N/'inventory.stdout'),summary=summary,events=0,role='STRUCTURAL_ONLY'),indent=2))
print(summary)
