"""Only repair failed empty-batch gate with the new kernel; source DML and accepted old gates not replayed."""
import json,subprocess,sys
from pathlib import Path
R=Path(__file__).resolve().parent.parent;O=R/'lab/out/empty-dml-capture-20261005';J=R/'lab/out/release-empty-foreign-begin-r2-20261005/kernel.jar'
assert J.exists();args=json.loads((O/'complete-batch.command.json').read_text());args[2]=str(J)+';'+str(R/'lab/out/captured-batch-20261005/classes')
def run(n,a):
    assert not (O/(n+'.rc.txt')).exists();(O/(n+'.command.json')).write_text(json.dumps(list(map(str,a))));p=subprocess.run(list(map(str,a)),capture_output=True,timeout=240)
    for ext,v in [('stdout',p.stdout),('stderr',p.stderr)]:(O/(n+'.'+ext+'.txt')).write_bytes(v)
    (O/(n+'.rc.txt')).write_text(str(p.returncode));assert p.returncode==0,(n,p.stderr)
    return p
run('native-control-audit',[sys.executable,R/'lab/compare_control_native.py',O/'control-inventory.stdout.txt',O/'dump.trc'])
p=run('complete-batch-r2',args);assert sum(s.startswith(b'EVENT|') for s in p.stdout.splitlines())==0
print(p.stdout.decode())
