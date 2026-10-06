"""Build only the frozen raw projection probe; no source replay or accepted-suite rebuild."""
import hashlib,json,shutil,subprocess,sys
from pathlib import Path
R=Path(__file__).resolve().parent.parent;H=R/'lab/out/v3-product-history-20261006';N=R/'lab/out/v3-product-runtime-20261006'
attempt=sys.argv[1] if len(sys.argv)>1 else 'first';assert not (H/(attempt+'-projection.command.json')).exists()
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
assert (H/'capture-reference.json').exists();ref=json.loads((H/'capture-reference.json').read_text());base=ref['baseline']
(H/('executed-'+attempt+'-projection-driver.py')).write_bytes(Path(__file__).read_bytes())
classes=H/'projection-classes';classes.mkdir(exist_ok=True)
libs=H/'jackson';libs.mkdir(exist_ok=True)
for path in (R/'lab/out/v2-dml-capture-20261006/implicit-tail-libs').glob('jackson-*.jar'):
    if not (libs/path.name).exists():shutil.copyfile(path,libs/path.name)
jackson=list(libs.glob('jackson-*.jar'));assert len(jackson)==3
cp=';'.join(map(str,[H/'kernel.jar',H/'adapter.jar',H/'spi.jar',classes,*jackson]));j='D:/work/Java/jdk-17.0.7/bin/'

def run(name,args):
    args=list(map(str,args));assert not (H/(name+'.command.json')).exists();(H/(name+'.command.json')).write_text(json.dumps(args))
    p=subprocess.run(args,capture_output=True,timeout=900)
    for suffix,value in [('stdout.txt',p.stdout),('stderr.txt',p.stderr),('rc.txt',str(p.returncode).encode())]:(H/(name+'.'+suffix)).write_bytes(value)
    assert p.returncode==0,(name,p.returncode,p.stderr[-1500:])
    return p.stdout

run(attempt+'-compile-projection',[j+'javac.exe','-cp',cp,'-d',classes,R/'lab/AcquiredHistoricalProjectionProbe.java'])
args=[j+'java.exe','-Xmx1536m','-cp',cp,'AcquiredHistoricalProjectionProbe',H/'actual-product-v3.snapshot',base['sha256'],'50342539']
for seq in ref['sequences']:
    path=H/'capture'/('sequence-'+str(seq)+'.oracap');args.extend([path,sha(path)])
value=json.loads(run(attempt+'-projection',args));assert len(value['envelopes'])==len(value['evidence']['events'])==2
assert value['evidence']['baselineSha256']==base['sha256'] and value['evidence']['baselineScn']==base['scn']
(H/'actual-product-history-projection.json').write_text(json.dumps(value,indent=2))
print('RAW_FACTORY_HISTORICAL_PROJECTION_COMPLETE|2')
