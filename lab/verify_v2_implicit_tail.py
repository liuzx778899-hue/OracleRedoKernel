"""Read-only candidate replay of frozen seq62--64; preserve the old parser failure."""
import hashlib,json,shutil,subprocess,sys
from pathlib import Path
R=Path(__file__).resolve().parent.parent;O=R/'lab/out/v2-dml-capture-20261006'
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
def run(name,args):
    assert not (O/(name+'.command.json')).exists()
    (O/(name+'.command.json')).write_text(json.dumps(list(map(str,args))))
    p=subprocess.run(list(map(str,args)),capture_output=True,timeout=600)
    (O/(name+'.stdout.txt')).write_bytes(p.stdout);(O/(name+'.stderr.txt')).write_bytes(p.stderr)
    (O/(name+'.rc.txt')).write_text(str(p.returncode))
    assert p.returncode==0,(name,p.stderr[-2000:])
lib=O/'implicit-tail-libs';lib.mkdir()
for p in (O/'parser-libs').glob('*.jar'):
    shutil.copyfile(R/'target/oracle-redo-kernel-0.1.0-SNAPSHOT.jar' if p.name=='kernel.jar' else p,lib/p.name)
sources=[R/'src/main/java/com/streamhouse/oracle'/n for n in ['HeapRowPatch.java','HeapUndoPair.java']]
(O/'implicit-tail-execution-pin.json').write_text(json.dumps(dict(driver=sha(Path(__file__)),binaries={p.name:sha(p) for p in lib.glob('*.jar')},sources={p.name:sha(p) for p in sources}),indent=2))
old=json.loads((O/'new-complete-batch.command.json').read_text());old[3]=str(lib/'*')+';'+str(O/'parser-classes')
run('implicit-tail-complete-batch',old)
physical=old.copy();physical[4:5]=['CapturedRowsProbe','physical'];run('implicit-tail-physical',physical)
projection=old.copy();projection[4]='AcquiredDmlProjectionProbe';run('implicit-tail-typed-projection',projection)
b=json.loads((O/'baseline-reference.json').read_text())
run('implicit-tail-native-physical',[sys.executable,R/'lab/compare_typed_physical.py',b['path'],O/'implicit-tail-physical.stdout.txt',O/'dump.trc'])
print('FROZEN_V2_BATCH_AND_NATIVE_GRAPH_VERIFIED')
