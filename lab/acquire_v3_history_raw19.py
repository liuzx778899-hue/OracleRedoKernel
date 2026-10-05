"""Fresh owned-source V3 acquisition before new controlled history; no DDL/DML in this driver."""
import hashlib,json,shutil,subprocess
from pathlib import Path
R=Path(__file__).resolve().parent.parent;O=R/'lab/out/v3-historical-raw-20261006';B=R/'lab/out/dictionary-baseline-candidate-20261006';S='oraredo-19c-lab-20261004'
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
assert subprocess.check_output(['docker','inspect',S,'--format','{{.Config.Labels.owner}}|{{.State.Status}}']).decode().strip()=='OracleRedoKernel|running'
O.mkdir(exist_ok=True);assert not (O/'baseline-reference.json').exists() and not (O/'actual-v3.snapshot').exists()
round=1+len(list(O.glob('executed-acquisition-driver-r*.py')))
shutil.copyfile(Path(__file__),O/f'executed-acquisition-driver-r{round}.py')
for name,digest in [('kernel','58a6e56ef7844bb58da94e06f5ae98fe7a63b2f347b60f02ffa071a095812ade'),('adapter','e095822b13e843e45d761d529f0f10c9e1313ce19aeaf2e4ecdb6fa284e9ae1e'),('spi','fb9c443ba88e49b656d97a4bd881c31a3c9fef305bf9d5a503c13364ed940b95')]:
    source=R/'lab/out/sys-history-kernel-candidate-20261006/kernel.jar' if name=='kernel' else B/(name+'.jar')
    assert sha(source)==digest;shutil.copyfile(source,O/(name+'.jar'))
for name in ['ojdbc8.jar','source.secret']:shutil.copyfile(B/name,O/name)
def run(name,args,body=None):
    name+=f'-r{round}';args=list(map(str,args));assert not (O/(name+'.command.json')).exists();(O/(name+'.command.json')).write_text(json.dumps(args));p=subprocess.run(args,input=body,capture_output=True,timeout=180)
    for suffix,data in [('stdout.txt',p.stdout),('stderr.txt',p.stderr),('rc.txt',str(p.returncode).encode())]:(O/(name+'.'+suffix)).write_bytes(data)
    assert p.returncode==0,p.stderr.decode();return p.stdout.decode()
body="""whenever sqlerror exit failure rollback
whenever oserror exit failure rollback
set pages 0 lines 32767 feedback off heading off trimspool on
select 'SOURCE|'||dbid||'|'||activation#||'|'||resetlogs_id||'|'||resetlogs_change#||'|'||supplemental_log_data_min||'|'||supplemental_log_data_all from v$database cross join (select resetlogs_id from v$database_incarnation where status='CURRENT');
select 'CURRENT|'||sequence#||'|'||first_change# from v$log where status='CURRENT';
alter session set container=ORKPDB;
select 'PDB|'||con_uid||'|'||con_id||'|'||name from v$containers where con_id=to_number(sys_context('USERENV','CON_ID'));
select 'OLD|'||ID||'|'||TXT||'|'||NOTE from ORKLAB.T_BASELINE_20261004;
select 'SEED|'||ID||'|'||TXT||'|'||nvl(NOTE,'<NULL>') from ORKLAB.T_V2_HIST_20261006 order by ID;
select 'ROLE|'||object_name||'|'||object_id||'|'||data_object_id from dba_objects where owner='ORKLAB' and object_name in ('T_V2_HIST_20261006','PK_V2_HIST_20261006') order by object_name;
select 'COL|'||column_name||'|'||segment_column_id||'|'||internal_column_id||'|'||hidden_column from dba_tab_cols where owner='ORKLAB' and table_name='T_V2_HIST_20261006' order by segment_column_id;
exit success
"""
(O/f'preflight-r{round}.sql').write_text(body);text=run('preflight',['docker','exec','-i',S,'sqlplus','-s','/','as','sysdba'],body.encode());assert 'SP2-' not in text
lines=[l.strip() for l in text.splitlines() if l.strip()]
assert 'SOURCE|1276726821|1276717861|1245693159|1920977|NO|NO' in lines and 'PDB|677737149|3|ORKPDB' in lines and 'OLD|11|blocking-closed|new-note' in lines
assert [l for l in lines if l.startswith('SEED|')]==['SEED|0|dictionary-restored|anchor'],lines
assert [l for l in lines if l.startswith('ROLE|')]==['ROLE|PK_V2_HIST_20261006|75186|75186','ROLE|T_V2_HIST_20261006|75185|75185']
assert [l for l in lines if l.startswith('COL|')]==['COL|ID|1|1|NO','COL|TXT|2|2|NO','COL|NOTE|3|3|YES']
(O/'classes').mkdir(exist_ok=True);cp=';'.join(str(O/(name+'.jar')) for name in ['kernel','adapter','spi','ojdbc8'])
run('compile-acquisition',['D:/work/Java/jdk-17.0.7/bin/javac.exe','-encoding','UTF-8','-cp',cp,'-d',O/'classes',R/'lab/OracleSnapshotAcquisitionProbe.java'])
text=run('actual-v3',['docker','run','--rm','--label','owner=OracleRedoKernel','--label','scope=new-v3-history-acquisition','--network','container:'+S,'--mount','type=bind,source='+str(O)+',target=/evidence','--entrypoint','java','flink:1.18-java17','-cp','/evidence/classes:/evidence/kernel.jar:/evidence/adapter.jar:/evidence/spi.jar:/evidence/ojdbc8.jar','OracleSnapshotAcquisitionProbe','/evidence/source.secret','v3-history','ORKLAB','T_V2_HIST_20261006','PK_V2_HIST_20261006','history-normal','/evidence/actual-v3.snapshot'])
q=text.strip().split('|');assert q[:2]==['ACQUIRED','v3-history'] and q[4:]==['3','1','1'] and q[3]==sha(O/'actual-v3.snapshot')
current=next(l.split('|') for l in lines if l.startswith('CURRENT|'));assert int(current[2])<=int(q[2])
proof=dict(path=str(O/'actual-v3.snapshot'),snapshot='lab/out/v3-historical-raw-20261006/actual-v3.snapshot',sha256=q[3],scn=int(q[2]),start_sequence=int(current[1]),source_actions=0,target_actions=0,events=0,history_dml_accepted=False)
(O/'baseline-reference.json').write_text(json.dumps(proof,indent=2));print(json.dumps(proof))
