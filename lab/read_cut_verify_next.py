"""Only changed-path frozen verification: actual SYS KTB, repeated header, and the once-only pending future row."""
from pathlib import Path
import os,json,subprocess,sys
R=Path(__file__).resolve().parent.parent;N=R/'lab/out/read-cut-20261010';raw=N/'central-raw/raw-members/oracle-online-normal'
assert (N/'actual-gap-r2-tests.rc').read_text().strip()=='0'
label=sys.argv[1] if len(sys.argv)>1 else 'next-verified';assert label.replace('-','').isalnum()
core=R/'target/classes';cp=os.pathsep.join([str(core),*[str(p) for p in (N/'diagnostic/libs').glob('*.jar')]])
def run(name,args):
    name=label+'-'+name;assert not (N/(name+'.rc')).exists()
    p=subprocess.run(list(map(str,args)),capture_output=True);(N/(name+'.log')).write_bytes(p.stdout+p.stderr);(N/(name+'.rc')).write_text(str(p.returncode));assert p.returncode==0,name
    return p.stdout.decode('utf8')
run('next-compile',['D:/work/Java/jdk-17.0.7/bin/javac.exe','-cp',cp,'-d',N,R/'lab/ReadCutIndexProbe.java',R/'lab/OnlineProductProbe.java'])
cp=str(N)+os.pathsep+cp
cut=json.loads((N/'cut.json').read_text());baseline=N/'prefix-state/read-cuts'/Path(cut['baselinePath']).name
nextSha='112725650ce3bd18d3c90c9e4b22b03090a860b5296e0f8eb8a6d8a458c84623';oldSha='3112c82f72e267174eef1e4cd27748c661e530df4472470e2c8573dc9994a8d5'
out=run('index-refusal-r3',['D:/work/Java/jdk-17.0.7/bin/java.exe','-Xmx1536m','-cp',cp,'com.streamhouse.oracle.ReadCutIndexProbe',raw/(nextSha+'.raw'),nextSha,baseline,cut['baselineSha256']]);assert 'refused=0|events=0' in out
out=run('next-projection',['D:/work/Java/jdk-17.0.7/bin/java.exe','-Xmx1536m','-cp',cp,'OnlineProductProbe',baseline,cut['baselineSha256'],50359491,raw/(oldSha+'.raw'),raw/(nextSha+'.raw')])
proof=json.loads(out);assert len(proof['events'])==1 and proof['pendingTransactions']==0
event=proof['events'][0];assert event['before']['ID']==1 and event['before']['NOTE']=='anchor'
assert event['after']==dict(event['before'],NOTE='readcut-next-1') and event['metadata']['beforeComplete'] and event['metadata']['afterComplete']
(N/'next-frozen-proof.json').write_text(json.dumps(proof,indent=2));print('PASS actual SYS pair scope; one complete future event from frozen 101/102; source actions=0, target actions=0')
