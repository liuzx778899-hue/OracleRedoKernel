"""Read-only complete physical/row gates for the one-shot mixed source experiment."""
import json,subprocess,sys
from pathlib import Path
R=Path(__file__).resolve().parent.parent;O=R/'lab/out/mixed-dml-capture-20261005';J=R/'lab/out/nullable-tail-20261005/kernel.jar'
def run(name,args,rc=None):
    assert not (O/(name+'.rc.txt')).exists(),name
    p=subprocess.run(list(map(str,args)),capture_output=True,timeout=240)
    for ext,b in [('stdout',p.stdout),('stderr',p.stderr)]: (O/(name+'.'+ext+'.txt')).write_bytes(b)
    (O/(name+'.command.json')).write_text(json.dumps(list(map(str,args))));(O/(name+'.rc.txt')).write_text(str(p.returncode))
    if rc is not None:assert p.returncode==rc,(name,p.stderr)
    return p
a=json.loads((O/'raw-index-leaf.command.json').read_text());snapshot=Path(a[4]);digest=a[5];leaf=(O/'raw-index-leaf.stdout.txt').read_text().strip().split('|')[1]
cp=str(J)+';'+str(R/'lab/out/baseline-physical-scope-20261005/classes');java=['D:/work/Java/jdk-17.0.7/bin/java.exe','-cp',cp]
physical=run('capture-physical',java+['CapturedRowsProbe','physical',snapshot,digest,leaf]+a[6:])
if physical.returncode==0:run('native-physical',[sys.executable,R/'lab/compare_typed_physical.py',snapshot,O/'capture-physical.stdout.txt',O/'dump.trc'],0)
state=run('capture-state',java+['CapturedRowsProbe','state',snapshot,digest,leaf]+a[6:])
run('compile-end-probe',['D:/work/Java/jdk-17.0.7/bin/javac.exe','-encoding','UTF-8','-cp',J,'-d',O/'classes',R/'lab/RawDmlEndProbe.java'],0)
end=a.copy();end[3]='com.streamhouse.oracle.RawDmlEndProbe';run('raw-end-layout',end,0)
print(json.dumps({'physical_rc':physical.returncode,'state_rc':state.returncode,'full_matrix_accepted':False,'new_source_actions':0}))
