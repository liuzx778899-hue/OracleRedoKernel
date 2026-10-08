from pathlib import Path
import hashlib,json,subprocess,sys
R=Path(__file__).resolve().parent.parent;O=R/'lab/out/d2-initial-runtime-20261008';S=R/'lab/out/d2-initial-source-20261008';K=R/'lab/out/d2-usage-candidate-20261008/kernel.jar'
label=sys.argv[1];mode=label.split('-')[0];b=json.loads((O/'baseline-reference.json').read_text());inputs=json.loads((S/'archives.json').read_text());state=O/'pending.state'
cp=str(K)+';'+str(O/'probe-classes')
if '--dev' in sys.argv:cp=str(R/'target/classes')+';'+cp
if not (O/'index-leaf.txt').exists():
 obj=next(s.split('|')[4] for s in Path(b['path']).read_text().splitlines() if s.startswith('INDEX|BEFORE|'))
 p=subprocess.run(['D:/work/Java/jdk-17.0.7/bin/java.exe','-cp',cp,'UsageLeafProbe',inputs[0]['path'],obj],capture_output=True,timeout=120)
 (O/'leaf.stdout').write_bytes(p.stdout);(O/'leaf.stderr').write_bytes(p.stderr);assert p.returncode==0,p.stderr.decode();leaves=set(s.split('|')[1] for s in p.stdout.decode().splitlines() if s.startswith('LEAF|'));assert len(leaves)==1; (O/'index-leaf.txt').write_text(next(iter(leaves)))
args=['D:/work/Java/jdk-17.0.7/bin/java.exe','-cp',cp,'D2InitialRawProbe',mode,b['path'],b['sha256'],(O/'index-leaf.txt').read_text(),str(state)]
for i in inputs[:1] if mode=='prefix' else inputs:args.extend([i['path'],i['sha256']])
with (O/(label+'.stdout')).open('xb') as out,(O/(label+'.stderr')).open('xb') as err:p=subprocess.run(args,stdout=out,stderr=err,timeout=180)
text=(O/(label+'.stdout')).read_text();error=(O/(label+'.stderr')).read_text();result=dict(mode=mode,rc=p.returncode,events=sum(s.startswith('EVENT|') for s in text.splitlines()),sha256=hashlib.sha256(text.encode()).hexdigest(),reason=error.splitlines()[0] if error else None)
(O/(label+'.json')).write_text(json.dumps(result,indent=2));print(json.dumps(result));assert p.returncode==0,result
