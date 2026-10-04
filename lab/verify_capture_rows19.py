"""Raw captured graph/row replay and archive parity. Existing source actions are never replayed."""
import hashlib,json,shutil,subprocess,sys
from pathlib import Path

ROOT=Path(__file__).resolve().parent.parent;OLD=ROOT/'lab/out/closed-follow-20261005'
OUT=ROOT/('lab/out/capture-rows-r2-20261005' if sys.argv[1:]==['--after-marker3'] else 'lab/out/capture-rows-20261005')
JAVA='D:/work/Java/jdk-17.0.7/bin/java.exe';JAVAC='D:/work/Java/jdk-17.0.7/bin/javac.exe'
OUT.mkdir(parents=True,exist_ok=False);jar=OUT/'kernel.jar';shutil.copyfile(ROOT/'target/oracle-redo-kernel-0.1.0-SNAPSHOT.jar',jar)

def run(name,args,expected=0):
    (OUT/(name+'.command.json')).write_text(json.dumps(list(map(str,args))))
    result=subprocess.run(list(map(str,args)),capture_output=True,timeout=240)
    for ext,data in [('stdout',result.stdout),('stderr',result.stderr)]:(OUT/(name+'.'+ext+'.txt')).write_bytes(data)
    (OUT/(name+'.rc.txt')).write_text(str(result.returncode))
    if (result.returncode==0)!=(expected==0) or expected and result.stdout:raise RuntimeError(name+': unexpected result; preserve')
    return result.stdout.decode()

run('freeze-tree',['git','archive','--format=zip','--output='+str(OUT/'source-tree.zip'),'HEAD'])
classes=OUT/'classes';classes.mkdir();probes=['CapturedRowsProbe.java','PhysicalTransactionProbe.java','VerifiedHeapRowsProbe.java','VerifiedHeapStateProbe.java']
run('compile-probes',[JAVAC,'-cp',jar,'-d',classes]+[ROOT/'lab'/name for name in probes])
java=[JAVA,'-cp',str(jar)+';'+str(classes)];snapshot=OLD/'baseline/snapshot.stdout.txt';digest=json.loads((OLD/'baseline/receipt.json').read_text())['snapshot_sha256'];leaf='0x0300019b'
items=json.loads((OLD/'capture/receipt.json').read_text())['captured'];captures=[];archives=[]
for item in items:captures.extend([OLD/'capture'/item['journal'],item['sha256']]);archives.append(OLD/('archive-'+str(item['sequence'])+'.dbf'))
for mode in ('physical','rows','state'):
    run('capture-'+mode,java+['CapturedRowsProbe',mode,snapshot,digest,leaf]+captures)
    if mode=='physical':args=['PhysicalTransactionProbe','--snapshot',snapshot,digest,leaf]+archives
    elif mode=='rows':args=['VerifiedHeapRowsProbe',archives[0],snapshot,digest,leaf]+archives[1:]
    else:args=['VerifiedHeapStateProbe',snapshot,digest,leaf]+archives
    run('archive-'+mode,java+args)
    assert (OUT/('capture-'+mode+'.stdout.txt')).read_bytes()==(OUT/('archive-'+mode+'.stdout.txt')).read_bytes()
dumps=[ROOT/'lab/out/capture-records-20261005'/('dump-'+str(n)+'.trc') for n in (40,41,42)]
run('native-physical',[sys.executable,ROOT/'lab/compare_typed_physical.py',snapshot,OUT/'capture-physical.stdout.txt']+dumps)
proof={'runtime_commit':subprocess.check_output(['git','rev-parse','HEAD']).decode().strip(),'jar_sha256':hashlib.sha256(jar.read_bytes()).hexdigest(),'changes':len((OUT/'capture-rows.stdout.txt').read_text().splitlines()),'capture_to_archive_graph_rows_state_equal':True,'cdc_events':0,'consumer_ack':False}
(OUT/'proof.json').write_text(json.dumps(proof,indent=2));print(json.dumps(proof))
