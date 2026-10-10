"""Pin the two actual refusals without restarting Oracle or replaying any source operation."""
from pathlib import Path
import hashlib,json,subprocess,struct
R=Path(__file__).resolve().parent.parent;N=R/'lab/out/read-cut-20261010'
raw=N/'central-raw/raw-members/oracle-online-normal';first='3112c82f72e267174eef1e4cd27748c661e530df4472470e2c8573dc9994a8d5';changed='27f0839cdc56ed1ec0519ddd688e8e48993c33bca82cefe8a1fc7a319c983b04'
with (raw/(first+'.raw')).open('rb') as a,(raw/(changed+'.raw')).open('rb') as b:
    left=a.read(1024);right=b.read(1024);nab=struct.unpack_from('<I',left,668)[0];assert nab==struct.unpack_from('<I',right,668)[0]
    diffs=[(i,left[i],right[i]) for i in range(1024) if left[i]!=right[i]]
    length=(nab-2)*512;bodya=a.read(length);bodyb=b.read(length)
    result=dict(firstSha256=first,changedSha256=changed,sequence=101,nab=nab,headerDifferences=diffs,usedBodyEqual=bodya==bodyb,usedBodySha256=hashlib.sha256(bodya).hexdigest())
    (N/'generation-conflict-diagnostic.json').write_text(json.dumps(result,indent=2));print(json.dumps(result))
jar=R/'target/oracle-redo-kernel-0.1.0-SNAPSHOT.jar'
compile=subprocess.run(['D:/work/Java/jdk-17.0.7/bin/javac.exe','-cp',str(jar),'-d',str(N),str(R/'lab/ReadCutIndexProbe.java')],capture_output=True)
(N/'index-refusal-r2-probe-compile.log').write_bytes(compile.stdout+compile.stderr);(N/'index-refusal-r2-probe-compile.rc').write_text(str(compile.returncode));assert compile.returncode==0
cut=json.loads((N/'cut.json').read_text());baseline=N/'prefix-state/read-cuts'/Path(cut['baselinePath']).name
sha='112725650ce3bd18d3c90c9e4b22b03090a860b5296e0f8eb8a6d8a458c84623'
run=subprocess.run(['D:/work/Java/jdk-17.0.7/bin/java.exe','-Xmx1536m','-cp',str(N)+';'+str(jar),'com.streamhouse.oracle.ReadCutIndexProbe',str(raw/(sha+'.raw')),sha,str(baseline),cut['baselineSha256']],capture_output=True)
(N/'index-refusal-r2-probe.log').write_bytes(run.stdout+run.stderr);(N/'index-refusal-r2-probe.rc').write_text(str(run.returncode));print(run.returncode);print(run.stdout.decode('utf8')[-2200:])
