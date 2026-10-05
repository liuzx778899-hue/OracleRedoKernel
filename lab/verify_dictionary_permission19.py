"""Actual fresh SELECT-only permission refusal; original evidence and secrets stay private."""
import hashlib,json,shutil,subprocess
from pathlib import Path
R=Path(__file__).resolve().parent.parent
O=R/'lab/out/dictionary-acquisition-20261005'
A=R/'lab/out/oracle-snapshot-acquisition-20261005'
def run(name,args):
    assert not (O/(name+'.rc')).exists()
    (O/(name+'.command.json')).write_text(json.dumps(list(map(str,args))))
    p=subprocess.run(list(map(str,args)),capture_output=True,timeout=120)
    (O/(name+'.stdout.log')).write_bytes(p.stdout)
    (O/(name+'.stderr.log')).write_bytes(p.stderr)
    (O/(name+'.rc')).write_text(str(p.returncode))
    assert p.returncode==0,name
    return p.stdout
for name,src in [('adapter.jar',R/'integration/streamhouse-engine-oracle/target/streamhouse-engine-oracle-0.1.0-SNAPSHOT.jar'),('ojdbc8.jar',A/'ojdbc8.jar'),('source.secret',A/'source.secret')]:
    shutil.copyfile(src,O/name)
classes=O/'classes';classes.mkdir(exist_ok=False)
cp=';'.join(str(O/(n+'.jar')) for n in ['adapter','kernel','spi','ojdbc8'])
run('compile-permission-probe',['D:/work/Java/jdk-17.0.7/bin/javac.exe','-encoding','UTF-8','-cp',cp,'-d',classes,R/'lab/OracleDictionaryAcquisitionProbe.java'])
files=[R/'lab/OracleDictionaryAcquisitionProbe.java',R/'lab/verify_dictionary_permission19.py',*classes.glob('*'),*[O/(n+'.jar') for n in ['adapter','kernel','spi','ojdbc8']]]
(O/'permission-execution-pins.json').write_text(json.dumps({str(p.relative_to(R)):hashlib.sha256(p.read_bytes()).hexdigest() for p in files},indent=2))
case=json.loads((A/'cases.json').read_text())[0]
out=run('actual-permission-refusal',['docker','run','--rm','--label','owner=OracleRedoKernel','--label','scope=dictionary-permission-refusal','--network','container:oraredo-19c-lab-20261004','--mount','type=bind,source='+str(O)+',target=/evidence,readonly','--entrypoint','java','flink:1.18-java17','-cp','/evidence/classes:/evidence/adapter.jar:/evidence/kernel.jar:/evidence/spi.jar:/evidence/ojdbc8.jar','OracleDictionaryAcquisitionProbe','/evidence/source.secret',case['owner'],case['table'],case['index']])
assert out.startswith(b'REFUSED|DICTIONARY_PERMISSION|')
print(out.decode().strip())
