"""New/upgrade owned controls; real Spring/MyBatis transactions and independent target proof, no target DML."""
import hashlib,json,shutil,subprocess,sys,zipfile
from pathlib import Path
R=Path(__file__).resolve().parent.parent;P=R/'lab/out/streamhouse-oracle-gate-worktree';O=R/'lab/out/oracle-control-publication-20261005';B=R/'lab/out/oracle-readonly-receipt-20261005';T=R/'lab/out/oracle-target-20261005';F=R/'lab/out/oracle-null-sides-target-20261005';E=R/'lab/out/oracle-empty-target-20261005'
resume=sys.argv[1:]==['--resume'];compile_repair=sys.argv[1:]==['--resume-compile'];suffix='-r2' if resume or compile_repair else ''
assert (O/'build-proof-r3.json').exists()
if resume:assert (O/'matrix.rc.txt').read_text()=='1'
if compile_repair:assert (O/'compile.rc.txt').read_text()=='1' and not (O/'matrix.rc.txt').exists()
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
def run(n,args,data=None):
    args=list(map(str,args));assert not (O/(n+'.rc.txt')).exists();(O/(n+'.command.json')).write_text(json.dumps(args))
    with (O/(n+'.stdout.txt')).open('wb') as out,(O/(n+'.stderr.txt')).open('wb') as err:p=subprocess.run(args,input=data,stdout=out,stderr=err,timeout=900)
    (O/(n+'.rc.txt')).write_text(str(p.returncode));assert p.returncode==0,n
proof=json.loads((B/'build-proof.json').read_text())
for n in ['adapter','kernel']:
    assert sha(B/(n+'.jar'))==proof[n+'_sha256']
    if not resume and not compile_repair:shutil.copyfile(B/(n+'.jar'),O/(n+'.jar'))
    else:assert sha(O/(n+'.jar'))==proof[n+'_sha256']
libs=O/('libs'+suffix);libs.mkdir()
with zipfile.ZipFile(P/'server/streamhouse-app/target/streamhouse-app-0.1.0-SNAPSHOT.jar') as z:
    for n in z.namelist():
        if n.startswith('BOOT-INF/lib/') and n.endswith('.jar') and not Path(n).name.startswith('streamhouse-'):(libs/Path(n).name).write_bytes(z.read(n))
classes=O/('classes'+suffix);classes.mkdir();modules=[d.name for d in (P/'server').iterdir() if (d/'target/classes').exists()]
cp=';'.join([str(classes)]+[str(P/'server'/d/'target/classes') for d in modules]+[str(O/(n+'.jar')) for n in ['adapter','kernel']]+[str(libs/'*')])
run('compile'+suffix,['D:/work/Java/jdk-17.0.7/bin/javac.exe','-encoding','UTF-8','-cp',cp,'-d',classes,R/'lab/OracleRegistryDbProbe.java',R/'lab/OracleControlPublicationDbProbe.java'])
def snapshot(name):
    body='whenever sqlerror exit failure\nset pages 0 feedback off heading off lines 32767 trimspool on\nalter session set container=ORKPDB;\n'
    body+="select 'CONFIG|'||dbid||'|'||activation#||'|'||supplemental_log_data_min||'|'||supplemental_log_data_all from v$database;\nselect 'OLD|'||ID||'|'||TXT||'|'||NOTE from ORKLAB.T_BASELINE_20261004;\n"
    for prefix in ['PCNON','PCNUL','PCMIX','PCEMP']:
        text=(R/'lab/out/oracle-postcommit-proof-20261005'/(prefix+'-final.sql')).read_text();body+=text[text.index("select 'STATE|"):text.rindex('exit success')]
    body+='exit success\n';(O/(name+'.sql')).write_text(body);run(name,['docker','exec','-i','oraredo-19c-lab-20261004','sqlplus','-s','/','as','sysdba'],body.encode())
    return (O/(name+'.stdout.txt')).read_bytes()
before=snapshot('source-target-before'+suffix)
shutil.copyfile(R/'lab/out/oracle-registry-20261005/configs.json',O/'configs.json')
linuxcp=':'.join(['/evidence/'+classes.name]+['/product/'+d+'/target/classes' for d in modules]+['/evidence/adapter.jar','/evidence/kernel.jar','/evidence/'+libs.name+'/*'])
a=['docker','run','--rm','--label','owner=OracleRedoKernel','--label','scope=oracle-control-publication','--user','0','--network','container:oraredo-19c-lab-20261004']
for path,target in [(O,'/evidence'),(P/'server','/product'),(T,'/target'),(F,'/frozen'),(E,'/empty')]:a+=['--mount','type=bind,source='+str(path)+',target='+target+(',readonly' if target!='/evidence' else '')]
a+=['--entrypoint','java','flink:1.18-java17','-Xmx1536m','-cp',linuxcp,'OracleControlPublicationDbProbe','/evidence/control.secret.json','/evidence/configs.json','/target/target.secret','/evidence/matrix-proof.json','/evidence/matrix-progress.json']
if resume:a.append('--resume')
run('matrix'+suffix,a)
results=json.loads((O/'matrix-proof.json').read_text());assert len(results)==24
assert all(x['real_checkpoint_sql_failure_rolled_back_all'] and x['concurrent_publication_single'] and x['target_dml']==0 for x in results)
assert snapshot('source-target-after'+suffix)==before
run('helpers-exited'+suffix,['docker','ps','--filter','label=scope=oracle-control-publication','--format','{{.Names}}'])
assert not (O/('helpers-exited'+suffix+'.stdout.txt')).read_bytes().strip()
print('CONTROL_PUBLICATION_THREE_DIALECT_VERIFIED|24')
