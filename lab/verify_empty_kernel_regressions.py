"""New kernel regression on frozen complete batches; no source/target actions or old test-suite rerun."""
import hashlib,json,subprocess
from pathlib import Path
R=Path(__file__).resolve().parent.parent;O=R/'lab/out/oracle-empty-target-20261005';J=R/'lab/out/release-empty-foreign-begin-r2-20261005/kernel.jar'
def run(n,args):
    assert not (O/(n+'.rc.txt')).exists();(O/(n+'.command.json')).write_text(json.dumps(list(map(str,args))));p=subprocess.run(list(map(str,args)),capture_output=True,timeout=240)
    for ext,v in [('stdout',p.stdout),('stderr',p.stderr)]:(O/(n+'.'+ext+'.txt')).write_bytes(v)
    (O/(n+'.rc.txt')).write_text(str(p.returncode));assert p.returncode==0,(n,p.stderr)
    return p
for name,command in [('update','captured-batch-20261005/actual.command.json'),('nonnull','baseline-physical-scope-20261005/actual-r2.command.json'),('null','nullable-tail-20261005/actual.command.json'),('mixed','mixed-dml-capture-20261005/raw-complete-batch.command.json')]:
    old=R/'lab/out'/command;args=json.loads(old.read_text());parts=args[2].split(';');args[2]=str(J)+';'+ ';'.join(parts[1:])
    assert run('new-kernel-regression-'+name,args).stdout==old.with_name(old.name.replace('command.json','stdout.txt')).read_bytes()
classes=O/'structural-classes';classes.mkdir();run('compile-structural',['D:/work/Java/jdk-17.0.7/bin/javac.exe','-encoding','UTF-8','-cp',J,'-d',classes,R/'lab/CapturedRecordsProbe.java'])
for name in ['foreign-uba-mismatch','active-same-sequence-slot','missing-rollback-marker']:
    path=O/(name+'.oracap');digest=hashlib.sha256(path.read_bytes()).hexdigest()
    run(name+'-structure',['D:/work/Java/jdk-17.0.7/bin/java.exe','-cp',str(J)+';'+str(classes),'CapturedRecordsProbe',path,digest,'records'])
(O/'regression-proof.json').write_text(json.dumps({'new_kernel_old_complete_batches_byte_equal':['update','nonnull','null','mixed'],'resealed_structural_refusals_passed':3,'source_actions':0,'old_test_suites_rerun':False},indent=2));print((O/'regression-proof.json').read_text())
