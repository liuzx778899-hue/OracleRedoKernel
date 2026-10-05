"""Frozen commit-SCN complete SYS images, independent rulers only; source transactions are never replayed."""
import hashlib,json,shutil,subprocess
from pathlib import Path
R=Path(__file__).resolve().parent.parent;O=R/'lab/out/sys-full-history-research-20261006';S='oraredo-19c-lab-20261004';A=R/'lab/out/oracle-snapshot-acquisition-20261005'
assert subprocess.check_output(['docker','inspect',S,'--format','{{.Config.Labels.owner}}|{{.State.Status}}']).decode().strip()=='OracleRedoKernel|running'
O.mkdir(exist_ok=False);(O/'classes').mkdir();shutil.copyfile(Path(__file__),O/'executed-history-driver.py');shutil.copyfile(R/'lab/SysDictionaryBaselineResearchProbe.java',O/'executed-history-probe.java')
shutil.copyfile(A/'source.secret',O/'source.secret');shutil.copyfile(A/'ojdbc8.jar',O/'ojdbc8.jar')
def run(name,args):
    args=list(map(str,args));(O/(name+'.command.json')).write_text(json.dumps(args));p=subprocess.run(args,capture_output=True,timeout=180)
    for suffix,data in [('stdout.txt',p.stdout),('stderr.txt',p.stderr),('rc.txt',str(p.returncode).encode())]:(O/(name+'.'+suffix)).write_bytes(data)
    assert p.returncode==0,p.stderr.decode();return p.stdout.decode()
run('compile',['D:/work/Java/jdk-17.0.7/bin/javac.exe','-encoding','UTF-8','-d',O/'classes',R/'lab/SysDictionaryBaselineResearchProbe.java'])
ends=json.loads((R/'lab/out/sys-control-candidate-20261006/native-end-proof.json').read_text())['cases'];proof=[]
for end in ends:
    scn=end['end_scn'];sub=O/str(scn);sub.mkdir()
    text=run('full-sys-'+str(scn),['docker','run','--rm','--label','owner=OracleRedoKernel','--label','scope=sys-full-history-research','--network','container:'+S,'--mount','type=bind,source='+str(O)+',target=/evidence','--entrypoint','java','flink:1.18-java17','-cp','/evidence/classes:/evidence/ojdbc8.jar','SysDictionaryBaselineResearchProbe','/evidence/source.secret',scn,75185,75186,'/evidence/'+str(scn)])
    proof.append(dict(xid=end['xid'],scn=scn,full_rows=sum(l.startswith('SYS_ROW|') for l in text.splitlines()),cells=sum(l.startswith('CELL|') for l in text.splitlines()),history_authority=False))
(O/'full-history-query-proof.json').write_text(json.dumps(dict(commits=proof,business_ddl_dml=0,source_config_changes=0,events=0),indent=2));print(json.dumps(proof))
