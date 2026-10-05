"""Read-only full SYS baseline experiment in the owned source namespace; never backfill old authority."""
import hashlib,json,shutil,subprocess,sys
from pathlib import Path
R=Path(__file__).resolve().parent.parent;O=R/'lab/out/sys-full-baseline-research-20261006';A=R/'lab/out/oracle-snapshot-acquisition-20261005';S='oraredo-19c-lab-20261004'
assert subprocess.check_output(['docker','inspect',S,'--format','{{.Config.Labels.owner}}|{{.State.Status}}']).decode().strip()=='OracleRedoKernel|running'
round=int(sys.argv[1]);previous=round-1
assert not (O/f'research-r{round}.command.json').exists()
for name in ['research-query.sql','partial-results.txt']:shutil.copyfile(O/name,O/(f'failed-r{previous}-'+name))
shutil.copyfile(R/'lab/SysDictionaryBaselineResearchProbe.java',O/f'executed-research-probe-r{round}.java')
shutil.copyfile(Path(__file__),O/f'executed-research-driver-r{round}.py')
shutil.copyfile(A/'source.secret',O/'source.secret');shutil.copyfile(A/'ojdbc8.jar',O/'ojdbc8.jar')
def run(name,args):
    args=list(map(str,args));(O/(name+'.command.json')).write_text(json.dumps(args));p=subprocess.run(args,capture_output=True,timeout=180)
    for suffix,data in [('stdout.txt',p.stdout),('stderr.txt',p.stderr),('rc.txt',str(p.returncode).encode())]:(O/(name+'.'+suffix)).write_bytes(data)
    assert p.returncode==0,p.stderr.decode();return p
run(f'compile-r{round}',['D:/work/Java/jdk-17.0.7/bin/javac.exe','-encoding','UTF-8','-d',O/'classes',R/'lab/SysDictionaryBaselineResearchProbe.java'])
args=['docker','run','--rm','--label','owner=OracleRedoKernel','--label','scope=full-sys-baseline-research','--network','container:'+S,'--mount','type=bind,source='+str(O)+',target=/evidence','--entrypoint','java','flink:1.18-java17','-cp','/evidence/classes:/evidence/ojdbc8.jar','SysDictionaryBaselineResearchProbe','/evidence/source.secret',2522819,75185,75186,'/evidence']
run(f'research-r{round}',args)
print('Frozen full SYS baseline research recorded; old-SCN research only, no source DML or history permission.')
