"""Unified checkpoint hard exit and complete recovered events; frozen sources only."""
import hashlib,json,os,shutil,subprocess
from pathlib import Path
R=Path(__file__).resolve().parent.parent;N=R/'lab/out/unified-recovery-20261007'
assert not N.exists();N.mkdir()
def sha(p):
    with p.open('rb') as f:return hashlib.file_digest(f,'sha256').hexdigest()
def run(name,args,expected=0):
    args=list(map(str,args));assert not (N/(name+'.command.json')).exists();(N/(name+'.command.json')).write_text(json.dumps(args))
    env=dict(os.environ,JAVA_HOME='D:/work/Java/jdk-17.0.7');env['PATH']=env['JAVA_HOME']+'/bin;'+env['PATH']
    p=subprocess.run(args,capture_output=True,timeout=300,cwd=R,env=env)
    for suffix,value in [('stdout.txt',p.stdout),('stderr.txt',p.stderr),('rc.txt',str(p.returncode).encode())]:(N/(name+'.'+suffix)).write_bytes(value)
    assert p.returncode==expected,(name,p.returncode);return p
run('full-build',['D:/work/Java/apache-maven-3.9.11/bin/mvn.cmd','-B','package'])
shutil.copyfile(R/'target/oracle-redo-kernel-0.1.0-SNAPSHOT.jar',N/'kernel.jar');(N/'classes').mkdir()
j='D:/work/Java/jdk-17.0.7/bin/';run('compile',[j+'javac.exe','-cp',N/'kernel.jar','-d',N/'classes',R/'lab/UnifiedRecoveryProbe.java'])
base=[j+'java.exe','-Xmx1536m','-cp',str(N/'classes')+';'+str(N/'kernel.jar'),'UnifiedRecoveryProbe'];cases=[]
for name,folder,seqs,baseline,receipt,reference in [
 ('active','cross-window-active-20261007',[73,74],'current-v3.snapshot','3619a433adcbbb336a67877fdaeba9c8e46fd69a841e3c955d28affb4627d79a',R/'lab/out/cross-window-range-20261007/candidate-closed.stdout.txt'),
 ('historical','v3-product-history-20261006',[70,71],'actual-product-v3.snapshot','323fd35a8ceee43545c28a9fb32f55cedd41409a3e1b5fb63e08606380cd24a2',R/'lab/out/split-history-candidate-20261007/r6/capture-business.stdout.txt')]:
    folder=R/'lab/out'/folder;first=folder/f'capture/sequence-{seqs[0]}.oracap';second=folder/f'capture/sequence-{seqs[1]}.oracap';state=N/(name+'.state')
    prefix=[folder/baseline,receipt,'50342539',first,sha(first)];expanded=prefix+[second,sha(second)]
    p=run(name+'-prefix-halt',base+['prefix',state,'unused',*prefix],23);assert p.stdout.strip()==('UNIFIED_CHECKPOINT|'+sha(state)).encode()
    p=run(name+'-separate-jvm-resume',base+['resume',state,sha(state),*expanded]);assert p.stdout==reference.read_bytes() and p.stdout.count(b'EVENT|')==2
    cases.append(dict(name=name,checkpoint_sha256=sha(state),output_sha256=sha(N/(name+'-separate-jvm-resume.stdout.txt')),reference=str(reference.relative_to(R)),events=2,halt_rc=23))
    original=state.read_bytes();poison=N/(name+'-poison.state');raw=bytearray(original);raw[-2]^=1;poison.write_bytes(raw)
    p=run(name+'-digest-poison',base+['resume',poison,sha(state),*expanded],1);assert not p.stdout and b'HISTORICAL_RECOVERY_DIGEST_MISMATCH' in p.stderr
    p=run(name+'-state-poison',base+['resume',poison,sha(poison),*expanded],1);assert not p.stdout and b'HISTORICAL_RECOVERY_STATE_CHANGED' in p.stderr
    p=run(name+'-wrong-leaf',base+['resume',state,sha(state),*prefix[:2],'50342538',*expanded[3:]],1);assert not p.stdout and b'HISTORICAL_RECOVERY_BASELINE_OR_LEAF' in p.stderr
    changed=N/(name+'-missing-owner.state');lines=original.splitlines(keepends=True);changed.write_bytes(b''.join(line for line in lines if not line.startswith(b'P|' if name=='active' else b'C|')))
    p=run(name+'-missing-owner',base+['resume',changed,sha(changed),*expanded],1);assert not p.stdout and b'HISTORICAL_RECOVERY_STATE_CHANGED' in p.stderr
    changed=N/(name+'-missing-source.state');changed.write_bytes(b''.join(line for line in lines if not line.startswith(b'S|')))
    p=run(name+'-missing-source',base+['resume',changed,sha(changed),*expanded],1);assert not p.stdout and b'HISTORICAL_RECOVERY_SOURCE_PREFIX_CHANGED' in p.stderr
    if name=='active':
        p=run('active-missing-suffix',base+['resume',state,sha(state),*prefix],1);assert not p.stdout and b'INCOMPLETE_TRANSACTION_IDENTITIES:1' in p.stderr
(N/'proof.json').write_text(json.dumps(dict(kernel_sha256=sha(N/'kernel.jar'),cases=cases,source_actions=0,target_actions=0,product_actions=0,raw_prefix_required=True,physical_state_suffix_only=True,business_rows_raw_replayed=True,pending_sys_recovery=False,product_publication_recovery=False),indent=2))
print('UNIFIED_DICTIONARY_IDENTITY_UNDO_RECOVERY_AND_COMPLETE_EVENTS_VERIFIED')
