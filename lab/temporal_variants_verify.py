"""Compare frozen raw row bytes against independent SQL DUMP and semantic truth."""
from pathlib import Path
import hashlib,json,re,subprocess,sys
R=Path(__file__).resolve().parent.parent
BASE=R/'lab/out/temporal-variants-20261011'
SOURCE=BASE/'source-r2';N=BASE/sys.argv[1];N.mkdir(exist_ok=False)
KERNEL=R/'target/oracle-redo-kernel-0.1.0-SNAPSHOT.jar'
JAVA=Path('D:/work/Java/jdk-17.0.7/bin')
sha=lambda p:hashlib.sha256(Path(p).read_bytes()).hexdigest()
def run(name,args):
    p=subprocess.run(list(map(str,args)),capture_output=True,cwd=R)
    for suffix,data in [('stdout',p.stdout),('stderr',p.stderr),('rc',str(p.returncode).encode())]:
        (N/(name+'.'+suffix)).write_bytes(data)
    assert p.returncode==0,name
    return p.stdout.decode()
archives=json.loads((SOURCE/'archives.json').read_text());assert len(archives)==1
raw=Path(archives[0]['path']);assert sha(raw)==archives[0]['sha256']
run('compile',[JAVA/'javac.exe','-cp',KERNEL,'-d',N,R/'lab/TemporalVariantsProbe.java'])
output=run('raw-values',[JAVA/'java.exe','-Xmx1024m','-cp',str(N)+';'+str(KERNEL),'TemporalVariantsProbe',raw,80549])
cols=['D','TS0','TS3','TS6','TS9','TZ','TZR','LTZ'];independent={}
for col in cols:
    for line in (SOURCE/('bytes-'+col+'.stdout')).read_text().splitlines():
        if not line.startswith('BYTES|'):continue
        _,id,name,dump=line.split('|');values=dump.split(': ',1)[1].split(',')
        independent[(id,name)]=bytes(int(b.strip(),16) for b in values).hex()
actual={}
for line in output.splitlines():
    if not line.startswith('ROW|'):continue
    parts=line.split('|')
    for col,value in zip(cols,parts[2:],strict=True):actual[(parts[1],col)]=value
assert actual==independent and len(actual)==24
truth={line.split('|')[1]:line.split('|')[2:] for line in (SOURCE/'truth.stdout').read_text().splitlines() if line.startswith('TRUTH|')}
def civil(text):
    date,time,era=text.split();year,month,day=map(int,date.split('-'))
    year=year if era=='AD' else 1-year
    hour,minute,seconds=time.split(':');second,_,fraction=seconds.partition('.')
    return year,month,day,int(hour),int(minute),int(second),int(fraction.ljust(9,'0') or '0')
def iso(text):
    m=re.fullmatch(r'(-?\d+)-(\d+)-(\d+)T(\d+):(\d+)(?::(\d+)(?:\.(\d+))?)?',text)
    assert m,text
    y,mo,d,h,mi,s,f=m.groups()
    return int(y),int(mo),int(d),int(h),int(mi),int(s or '0'),int((f or '').ljust(9,'0') or '0')
semantic=0
for line in output.splitlines():
    if not line.startswith('VALUE|'):continue
    _,id,col,value=line.split('|')
    if col=='TZ':
        expected=truth[id][5].replace(' ','T',1).replace(' ','')
        expected=expected.replace('+00:00','Z')
        assert value==expected,(value,expected)
    else:assert iso(value)==civil(truth[id][cols.index(col)]),(id,col,value)
    semantic+=1
assert semantic==18
proof=dict(rawSha256=sha(raw),kernelSha256=sha(KERNEL),rawRows=3,rawByteValues=24,byteDifferences=0,
    scalarValues=18,semanticDifferences=0,sourceStopped=(SOURCE/'source-stop.rc').read_text().strip()=='0',
    completeEventChain=False,regionTimezoneIntegrated=False,localTimezoneIntegrated=False,productIntegrated=False)
(N/'proof.json').write_text(json.dumps(proof,indent=2));print(json.dumps(proof))
