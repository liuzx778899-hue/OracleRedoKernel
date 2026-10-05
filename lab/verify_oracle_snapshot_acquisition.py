"""Real read-only source acquisition, then independent fixed-SCN SQL/byte comparison."""
import hashlib,json,shutil,subprocess
from pathlib import Path
R=Path(__file__).resolve().parent.parent;O=R/'lab/out/oracle-snapshot-acquisition-20261005';S='oraredo-19c-lab-20261004'
def run(name,args,body=None):
 assert not (O/(name+'.rc.txt')).exists();(O/(name+'.command.json')).write_text(json.dumps(list(map(str,args))));p=subprocess.run(list(map(str,args)),input=body,capture_output=True,timeout=120)
 (O/(name+'.stdout.txt')).write_bytes(p.stdout);(O/(name+'.stderr.txt')).write_bytes(p.stderr);(O/(name+'.rc.txt')).write_text(str(p.returncode));assert p.returncode==0,name;return p.stdout
proof=json.loads((O/'build-proof.json').read_text());assert proof['tests']==dict(tests=19,errors=0,failures=0,skipped=0)
for n in ['adapter','kernel','spi']:assert hashlib.sha256((O/(n+'.jar')).read_bytes()).hexdigest()==proof[n+'_sha256']
shutil.copyfile(R/'lab/out/oracle-readonly-receipt-20261005/ojdbc8.jar',O/'ojdbc8.jar');classes=O/'classes';classes.mkdir();cp=';'.join(str(O/(n+'.jar')) for n in ['adapter','kernel','spi','ojdbc8']);run('compile-probe',['D:/work/Java/jdk-17.0.7/bin/javac.exe','-encoding','UTF-8','-cp',cp,'-d',classes,R/'lab/OracleSnapshotAcquisitionProbe.java'])
(O/'execution-pin.json').write_text(json.dumps(dict(source_commit=subprocess.check_output(['git','rev-parse','HEAD']).decode().strip(),files=[dict(path=p.relative_to(R).as_posix(),sha256=hashlib.sha256(p.read_bytes()).hexdigest()) for p in list(classes.glob('*'))+[R/'lab/OracleSnapshotAcquisitionProbe.java',O/'adapter.jar',O/'kernel.jar',O/'spi.jar',O/'ojdbc8.jar']]),indent=2))
cases=json.loads((O/'cases.json').read_text());jobs=[]
for c in cases:jobs.append((c['case'],c['owner'],c['table'],c['index'],'normal'))
a=cases[0];jobs += [('wrong-source',a['owner'],a['table'],a['index'],'wrong-source'),('old-transaction',a['owner'],a['table'],a['index'],'old-transaction'),('wrong-index',a['owner'],a['table'],cases[1]['index'],'wrong-index'),('missing-table-read','ORKLAB','T_BASELINE_20261004','PK_BASELINE_20261004','missing-table-read')]
for name,owner,table,index,mode in jobs:
 args=['docker','run','--rm','--label','owner=OracleRedoKernel','--label','scope=oracle-source-snapshot','--network','container:'+S,'--mount','type=bind,source='+str(O)+',target=/evidence','--entrypoint','java','flink:1.18-java17','-cp','/evidence/classes:/evidence/adapter.jar:/evidence/kernel.jar:/evidence/spi.jar:/evidence/ojdbc8.jar','OracleSnapshotAcquisitionProbe','/evidence/source.secret',name,owner,table,index,mode,'/evidence/'+name+'.snapshot.txt'];run(name,args)
print('SOURCE_SNAPSHOT_REAL_JDBC_RUN_COMPLETE|3|4_REFUSALS')
