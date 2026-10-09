"""Verify the already executed RAW fixture with a repaired binary; no source or target actions."""
from pathlib import Path
import hashlib,json,shutil,subprocess
R=Path('D:/work/vibecode/OracleRedoKernel');N=R/'lab/out/d4-raw-source-20261009';O=N/'parser-r4';O.mkdir(exist_ok=False)
shutil.copyfile(__file__,O/'executed.py')
sha=lambda p:hashlib.sha256(Path(p).read_bytes()).hexdigest()
assert (N/'resume-stop.rc').read_text()=='0' and (N/'resume-events.rc').read_text()=='1'
libs=O/'libs';libs.mkdir()
for p in (N/'libs').glob('*.jar'):
 if p.name not in ['kernel.jar','spi.jar']:shutil.copyfile(p,libs/p.name)
shutil.copyfile(R/'lab/out/d3-stream-runtime-20261009/producer-product-r109/libs/streamhouse-spi-0.1.0-SNAPSHOT.jar',libs/'spi.jar')
shutil.copyfile(R/'target/oracle-redo-kernel-0.1.0-SNAPSHOT.jar',libs/'kernel.jar')
base=json.loads((N/'baseline-reference.json').read_text());archives=json.loads((N/'archives.json').read_text())
assert sha(base['path'])==base['sha256']
args=[base['path'],base['sha256']]
for a in archives:assert sha(a['path'])==a['sha256'];args.extend([a['path'],a['sha256']])
command=['D:/work/Java/jdk-17.0.7/bin/java.exe','-Xmx3g','-cp',str(libs/'*')+';'+str(N/'classes'),'RawValueEventsProbe',*args]
(O/'command.json').write_text(json.dumps(command,indent=2))
p=subprocess.run(command,capture_output=True,timeout=600)
for suffix,data in [('stdout',p.stdout),('stderr',p.stderr)]:(O/('events.'+suffix)).write_bytes(data)
(O/'events.rc').write_text(str(p.returncode));assert p.returncode==0,p.stderr.decode()
result=json.loads(p.stdout);events=result['events']
expected=[('UPDATE',{'ID':1,'BIN':{'hex':'00ff80c328'}},{'ID':1,'BIN':{'hex':'010080ff'}}),('INSERT',None,{'ID':2,'BIN':{'hex':'c32800ff'}}),('UPDATE',{'ID':2,'BIN':{'hex':'c32800ff'}},{'ID':2,'BIN':None}),('DELETE',{'ID':2,'BIN':None},None)]
assert [(event['operation'],event['before'],event['after']) for event in events]==expected
assert len({event['eventId'] for event in events})==4
truth=(N/'resume-dml.stdout').read_text()
for line in ['TRUTH|U1|1|010080FF|','TRUTH|I2|2|C32800FF|','TRUTH|U2|2|NULL','FINAL|1|010080FF','COUNT|1']:assert line in truth
assert result['evidence']['columns'][1]['type']=='RAW'
proof=dict(events=4,mismatches=0,rollbackEvents=0,baseline=base,archives=archives,leaf=result['leaf'],kernelSha256=sha(libs/'kernel.jar'),adapterSha256=sha(libs/'adapter.jar'),spiSha256=sha(libs/'spi.jar'),sourceDmlReplayed=False,sourceActions=0,targetActions=0,productIntegrated=False)
(O/'normal-proof.json').write_text(json.dumps(proof,indent=2));print(json.dumps(proof))
