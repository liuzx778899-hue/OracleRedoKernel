"""Read-only gate continuation against frozen real captures; no Oracle actions."""
import hashlib
import json
import subprocess
from pathlib import Path

R=Path(__file__).resolve().parent.parent
O=R/'lab/out/baseline-physical-scope-20261005'
F=R/'lab/out/full-dml-capture-20261005'
J=O/'kernel-r2.jar'
JAVA='D:/work/Java/jdk-17.0.7/bin/java.exe'
JAVAC='D:/work/Java/jdk-17.0.7/bin/javac.exe'
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()

def run(name,args,rc=0,reason=None):
    if not (O/(name+'.rc.txt')).exists():
        (O/(name+'.command.json')).write_text(json.dumps(list(map(str,args))))
        p=subprocess.run(list(map(str,args)),capture_output=True,timeout=300)
        for ext,data in [('stdout',p.stdout),('stderr',p.stderr)]:
            (O/(name+'.'+ext+'.txt')).write_bytes(data)
        (O/(name+'.rc.txt')).write_text(str(p.returncode))
    assert int((O/(name+'.rc.txt')).read_text())==rc,name
    if rc:
        assert not (O/(name+'.stdout.txt')).read_bytes(),name
        assert reason in (O/(name+'.stderr.txt')).read_text(),name
    return (O/(name+'.stdout.txt')).read_bytes()

assert (O/'build.rc.txt').read_text()=='0'
assert sha(J)=='58da93ea85f3246d5a48ae914a29e23aa84bc6ade02de619d01e64e4c51d7932'
classes=O/'classes';classes.mkdir(exist_ok=True)
probes=['PhysicalTransactionProbe.java','VerifiedHeapRowsProbe.java','VerifiedHeapStateProbe.java','CapturedRowsProbe.java']
run('compile-gate-probes',[JAVAC,'-encoding','UTF-8','-cp',J,'-d',classes]+[R/'lab'/n for n in probes])
java=[JAVA,'-cp',str(J)+';'+str(classes)]
command=json.loads((O/'actual-r2.command.json').read_text())
snapshot=Path(command[4]);digest=command[5];leaf=command[6];captures=command[7:]
run('generic-no-tail',java+['CapturedRowsProbe','physical',snapshot,digest,leaf]+captures,1,'TRANSACTION_END_HAS_NO_UNDO_TAIL_REFERENCE')
before=O/'before-seed.snapshot';before.write_text(snapshot.read_text().replace('SNAPSHOT_SCN|2421228','SNAPSHOT_SCN|2370428'))
negative=command.copy();negative[4]=str(before);negative[5]=sha(before)
run('selected-no-tail',negative,1,'TRANSACTION_END_HAS_NO_UNDO_TAIL_REFERENCE')
negative=command.copy();negative[5]='0'*64
run('wrong-receipt',negative,1,'SNAPSHOT_ACQUISITION_DIGEST_MISMATCH')
foreign=O/'foreign-source.snapshot';foreign.write_text(snapshot.read_text().replace('1276726821','1276726822'))
negative=command.copy();negative[4]=str(foreign);negative[5]=sha(foreign)
run('foreign-source',negative,1,'SNAPSHOT_REDO_SOURCE_MISMATCH')
archive=run('archive-parity',java+['VerifiedHeapRowsProbe',F/'archive-44.dbf',snapshot,digest,leaf,F/'archive-45.dbf'])
rows=b'\n'.join(line for line in (O/'actual-r2.stdout.txt').read_bytes().splitlines() if line.startswith(b'ROW|'))+b'\n'
assert archive.replace(b'\r\n',b'\n')==rows.replace(b'\r\n',b'\n')
state=run('final-state',java+['CapturedRowsProbe','state',snapshot,digest,leaf]+captures)
assert state.decode().strip()=='STATE|73252|50333085|0|VALUE:80,VALUE:646d6c2d73656564,VALUE:616e63686f72'
old=R/'lab/out/captured-batch-20261005'
old_command=json.loads((old/'actual.command.json').read_text());old_command[2]=command[2]
assert run('old-update-parity',old_command)==(old/'actual.stdout.txt').read_bytes()
for name,path,rc,reason in [
    ('cross-baseline',R/'lab/out/release-closed-batch-r2/regression-real-snapshot-boundary-command.json',1,'BASELINE_CROSSES_TRANSACTION_BOUNDARY'),
    ('late-before',R/'lab/out/capture-row-faults-20261005/late-before-rows.command.json',1,'ROW_REPLAY_BEFORE_VALUE_MISMATCH'),
    ('rollback-only',R/'lab/out/verify-rollback-only/rows-command.json',0,None)]:
    args=json.loads(path.read_text());args[2]=java[2]
    assert run(name,args,rc,reason)==b''
proof={'kernel_sha256':sha(J),'generic_no_tail_refused':True,'post_baseline_no_tail_refused':True,
       'bad_receipt_and_source_refused':True,'capture_archive_rows_equal':True,'final_seed_state_equal':True,
       'old_update_batch_output_byte_equal':True,'new_source_actions':0,'full_dml_matrix_accepted':False}
(O/'gate-proof.json').write_text(json.dumps(proof,indent=2));print(json.dumps(proof))
