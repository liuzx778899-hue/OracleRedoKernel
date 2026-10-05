"""Reproduce a historical-schema acceptance failure with frozen shipped binaries."""
import hashlib,json,subprocess,sys
from pathlib import Path
R=Path(__file__).resolve().parent.parent;O=R/'lab/out/schema-barrier-20261005';sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
def run(name,args,expected=0):
 assert not (O/(name+'.command.json')).exists();(O/(name+'.command.json')).write_text(json.dumps(list(map(str,args))))
 p=subprocess.run(list(map(str,args)),capture_output=True,timeout=180)
 for suffix,value in [('stdout',p.stdout),('stderr',p.stderr)]:(O/(name+'.'+suffix+'.txt')).write_bytes(value)
 (O/(name+'.rc.txt')).write_text(str(p.returncode));assert expected is None or p.returncode==expected,(name,p.stderr[-1500:]);return p
ref=json.loads((O/'capture-reference.json').read_text());b=ref['baseline'];classes=O/'parser-classes';classes.mkdir(exist_ok=False)
# The seed index insert precedes acquisition. This raw-only bootstrap probe is
# local to this experiment; event replay still filters at the actual baseline SCN.
leafsource=(R/'lab/RawDmlLeafProbe.java').read_text().replace('public class RawDmlLeafProbe','public class RawBootstrapLeafProbe').replace(' || record.scn()<=snapshot.scn()','')
p=O/'RawBootstrapLeafProbe.java';p.write_text(leafsource)
probes=[p]+[R/'lab'/(n+'.java') for n in ['CapturedBatchProbe','VerifiedHeapRowsProbe','PhysicalTransactionProbe','CapturedRowsProbe']]
cp=';'.join(str(O/(n+'.jar')) for n in ['kernel','adapter','spi'])+';'+str(classes)
(O/'parser-execution-pin.json').write_text(json.dumps(dict(files={str(p.relative_to(R)):sha(p) for p in probes+list(O.glob('*.jar'))+[Path(__file__)]}),indent=2))
run('compile-parser',['D:/work/Java/jdk-17.0.7/bin/javac.exe','-encoding','UTF-8','-cp',cp,'-d',classes]+probes)
inputs=[]
for seq in ref['sequences']:
 p=O/'capture'/('sequence-'+str(seq)+'.oracap');inputs += [str(p),sha(p)]
base=[b['path'],b['sha256']];java=['D:/work/Java/jdk-17.0.7/bin/java.exe','-Xmx1536m','-cp',cp]
leaf=run('raw-bootstrap-leaf',java+['RawBootstrapLeafProbe']+base+inputs).stdout.decode().strip().split('|')[1]
(O/'raw-leaf.txt').write_text(leaf)
run('old-schema-batch',java+['CapturedBatchProbe']+base+[leaf]+inputs,None)
run('old-schema-physical',java+['CapturedRowsProbe','physical']+base+[leaf]+inputs,None)
run('native-physical',[sys.executable,R/'lab/compare_typed_physical.py',b['path'],O/'old-schema-physical.stdout.txt',O/'dump.trc'],None)
print('SCHEMA_BARRIER_REPRODUCTION_PRESERVED')
