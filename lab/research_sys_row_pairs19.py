"""Raw eleven SYS row-pair values plus explicit dispositions; no source mutation or historical authority."""
import hashlib,json,shutil,subprocess,sys
from pathlib import Path
R=Path(__file__).resolve().parent.parent;O=R/'lab/out/sys-full-history-research-20261006';H=R/'lab/out/v2-historical-product-20261006';round=int(sys.argv[1]);K=O/'new-kernel.jar'
assert round==2 and not K.exists();shutil.copyfile(R/'target/oracle-redo-kernel-0.1.0-SNAPSHOT.jar',K)
shutil.copyfile(Path(__file__),O/f'executed-row-pair-driver-r{round}.py');shutil.copyfile(R/'lab/SysDictionaryRowPairsProbe.java',O/f'executed-row-pair-probe-r{round}.java')
classes=O/f'row-classes-r{round}';classes.mkdir()
def run(name,args):
    assert not (O/(name+'.command.json')).exists();args=list(map(str,args));(O/(name+'.command.json')).write_text(json.dumps(args));p=subprocess.run(args,capture_output=True,timeout=180)
    for suffix,data in [('stdout.txt',p.stdout),('stderr.txt',p.stderr),('rc.txt',str(p.returncode).encode())]:(O/(name+'.'+suffix)).write_bytes(data)
    assert p.returncode==0,p.stderr.decode();return p.stdout.decode()
run(f'compile-row-pairs-r{round}',['D:/work/Java/jdk-17.0.7/bin/javac.exe','-encoding','UTF-8','-cp',K,'-d',classes,R/'lab/SysDictionaryRowPairsProbe.java'])
proof=[]
for seq,xid in [(66,'10.18.2272'),(66,'3.30.705'),(67,'3.26.705')]:
    text=run(f'row-pairs-r{round}-{seq}-{xid}',['D:/work/Java/jdk-17.0.7/bin/java.exe','-Xmx1536m','-cp',str(K)+';'+str(classes),'SysDictionaryRowPairsProbe',H/'snapshots/100644812901937152.snapshot','e16e6275651894614dc9a79e42381338ddbf0771aa68c971df66f35abd5c0e27',H/f'archive-{seq}.dbf',xid])
    proof.append(dict(sequence=seq,xid=xid,row_pairs=sum(l.startswith('ROW|') for l in text.splitlines()),dispositions=sum(l.startswith('DISPOSITION|') for l in text.splitlines())))
(O/'row-pair-query-proof.json').write_text(json.dumps(dict(cases=proof,history_authority=False,events=0),indent=2));print(json.dumps(proof))
