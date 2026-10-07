"""Dictionary receipt recovery and complete events from frozen raw bytes, in new JVMs."""
import hashlib,json,os,shutil,subprocess
from pathlib import Path
R=Path(__file__).resolve().parent.parent;N=R/'lab/out/cross-window-dictionary-20261007'
assert not N.exists();N.mkdir(parents=True)
def sha(p):
    with p.open('rb') as f:return hashlib.file_digest(f,'sha256').hexdigest()
def run(name,args,expected=0):
    args=list(map(str,args));assert not (N/(name+'.command.json')).exists()
    (N/(name+'.command.json')).write_text(json.dumps(args))
    env=dict(os.environ,JAVA_HOME='D:/work/Java/jdk-17.0.7');env['PATH']=env['JAVA_HOME']+'/bin;'+env['PATH']
    p=subprocess.run(args,capture_output=True,timeout=300,env=env,cwd=R)
    for suffix,value in [('stdout.txt',p.stdout),('stderr.txt',p.stderr),('rc.txt',str(p.returncode).encode())]:(N/(name+'.'+suffix)).write_bytes(value)
    assert p.returncode==expected,(name,p.returncode)
    return p
run('full-build',['D:/work/Java/apache-maven-3.9.11/bin/mvn.cmd','-B','package'])
shutil.copyfile(R/'target/oracle-redo-kernel-0.1.0-SNAPSHOT.jar',N/'kernel.jar');(N/'classes').mkdir()
j='D:/work/Java/jdk-17.0.7/bin/'
run('compile-probe',[j+'javac.exe','-cp',N/'kernel.jar','-d',N/'classes',R/'lab/DictionaryResumeProbe.java'])
base=[j+'java.exe','-Xmx1536m','-cp',str(N/'classes')+';'+str(N/'kernel.jar'),'DictionaryResumeProbe']
proof=[]
for name,folder,seqs,baseline,receipt,reference,expected in [
 ('historical','v3-product-history-20261006',[70,71],'actual-product-v3.snapshot','323fd35a8ceee43545c28a9fb32f55cedd41409a3e1b5fb63e08606380cd24a2',R/'lab/out/split-history-candidate-20261007/r6/capture-business.stdout.txt',b'DICTIONARY_CHECKPOINT|2|4'),
 ('active','cross-window-active-20261007',[73,74],'current-v3.snapshot','3619a433adcbbb336a67877fdaeba9c8e46fd69a841e3c955d28affb4627d79a',R/'lab/out/cross-window-range-20261007/candidate-closed.stdout.txt',b'DICTIONARY_CHECKPOINT|0|3')]:
    folder=R/'lab/out'/folder;state=N/(name+'.state');first=folder/f'capture/sequence-{seqs[0]}.oracap';second=folder/f'capture/sequence-{seqs[1]}.oracap'
    args=[folder/baseline,receipt,'50342539',first,sha(first)]
    p=run(name+'-prefix-halt',base+['prefix',state,'unused',*args],23);assert p.stdout.strip()==expected
    expanded=args+[second,sha(second)];resume=base+['resume',state,sha(state),*expanded]
    p=run(name+'-complete-resume',resume);assert p.stdout==reference.read_bytes()
    poison=N/(name+'-poison.state');raw=bytearray(state.read_bytes());raw[-2]^=1;poison.write_bytes(raw)
    p=run(name+'-digest-poison',base+['resume',poison,sha(state),*expanded],1);assert not p.stdout and b'DICTIONARY_CHECKPOINT_DIGEST_MISMATCH' in p.stderr
    p=run(name+'-state-poison',base+['resume',poison,sha(poison),*expanded],1)
    assert not p.stdout and (b'DICTIONARY_CHECKPOINT_STATE_CHANGED' in p.stderr or b'DICTIONARY_CHECKPOINT_SOURCE_PREFIX_CHANGED' in p.stderr)
    duplicate=N/(name+'-duplicate.state');duplicate.write_bytes(state.read_bytes()+b'C|duplicate\n')
    p=run(name+'-duplicate-commit',base+['resume',duplicate,sha(duplicate),*expanded],1);assert not p.stdout and b'DICTIONARY_CHECKPOINT_STATE_CHANGED' in p.stderr
    truncated=N/(name+'-truncated.state');truncated.write_bytes(state.read_bytes()[:-1])
    p=run(name+'-noncanonical',base+['resume',truncated,sha(truncated),*expanded],1);assert not p.stdout and b'DICTIONARY_CHECKPOINT_STATE_CHANGED' in p.stderr
    proof.append(dict(name=name,checkpoint_sha256=sha(state),output_sha256=sha(N/(name+'-complete-resume.stdout.txt')),events=p.stdout.count(b'EVENT|'),byte_equal_reference=str(reference.relative_to(R)),halt_rc=23))
    proof[-1]['events']=2
for path in ['src/main/java/com/streamhouse/oracle/ArchivedDictionaryHistory.java','src/main/java/com/streamhouse/oracle/CapturedHistoricalHeapBatch.java','lab/DictionaryResumeProbe.java','lab/verify_dictionary_resume19.py']:shutil.copyfile(R/path,N/Path(path).name)
(N/'proof.json').write_text(json.dumps(dict(kernel_sha256=sha(N/'kernel.jar'),cases=proof,source_actions=0,target_actions=0,product_actions=0,incremental_pending_sys_recovery=False,raw_prefix_required=True,full_publication_recovery=False),indent=2))
print('DICTIONARY_RECEIPT_RECOVERY_AND_COMPLETE_EVENTS_VERIFIED')
