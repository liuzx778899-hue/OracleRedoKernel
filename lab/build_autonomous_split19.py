"""Build the allocation-aware closure after preserving the rejected coverage audit candidate."""
import hashlib,json,os,shutil,subprocess,zipfile
from pathlib import Path
R=Path(__file__).resolve().parent.parent;N=R/'lab/out/dictionary-split-candidate-20261006';H=R/'lab/out/v3-product-history-20261006'
assert not (N/'r3-kernel-build.command.json').exists();(N/'executed-r3-build-driver.py').write_bytes(Path(__file__).read_bytes())
for p in [R/'src/main/java/com/streamhouse/oracle/ArchivedDictionaryIndexSplit.java',R/'lab/ArchivedDictionarySplitProbe.java',R/'lab/CapturedDictionarySplitProbe.java']:shutil.copyfile(p,N/('r3-'+p.name))
env=dict(os.environ,JAVA_HOME='D:/work/Java/jdk-17.0.7');env['PATH']=env['JAVA_HOME']+'/bin;'+env['PATH']
def run(name,args):
    args=list(map(str,args));assert not (N/(name+'.command.json')).exists();(N/(name+'.command.json')).write_text(json.dumps(args));p=subprocess.run(args,env=env,capture_output=True,timeout=900)
    for ext,value in [('stdout.txt',p.stdout),('stderr.txt',p.stderr),('rc.txt',str(p.returncode).encode())]:(N/(name+'.'+ext)).write_bytes(value)
    assert p.returncode==0,(name,p.returncode,p.stderr[-300:]);print(name,p.returncode,flush=True);return p
run('r3-kernel-build',['D:/work/Java/apache-maven-3.9.11/bin/mvn.cmd','clean','package']);shutil.copyfile(R/'target/oracle-redo-kernel-0.1.0-SNAPSHOT.jar',N/'kernel-r3.jar');shutil.copytree(R/'target/surefire-reports',N/'kernel-r3-reports')
with zipfile.ZipFile(R/'lab/out/index-leaf-rows-r2-20261006/kernel.jar') as a,zipfile.ZipFile(N/'kernel-r3.jar') as b:
    before={n:a.read(n) for n in a.namelist() if n.endswith('.class')};after={n:b.read(n) for n in b.namelist() if n.endswith('.class')};assert all(after[n]==v for n,v in before.items())
    added=sorted(set(after)-set(before));assert len(added)==3 and all('ArchivedDictionaryIndexSplit' in n for n in added)
    (N/'r3-class-compatibility.json').write_text(json.dumps(dict(previous_classes=len(before),unchanged=True,added=added)))
c=N/'classes-r3';c.mkdir();cp=str(N/'kernel-r3.jar')+';'+str(c);j=env['JAVA_HOME']+'/bin/';receipt='323fd35a8ceee43545c28a9fb32f55cedd41409a3e1b5fb63e08606380cd24a2'
run('compile-r3-split',[j+'javac.exe','-cp',cp,'-d',c,R/'lab/ArchivedDictionarySplitProbe.java',R/'lab/CapturedDictionarySplitProbe.java'])
base=[j+'java.exe','-Xmx1536m','-cp',cp];a=run('r3-split',base+['ArchivedDictionarySplitProbe',H/'actual-product-v3.snapshot',receipt,H/'archive-70.dbf',H/'archive-71.dbf'])
args=base+['CapturedDictionarySplitProbe',H/'actual-product-v3.snapshot',receipt]
for seq in [70,71]:
    p=H/f'capture/sequence-{seq}.oracap';args.extend([p,hashlib.sha256(p.read_bytes()).hexdigest()])
assert run('r3-captured-split',args).stdout==a.stdout
print('ALLOCATION_AWARE_SPLIT_CLOSURE|events=0',flush=True)
