"""Build and exercise the complete frozen history, preserving every attempt separately. No source actions."""
import hashlib,json,os,shutil,subprocess,sys,xml.etree.ElementTree as ET
from pathlib import Path
R=Path(__file__).resolve().parent.parent;H=R/'lab/out/v3-product-history-20261006'
N=R/'lab/out/split-history-candidate-20261007';N.mkdir(exist_ok=True)
attempt=sys.argv[1] if len(sys.argv)>1 else 'r1';A=N/attempt;A.mkdir()
(A/'executed-build-driver.py').write_bytes(Path(__file__).read_bytes())
shutil.copyfile(R/'src/test/java/com/streamhouse/oracle/DictionaryObjectUndoPairTest.java',A/'DictionaryObjectUndoPairTest.java')
shutil.copyfile(R/'src/test/java/com/streamhouse/oracle/TransactionStartTest.java',A/'TransactionStartTest.java')
shutil.copyfile(R/'src/test/resources/col-delete-begin-profile.txt',A/'col-delete-begin-profile.txt')
for name in ['ArchivedDictionaryIndexRestores','ArchivedDictionaryIndexSplit','ArchivedDictionaryHistory','IndexUndoPair','DictionaryHistoryRows','HeapRowPatch','DictionaryObjectUndoPair','TransactionStart']:
    shutil.copyfile(R/f'src/main/java/com/streamhouse/oracle/{name}.java',A/(name+'.java'))
env=dict(os.environ,JAVA_HOME='D:/work/Java/jdk-17.0.7');env['PATH']=env['JAVA_HOME']+'/bin;'+env['PATH']
def run(name,args,required=True):
    args=list(map(str,args));assert not (A/(name+'.command.json')).exists();(A/(name+'.command.json')).write_text(json.dumps(args))
    p=subprocess.run(args,env=env,capture_output=True,timeout=900)
    for ext,b in [('stdout.txt',p.stdout),('stderr.txt',p.stderr),('rc.txt',str(p.returncode).encode())]:(A/(name+'.'+ext)).write_bytes(b)
    print(name,p.returncode,p.stderr[-250:].decode(errors='replace'),flush=True)
    if required:assert p.returncode==0,(name,p.returncode)
    return p
run('kernel-build',['D:/work/Java/apache-maven-3.9.11/bin/mvn.cmd','clean','package'])
shutil.copyfile(R/'target/oracle-redo-kernel-0.1.0-SNAPSHOT.jar',A/'kernel.jar');shutil.copytree(R/'target/surefire-reports',A/'reports')
tests=[0,0,0,0]
for p in (A/'reports').glob('TEST-*.xml'):
    e=ET.parse(p).getroot()
    for i,k in enumerate(['tests','failures','errors','skipped']):tests[i]+=int(e.attrib[k])
(A/'build-summary.json').write_text(json.dumps(dict(tests=tests,kernel_sha256=hashlib.sha256((A/'kernel.jar').read_bytes()).hexdigest())))
C=A/'classes';C.mkdir();cp=str(A/'kernel.jar')+';'+str(C);j=env['JAVA_HOME']+'/bin/'
probes=['ArchivedDictionaryHistoryEvidenceProbe','CapturedHistoricalBatchProbe']
for name in probes:shutil.copyfile(R/f'lab/{name}.java',A/(name+'.java'))
run('compile-probes',[j+'javac.exe','-cp',cp,'-d',C,*[R/f'lab/{n}.java' for n in probes]])
baseline=[H/'actual-product-v3.snapshot','323fd35a8ceee43545c28a9fb32f55cedd41409a3e1b5fb63e08606380cd24a2']
base=[j+'java.exe','-Xmx1536m','-cp',cp]
run('archive-history',base+['ArchivedDictionaryHistoryEvidenceProbe',*baseline,H/'archive-70.dbf',H/'archive-71.dbf'],False)
capture=[]
for seq in [70,71]:
    p=H/f'capture/sequence-{seq}.oracap';capture.extend([p,hashlib.sha256(p.read_bytes()).hexdigest()])
run('capture-history',base+['ArchivedDictionaryHistoryEvidenceProbe',*baseline,'captures',*capture],False)
run('capture-business',base+['CapturedHistoricalBatchProbe',*baseline,'50342539',*capture],False)
