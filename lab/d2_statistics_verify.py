"""Read-only frozen-input verification. Does not replay source actions or acquire a baseline."""
from pathlib import Path
import hashlib,json,subprocess,sys
R=Path(__file__).resolve().parent.parent;N=R/'lab/out/d2-statistics-runtime-20261008';S=R/'lab/out/d2-statistics-source-20261008'
usage='--usage' in sys.argv
if usage:
    sys.argv.remove('--usage');N=R/'lab/out/d2-usage-runtime-20261008';S=R/'lab/out/d2-usage-source-20261008'
label=sys.argv[1];mode=label.split('-')[0];assert mode in ['prefix','closed','restored']
b=json.loads((N/'baseline-reference.json').read_text());state=N/'pending.state'
if mode=='prefix':
    raw=sorted(S.glob('*.raw'),key=lambda p:int(p.stem))[:1];assert len(raw)==1,raw
    inputs=[dict(path=str(raw[0]),sha256=hashlib.sha256(raw[0].read_bytes()).hexdigest())]
else:inputs=json.loads((S/'archives.json').read_text())
args=['D:/work/Java/jdk-17.0.7/bin/java.exe','-Dork.indexDba='+(N/'index-leaf.txt').read_text(),'-cp',str(R/('lab/out/d2-usage-candidate-20261008/kernel.jar' if usage else 'lab/out/d2-statistics-candidate-20261008/kernel.jar'))+';'+str(N/'probe-classes'),'D2PendingSysRawProbe',mode,str(state),hashlib.sha256(state.read_bytes()).hexdigest() if mode=='restored' else '-',b['path'],b['sha256']]
if '--development-classes' in sys.argv:args[3]=str(R/'target/classes')+';'+args[3]
for item in inputs:args.extend([item['path'],item['sha256']])
with (N/(label+'.stdout')).open('xb') as out,(N/(label+'.stderr')).open('xb') as err:p=subprocess.run(args,stdout=out,stderr=err,timeout=180)
text=(N/(label+'.stdout')).read_text();error=(N/(label+'.stderr')).read_text()
result=dict(mode=mode,rc=p.returncode,events=sum(l.startswith('EVENT|') for l in text.splitlines()),stdoutSha256=hashlib.sha256(text.encode()).hexdigest(),reason=error.splitlines()[0] if error else None)
(N/(label+'.result.json')).write_text(json.dumps(result,indent=2));print(json.dumps(result));print(text)
assert p.returncode==0,result
if mode!='prefix':assert result['events']==2,result
if mode=='restored':
    closed=[p for p in N.glob('closed*.result.json') if json.loads(p.read_text())['rc']==0]
    assert len(closed)==1,closed
    assert (N/closed[0].name.replace('.result.json','.stdout')).read_bytes()==(N/(label+'.stdout')).read_bytes()
