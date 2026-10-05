"""New owned targets exercise null sides using frozen inputs and existing independent checks."""
import hashlib,json,shutil,subprocess
from pathlib import Path
R=Path(__file__).resolve().parent.parent
O=R/'lab/out/oracle-null-sides-target-20261005'
OLD=R/'lab/out/oracle-dml-target-20261005'
B=R/'lab/out/release-oracle-null-sides-20261005'
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
O.mkdir(parents=True,exist_ok=False)
proof=json.loads((B/'proof.json').read_text())
for n in ['adapter','kernel','spi']:
    assert sha(B/(n+'.jar'))==proof[n+'_sha256']
    shutil.copyfile(B/(n+'.jar'),O/(n+'.jar'))
shutil.copyfile(OLD/'ojdbc8.jar',O/'ojdbc8.jar')
assert sha(O/'ojdbc8.jar')==sha(R/'lab/out/oracle-target-20261005/ojdbc8.jar')
def run(n,args):
    args=list(map(str,args));(O/(n+'.command.json')).write_text(json.dumps(args))
    p=subprocess.run(args,capture_output=True,timeout=240)
    for ext,v in [('stdout',p.stdout),('stderr',p.stderr)]: (O/(n+'.'+ext+'.txt')).write_bytes(v)
    (O/(n+'.rc.txt')).write_text(str(p.returncode));assert p.returncode==0,(n,p.stderr)
    return p.stdout
classes=O/'classes';classes.mkdir()
cp=';'.join(str(O/(n+'.jar')) for n in ['adapter','kernel','spi','ojdbc8'])+';'+str(classes)
run('compile',['D:/work/Java/jdk-17.0.7/bin/javac.exe','-encoding','UTF-8','-cp',cp,'-d',classes,*[R/'lab'/n for n in ['OracleDmlCheckpointWorker.java','OracleProjectionProbe.java','OracleEnvelopeSidesProbe.java']]])
configs=json.loads((OLD/'config.json').read_text());sides=[]
for c in configs:
    old=c['prefix'];new={'DNON':'SNON','DNUL':'SNUL','DMIX':'SMIX'}[old]
    shutil.copytree(OLD/old,O/new);c['prefix']=new
    for i in range(0,len(c['captures']),2):
        c['captures'][i]=str(O/new/Path(c['captures'][i]).name)
        assert sha(Path(c['captures'][i]))==c['captures'][i+1]
    # Only input files are retained; previous consumer state must not seed this new target.
    for p in (O/new).iterdir():
        if p.is_file() and p.suffix!='.oracap':p.unlink()
    a=[O/new/'baseline/snapshot.stdout.txt',c['baseline_sha256'],c['leaf']]
    assert sha(a[0])==a[1]
    text=run(new+'-inspect',['D:/work/Java/jdk-17.0.7/bin/java.exe','-cp',cp,'OracleDmlCheckpointWorker','inspect','unused',O/new,new,*a,str(len(c['windows'])),*c['captures']])
    assert text==(OLD/(old+'-inspect.stdout.txt')).read_bytes(),'projection hashes/images drifted'
    side=run(new+'-sides',['D:/work/Java/jdk-17.0.7/bin/java.exe','-cp',cp,'OracleEnvelopeSidesProbe',*a,*c['captures']]).decode().splitlines()
    assert len(side)==c['expected_events']
    assert [int(s.split('|')[-1]) for s in side]==([2,2] if new=='SNUL' else [0]*c['expected_events'])
    sides+=side
(O/'config.json').write_text(json.dumps(configs,indent=2))
# Reuse the accepted target driver and independent raw comparator, with only owned scope renamed.
sources=[]
for name in ['run_oracle_dml_target.py','verify_oracle_dml_target.py']:
    source=R/'lab'/name;text=source.read_text()
    for before,after in [('oracle-dml-target-20261005','oracle-null-sides-target-20261005'),('DNON','SNON'),('DNUL','SNUL'),('DMIX','SMIX'),('DFAIL','SFAIL'),('scope=oracle-dml-target','scope=oracle-null-sides-target')]:text=text.replace(before,after)
    derived=O/name;derived.write_text(text)
    sources.append({'source':str(source.relative_to(R)),'sha256':sha(source),'executed_sha256':sha(derived)})
    # Preserve the original repository-root resolution without copying product/research code into Git.
    exec(compile(text,str(derived),'exec'),{'__file__':str(source),'__name__':'__main__'})
(O/'sides-proof.json').write_text(json.dumps({'sides':sides,'projection_byte_equal':True,'driver_sources':sources,'adapter_sha256':proof['adapter_sha256'],'source_dml':0,'product_gate_accepted':False},indent=2))
print('NULL_SIDES_TARGET_VERIFIED')
